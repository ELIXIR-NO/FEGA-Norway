package cli

import (
	"bytes"
	"crypto/md5"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"testing"

	"github.com/golang-jwt/jwt/v5"
	"github.com/neicnordic/crypt4gh/keys"
	"github.com/neicnordic/crypt4gh/streaming"
)

const (
	testProject = "p11"
	testUser    = "p11-ega-user"
	testID      = "upload-1"
)

// fakeProxy plays the proxy, and in direct mode the TSD file API, on one
// httptest server. Uploads merge chunks in order, as TSD does.
type fakeProxy struct {
	t *testing.T

	mu         sync.Mutex
	inbox      []fileJSON
	outbox     map[string][]byte
	outboxList []fileJSON
	resumables []resumableJSON
	held       map[string][]byte // bytes received per file name
	maxChunk   map[string]int64

	// inboxStatus and inboxBody, when set, answer the inbox listing.
	inboxStatus int
	inboxBody   string
	// streamStatus answers GET /stream/{name} for the names it holds.
	streamStatus map[string]int

	requests []recorded
	finished map[string]url.Values // the chunk=end or notify query per file
}

type recorded struct {
	Method, Path string
	Query        url.Values
	BasicAuth    bool
}

type fileJSON struct {
	FileName     string `json:"fileName"`
	Size         int64  `json:"size"`
	ModifiedDate string `json:"modifiedDate"`
}

type resumableJSON struct {
	ID             string `json:"id"`
	FileName       string `json:"fileName"`
	PreviousOffset int64  `json:"previousOffset"`
	NextOffset     int64  `json:"nextOffset"`
	MaxChunk       int64  `json:"maxChunk"`
	MD5Sum         string `json:"md5Sum"`
}

func newFakeProxy(t *testing.T) (*fakeProxy, *httptest.Server) {
	f := &fakeProxy{
		t:            t,
		outbox:       map[string][]byte{},
		held:         map[string][]byte{},
		maxChunk:     map[string]int64{},
		finished:     map[string]url.Values{},
		streamStatus: map[string]int{},
	}
	srv := httptest.NewServer(f)
	t.Cleanup(srv.Close)
	return f, srv
}

func (f *fakeProxy) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()
	_, _, basic := r.BasicAuth()
	f.requests = append(f.requests, recorded{Method: r.Method, Path: r.URL.Path, Query: r.URL.Query(), BasicAuth: basic})
	body, _ := io.ReadAll(r.Body)
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	switch {
	case r.URL.Path == "/files" && r.Method == http.MethodGet:
		f.listFiles(w, r.URL.Query())
	case r.URL.Path == "/files" && r.Method == http.MethodDelete:
	case r.URL.Path == "/resumables" && r.Method == http.MethodGet:
		writeJSON(w, map[string]any{"resumables": f.resumables})
	case r.URL.Path == "/resumables" && r.Method == http.MethodDelete:
	case len(parts) == 3 && parts[0] == "stream" && parts[2] == "notify":
		f.finished[parts[1]] = r.URL.Query()
	case len(parts) == 2 && parts[0] == "stream" && r.Method == http.MethodPatch:
		f.patch(w, parts[1], r.URL.Query(), body, true)
	case len(parts) == 2 && parts[0] == "stream" && r.Method == http.MethodGet:
		f.stream(w, parts[1])
	case r.URL.Path == "/v1/"+testProject+"/auth/lifesc/token":
		writeJSON(w, map[string]string{"token": f.tsdToken()})
	case len(parts) == 6 && parts[2] == "ega" && parts[3] == testUser && parts[4] == "files":
		f.patch(w, parts[5], r.URL.Query(), body, false)
	default:
		http.Error(w, "no such route: "+r.Method+" "+r.URL.Path, http.StatusNotFound)
	}
}

func (f *fakeProxy) listFiles(w http.ResponseWriter, q url.Values) {
	if q.Get("inbox") == "true" && f.inboxStatus != 0 {
		w.WriteHeader(f.inboxStatus)
		io.WriteString(w, f.inboxBody)
		return
	}
	files := f.inbox
	if q.Get("inbox") == "false" {
		files = f.outboxList
	}
	page, _ := strconv.Atoi(q.Get("page"))
	perPage, _ := strconv.Atoi(q.Get("per_page"))
	if page < 0 || perPage <= 0 {
		http.Error(w, "bad paging", http.StatusBadRequest)
		return
	}
	from, to := min(page*perPage, len(files)), min((page+1)*perPage, len(files))
	writeJSON(w, map[string]any{"files": files[from:to]})
}

