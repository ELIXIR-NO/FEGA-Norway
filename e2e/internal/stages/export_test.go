package stages

import (
	"context"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/httpx"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/config"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/report"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
)

// The outbox is never emptied between runs, and it does not exist at all
// before doa writes the first export. The poll has to look past both until the
// exported file shows up. Each case scripts what the proxy answers, call by
// call, and checks where the poll stopped and what it handed back.

const (
	wantedFile   = "payload.enc"
	leftoverFile = "leftover-from-an-earlier-run.enc"
)

// reply is one scripted answer from the proxy's outbox listing.
type reply struct {
	status int
	body   string
}

var (
	folderMissing  = reply{http.StatusForbidden, ""}
	folderNotFound = reply{http.StatusNotFound, `{"code":"FOLDER_NOT_FOUND"}`}
	leftoversOnly  = reply{http.StatusOK, `{"files":[{"fileName":"` + leftoverFile + `"}]}`}
	fileExported   = reply{http.StatusOK, `{"files":[{"fileName":"` + leftoverFile + `"},{"fileName":"` + wantedFile + `"}]}`}
	serverError    = reply{http.StatusInternalServerError, ""}
)

func TestCheckFilesWithRetry(t *testing.T) {
	tests := []struct {
		name       string
		replies    []reply // one per call; the last one repeats for every call after it
		maxRetries int
		wantCalls  int
		wantStatus int
		wantListed int  // files in the listing the poll returns
		wantFound  bool // the exported file is among them
	}{
		{
			name:       "keeps polling past leftovers from earlier runs (FEGA-Norway#833)",
			replies:    []reply{leftoversOnly, leftoversOnly, fileExported},
			maxRetries: 5,
			wantCalls:  3, wantStatus: http.StatusOK, wantListed: 2, wantFound: true,
		},
		{
			name:       "waits for doa to create the outbox folder (FEGA-Norway#940)",
			replies:    []reply{folderMissing, folderNotFound, fileExported},
			maxRetries: 5,
			wantCalls:  3, wantStatus: http.StatusOK, wantListed: 2, wantFound: true,
		},
		{
			name:       "returns the last listing when the tries run out, so the failure names what the outbox held",
			replies:    []reply{leftoversOnly},
			maxRetries: 3,
			wantCalls:  3, wantStatus: http.StatusOK, wantListed: 1, wantFound: false,
		},
		{
			name:       "reports the missing folder when the tries run out, instead of claiming an empty outbox",
			replies:    []reply{folderMissing},
			maxRetries: 3,
			wantCalls:  3, wantStatus: http.StatusForbidden, wantListed: 0, wantFound: false,
		},
		{
			name:       "stops at once on any other status",
			replies:    []reply{serverError, fileExported},
			maxRetries: 5,
			wantCalls:  1, wantStatus: http.StatusInternalServerError, wantListed: 0, wantFound: false,
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			proxy := newScriptedProxy(t, tc.replies...)
			s := stateFor(t, proxy, tc.maxRetries)

			status, listing, err := checkFilesWithRetry(context.Background(), s, httpx.New(s.Config), "token")
			if err != nil {
				t.Fatalf("checkFilesWithRetry: %v", err)
			}
			if proxy.calls != tc.wantCalls {
				t.Errorf("listing calls: want %d, got %d", tc.wantCalls, proxy.calls)
			}
			if status != tc.wantStatus {
				t.Errorf("status: want %d, got %d", tc.wantStatus, status)
			}
			if listing == nil {
				t.Fatal("listing: want the last one seen, got nil")
			}
			if len(listing.Files) != tc.wantListed {
				t.Errorf("files listed: want %d, got %+v", tc.wantListed, listing.Files)
			}
			if found := countMatching(listing, wantedFile) == 1; found != tc.wantFound {
				t.Errorf("%s in the listing: want %t, got %t", wantedFile, tc.wantFound, found)
			}
		})
	}
}

// scriptedProxy stands in for the proxy's outbox listing. It answers each call
// with the next scripted reply, repeats the last one once the script is used
// up, and counts the calls so a test can see how far the poll went.
type scriptedProxy struct {
	srv   *httptest.Server
	calls int
}

func newScriptedProxy(t *testing.T, replies ...reply) *scriptedProxy {
	t.Helper()
	p := &scriptedProxy{}
	p.srv = httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		r := replies[min(p.calls, len(replies)-1)]
		p.calls++
		w.WriteHeader(r.status)
		if _, err := io.WriteString(w, r.body); err != nil {
			t.Errorf("writing the scripted listing: %v", err)
		}
	}))
	t.Cleanup(p.srv.Close)
	return p
}

// stateFor builds the run state the poll reads: the scripted proxy as the
// proxy, the given number of tries, and no wait between them.
func stateFor(t *testing.T, proxy *scriptedProxy, maxRetries int) *state.State {
	t.Helper()
	u, err := url.Parse(proxy.srv.URL)
	if err != nil {
		t.Fatalf("parsing test server URL: %v", err)
	}
	host, port, err := net.SplitHostPort(u.Host)
	if err != nil {
		t.Fatalf("splitting test server host: %v", err)
	}
	return &state.State{
		Config: &config.Config{
			Integration:                    config.IntegrationFEGA,
			ProxyHost:                      host,
			ProxyPort:                      port,
			ExportRequestMaxRetries:        maxRetries,
			ExportRequestIntervalInSeconds: 0,
		},
		Log:     report.New(io.Discard),
		EncFile: wantedFile,
	}
}
