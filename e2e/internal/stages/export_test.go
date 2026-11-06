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

const (
	wantedFile   = "payload.enc"
	leftoverFile = "leftover-from-an-earlier-run.enc"
)

type canned struct {
	status int
	body   string
}

// cannedServer serves one canned response per call, then repeats the last one,
// and counts the calls it received.
func cannedServer(t *testing.T, responses ...canned) (*httptest.Server, *int) {
	t.Helper()
	calls := 0
	srv := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		r := responses[min(calls, len(responses)-1)]
		calls++
		w.WriteHeader(r.status)
		if _, err := io.WriteString(w, r.body); err != nil {
			t.Errorf("writing the canned listing: %v", err)
		}
	}))
	t.Cleanup(srv.Close)
	return srv, &calls
}

// listingServer serves one canned outbox listing per call with status 200.
func listingServer(t *testing.T, bodies ...string) (*httptest.Server, *int) {
	t.Helper()
	responses := make([]canned, len(bodies))
	for i, body := range bodies {
		responses[i] = canned{http.StatusOK, body}
	}
	return cannedServer(t, responses...)
}

// stateFor builds a State whose proxy is srv and whose polling has no delay.
func stateFor(t *testing.T, srv *httptest.Server, maxRetries int) *state.State {
	t.Helper()
	u, err := url.Parse(srv.URL)
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

// The outbox is never emptied between runs, so unrelated leftovers must not end
// the poll: only the exported file does. Regression for a run that stopped on
// attempt 1 and then asserted zero matches (ELIXIR-NO/FEGA-Norway#833).
func TestCheckFilesWithRetryPollsPastUnrelatedFiles(t *testing.T) {
	srv, calls := listingServer(t,
		`{"files":[{"fileName":"`+leftoverFile+`"}]}`,
		`{"files":[{"fileName":"`+leftoverFile+`"}]}`,
		`{"files":[{"fileName":"`+leftoverFile+`"},{"fileName":"`+wantedFile+`"}]}`,
	)
	s := stateFor(t, srv, 5)

	status, listing, err := checkFilesWithRetry(context.Background(), s, httpx.New(s.Config), "token")
	if err != nil {
		t.Fatalf("checkFilesWithRetry: %v", err)
	}
	if status != http.StatusOK {
		t.Errorf("status: want 200, got %d", status)
	}
	if *calls != 3 {
		t.Errorf("listing calls: want 3, got %d", *calls)
	}
	if got := countMatching(listing, wantedFile); got != 1 {
		t.Errorf("matches for %s: want 1, got %d", wantedFile, got)
	}
}

// An exhausted poll returns the last listing, not nil, so the failure names
// what the outbox actually held.
func TestCheckFilesWithRetryReturnsLastListingWhenExhausted(t *testing.T) {
	srv, calls := listingServer(t, `{"files":[{"fileName":"`+leftoverFile+`"}]}`)
	s := stateFor(t, srv, 3)

	_, listing, err := checkFilesWithRetry(context.Background(), s, httpx.New(s.Config), "token")
	if err != nil {
		t.Fatalf("checkFilesWithRetry: %v", err)
	}
	if *calls != 3 {
		t.Errorf("listing calls: want 3, got %d", *calls)
	}
	if listing == nil {
		t.Fatal("listing: want the last one, got nil")
	}
	if len(listing.Files) != 1 || listing.Files[0].FileName != leftoverFile {
		t.Errorf("listing should carry what the outbox held, got %+v", listing.Files)
	}
}

// Until doa writes the first export, TSD has no outbox folder for the user and
// the proxy answers 403 (404 FOLDER_NOT_FOUND once the proxy passes TSD's
// status through), so the poll waits it out instead of failing on attempt 1.
func TestCheckFilesWithRetryWaitsForTheOutboxFolder(t *testing.T) {
	srv, calls := cannedServer(t,
		canned{http.StatusForbidden, ""},
		canned{http.StatusNotFound, `{"code":"FOLDER_NOT_FOUND"}`},
		canned{http.StatusOK, `{"files":[{"fileName":"` + wantedFile + `"}]}`},
	)
	s := stateFor(t, srv, 5)

	status, listing, err := checkFilesWithRetry(context.Background(), s, httpx.New(s.Config), "token")
	if err != nil {
		t.Fatalf("checkFilesWithRetry: %v", err)
	}
	if status != http.StatusOK {
		t.Errorf("status: want 200, got %d", status)
	}
	if *calls != 3 {
		t.Errorf("listing calls: want 3, got %d", *calls)
	}
	if got := countMatching(listing, wantedFile); got != 1 {
		t.Errorf("matches for %s: want 1, got %d", wantedFile, got)
	}
}

// A poll that only ever saw a missing folder reports that status, so the
// failure says 403 or 404 rather than claiming an empty outbox.
func TestCheckFilesWithRetryReportsMissingFolderWhenExhausted(t *testing.T) {
	srv, calls := cannedServer(t, canned{http.StatusForbidden, ""})
	s := stateFor(t, srv, 3)

	status, listing, err := checkFilesWithRetry(context.Background(), s, httpx.New(s.Config), "token")
	if err != nil {
		t.Fatalf("checkFilesWithRetry: %v", err)
	}
	if status != http.StatusForbidden {
		t.Errorf("status: want 403, got %d", status)
	}
	if *calls != 3 {
		t.Errorf("listing calls: want 3, got %d", *calls)
	}
	if listing == nil {
		t.Fatal("listing: want an empty one, got nil")
	}
}

// Any other refusal still ends the poll at once, carrying its status.
func TestCheckFilesWithRetryStopsOnOtherStatuses(t *testing.T) {
	srv, calls := cannedServer(t,
		canned{http.StatusInternalServerError, ""},
		canned{http.StatusOK, `{"files":[{"fileName":"` + wantedFile + `"}]}`},
	)
	s := stateFor(t, srv, 5)

	status, _, err := checkFilesWithRetry(context.Background(), s, httpx.New(s.Config), "token")
	if err != nil {
		t.Fatalf("checkFilesWithRetry: %v", err)
	}
	if status != http.StatusInternalServerError {
		t.Errorf("status: want 500, got %d", status)
	}
	if *calls != 1 {
		t.Errorf("listing calls: want 1, got %d", *calls)
	}
}