// patch takes a chunk or chunk=end. The proxy checks the chunk's md5 and
// TSD carries the upload ID as id rather than uploadId.
func (f *fakeProxy) patch(w http.ResponseWriter, name string, q url.Values, body []byte, viaProxy bool) {
	idParam := "id"
	if viaProxy {
		idParam = "uploadId"
	}
	if q.Get("chunk") == "end" {
		f.finished[name] = q
		delete(f.maxChunk, name)
		writeJSON(w, map[string]string{"id": q.Get(idParam)})
		return
	}
	num, _ := strconv.ParseInt(q.Get("chunk"), 10, 64)
	if num != f.maxChunk[name]+1 || (num > 1 && q.Get(idParam) != testID) {
		http.Error(w, "chunk_order_incorrect", http.StatusBadRequest)
		return
	}
	if sum := md5.Sum(body); viaProxy && q.Get("md5") != hex.EncodeToString(sum[:]) {
		http.Error(w, "md5 mismatch", http.StatusInternalServerError)
		return
	}
	f.held[name] = append(f.held[name], body...)
	f.maxChunk[name] = num
	writeJSON(w, map[string]string{"id": testID})
}

func (f *fakeProxy) stream(w http.ResponseWriter, name string) {
	if status := f.streamStatus[name]; status != 0 {
		http.Error(w, "stream failed", status)
		return
	}
	w.Write(f.outbox[name])
}

func (f *fakeProxy) tsdToken() string {
	token, err := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{"user": testUser}).SignedString([]byte("test-only-key"))
	if err != nil {
		f.t.Fatal(err)
	}
	return token
}

func (f *fakeProxy) addOutbox(name string, data []byte) {
	f.outbox[name] = data
	f.outboxList = append(f.outboxList, fileJSON{FileName: name, Size: int64(len(data)), ModifiedDate: "2026-10-28T14:02:11"})
}

// count returns how many requests had the method and a path starting with
// prefix.
func (f *fakeProxy) count(method, prefix string) int {
	f.mu.Lock()
	defer f.mu.Unlock()
	n := 0
	for _, r := range f.requests {
		if r.Method == method && strings.HasPrefix(r.Path, prefix) {
			n++
		}
	}
	return n
}

func (f *fakeProxy) chunkRequests() int {
	return f.count(http.MethodPatch, "/stream/") + f.count(http.MethodPatch, "/v1/")
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}

// testEnv is the environment of a run against srv.
func testEnv(srv *httptest.Server, extra ...string) func(string) string {
	vars := map[string]string{
		"LOCAL_EGA_INSTANCE_URL":    srv.URL,
		"ELIXIR_AAI_TOKEN":          "aai-token",
		"CENTRAL_EGA_USERNAME":      "cega-user",
		"CENTRAL_EGA_PASSWORD":      "cega-pass",
		"TSD_PROXY_URL":             srv.URL,
		"TSD_PROJ_NAME":             testProject,
		"LEGA_COMMANDER_CHUNK_SIZE": "1",
	}
	for i := 0; i+1 < len(extra); i += 2 {
		vars[extra[i]] = extra[i+1]
	}
	return func(name string) string { return vars[name] }
}

// encrypted returns a Crypt4GH file holding payloadSize random-looking bytes.
func encrypted(t *testing.T, payloadSize int) []byte {
	t.Helper()
	public, private, err := keys.GenerateKeyPair()
	if err != nil {
		t.Fatal(err)
	}
	var buf bytes.Buffer
	w, err := streaming.NewCrypt4GHWriter(&buf, private, [][32]byte{public}, nil)
	if err != nil {
		t.Fatal(err)
	}
	payload := make([]byte, payloadSize)
	for i := range payload {
		payload[i] = byte(i * 7)
	}
	if _, err := w.Write(payload); err != nil {
		t.Fatal(err)
	}
	if err := w.Close(); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

func writeTestFile(t *testing.T, dir, name string, data []byte) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, data, 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

func sha256Hex(b []byte) string {
	sum := sha256.Sum256(b)
	return hex.EncodeToString(sum[:])
}

func md5Hex(b []byte) string {
	sum := md5.Sum(b)
	return hex.EncodeToString(sum[:])
}

func mustContain(t *testing.T, stream, got string, want ...string) {
	t.Helper()
	for _, s := range want {
		if !strings.Contains(got, s) {
			t.Errorf("%s does not contain %q:\n%s", stream, s, got)
		}
	}
}
