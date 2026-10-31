package cli

import (
	"bytes"
	"context"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
)

type runResult struct {
	code           int
	stdout, stderr string
}

func run(getenv func(string) string, args ...string) runResult {
	var stdout, stderr bytes.Buffer
	code := Run(context.Background(), args, getenv, &stdout, &stderr)
	return runResult{code: code, stdout: stdout.String(), stderr: stderr.String()}
}

func (r runResult) check(t *testing.T, wantCode int) {
	t.Helper()
	if r.code != wantCode {
		t.Fatalf("exit code = %d, want %d\nstdout: %s\nstderr: %s", r.code, wantCode, r.stdout, r.stderr)
	}
}

func TestRunInboxLs(t *testing.T) {
	two := []fileJSON{
		{FileName: "sample-01.bam.c4gh", Size: 1288490188, ModifiedDate: "2026-10-28T14:02:11Z"},
		{FileName: "run-7.fastq.gz.c4gh", Size: 13636521574, ModifiedDate: "2026-10-29T09:15:03Z"},
	}
	tests := []struct {
		name       string
		inbox      []fileJSON
		args       []string
		wantCode   int
		wantStdout []string
		exact      string
	}{
		{
			name:       "table",
			inbox:      two,
			args:       []string{"inbox", "ls"},
			wantStdout: []string{"NAME", "SIZE", "MODIFIED", "sample-01.bam.c4gh", "1.2 GiB", "2026-10-28 14:02", "2 files, 13.9 GiB (page 1, 100 per page; --all for everything)"},
		},
		{
			name:  "json",
			inbox: two,
			args:  []string{"--json", "inbox", "ls"},
			exact: `[{"name":"sample-01.bam.c4gh","size":1288490188,"modified":"2026-10-28T14:02:11Z"},` +
				`{"name":"run-7.fastq.gz.c4gh","size":13636521574,"modified":"2026-10-29T09:15:03Z"}]` + "\n",
		},
		{name: "empty", args: []string{"inbox", "ls"}, exact: "Your inbox is empty.\n"},
		{name: "empty json", args: []string{"--json", "inbox", "ls"}, exact: "[]\n"},
		{
			name:       "all ignores page",
			inbox:      two,
			args:       []string{"inbox", "ls", "--all", "--page", "0"},
			wantStdout: []string{"2 files, 13.9 GiB\n"},
		},
		{name: "page 0", args: []string{"inbox", "ls", "--page", "0"}, wantCode: ExitUsage},
		{name: "per page too big", args: []string{"inbox", "ls", "-p", "50001"}, wantCode: ExitUsage},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			fake, srv := newFakeProxy(t)
			fake.inbox = tt.inbox
			res := run(testEnv(srv), tt.args...)
			res.check(t, tt.wantCode)
			if tt.exact != "" && res.stdout != tt.exact {
				t.Errorf("stdout =\n%q\nwant\n%q", res.stdout, tt.exact)
			}
			mustContain(t, "stdout", res.stdout, tt.wantStdout...)
			if tt.wantCode == ExitOK && !fake.requests[0].BasicAuth {
				t.Error("inbox listing sent no CEGA credentials")
			}
		})
	}
}

func TestRunOutboxLsNeedsNoCEGA(t *testing.T) {
	fake, srv := newFakeProxy(t)
	fake.addOutbox("a.c4gh", []byte("abc"))
	res := run(testEnv(srv, "CENTRAL_EGA_USERNAME", "", "CENTRAL_EGA_PASSWORD", ""), "outbox", "ls")
	res.check(t, ExitOK)
	mustContain(t, "stdout", res.stdout, "a.c4gh", "1 file, 3 B")
	if fake.requests[0].BasicAuth {
		t.Error("outbox listing sent CEGA credentials")
	}
}

func TestRunDownloadAll(t *testing.T) {
	fake, srv := newFakeProxy(t)
	want := map[string]string{"a.c4gh": "aaaa", "b.c4gh": "bbbbbbbb", "c.c4gh": "c"}
	for _, name := range []string{"a.c4gh", "b.c4gh", "c.c4gh"} {
		fake.addOutbox(name, []byte(want[name]))
	}
	dir := t.TempDir()
	res := run(testEnv(srv), "download", "-o", dir)
	res.check(t, ExitOK)
	mustContain(t, "stdout", res.stdout, "3 downloaded, 0 skipped, 0 failed")
	for name, content := range want {
		got, err := os.ReadFile(filepath.Join(dir, name))
		if err != nil || string(got) != content {
			t.Errorf("%s = %q, %v", name, got, err)
		}
	}
	listings := 0
	for _, r := range fake.requests {
		if r.Path != "/files" {
			continue
		}
		listings++
		page, perPage := atoi(t, r.Query.Get("page")), atoi(t, r.Query.Get("per_page"))
		if page < 0 || perPage <= 0 {
			t.Errorf("listing asked for page=%d per_page=%d", page, perPage)
		}
	}
	if listings == 0 {
		t.Error("the outbox was never listed")
	}
	for _, r := range fake.requests {
		if r.BasicAuth {
			t.Errorf("%s %s sent CEGA credentials", r.Method, r.Path)
		}
	}
}

func TestRunDownloadSkipsExisting(t *testing.T) {
	fake, srv := newFakeProxy(t)
	fake.addOutbox("a.c4gh", []byte("aaaa"))
	dir := t.TempDir()
	writeTestFile(t, dir, "a.c4gh", []byte("aaaa"))
	res := run(testEnv(srv), "download", "-o", dir, "a.c4gh")
	res.check(t, ExitOK)
	mustContain(t, "stdout", res.stdout, "skipped", "already downloaded", "0 downloaded, 1 skipped, 0 failed")
	if n := fake.count(http.MethodGet, "/stream/"); n != 0 {
		t.Errorf("fetched %d files, want none", n)
	}
}

func atoi(t *testing.T, s string) int {
	t.Helper()
	n, err := strconv.Atoi(s)
	if err != nil {
		t.Fatalf("not a number: %q", s)
	}
	return n
}

func TestRunUploadRejectsBeforeSending(t *testing.T) {
	fake, srv := newFakeProxy(t)
	dir := t.TempDir()
	c4gh := encrypted(t, 100)
	writeTestFile(t, dir, "a/x.c4gh", c4gh)
	writeTestFile(t, dir, "b/x.c4gh", c4gh)
	writeTestFile(t, dir, "good.c4gh", c4gh)
	writeTestFile(t, dir, "notes.txt", []byte("plain text"))
	res := run(testEnv(srv), "upload", dir)
	res.check(t, ExitError)
	mustContain(t, "stderr", res.stderr,
		"lega: nothing was uploaded; 3 files need attention:",
		"notes.txt", "not a Crypt4GH file",
		filepath.Join(dir, "a/x.c4gh"), "same name as "+filepath.Join(dir, "b/x.c4gh"))
	if strings.Contains(res.stderr, "good.c4gh") {
		t.Errorf("stderr lists a file that passed:\n%s", res.stderr)
	}
	if n := fake.chunkRequests(); n != 0 {
		t.Errorf("%d chunk requests reached the server, want none", n)
	}
}

func TestRunUploadListingError(t *testing.T) {
	tests := []struct {
		name        string
		status      int
		body        string
		wantCode    int
		wantWarning bool
	}{
		{name: "503 stops", status: http.StatusServiceUnavailable, body: "down", wantCode: ExitError},
		{name: "502 stops", status: http.StatusBadGateway, wantCode: ExitError},
		{name: "bodyless 403 without code proceeds with warning", status: http.StatusForbidden, wantCode: ExitOK, wantWarning: true},
		{
			name:     "auth code stops",
			status:   http.StatusForbidden,
			body:     `{"code":"EGA_AUTH_FAILED","detail":"wrong EGA password"}`,
			wantCode: ExitError,
		},
		{name: "200 [] proceeds", status: http.StatusOK, body: "[]", wantCode: ExitOK},
	}
	for _, mode := range []string{"proxy", "direct"} {
		for _, tt := range tests {
			t.Run(mode+"/"+tt.name, func(t *testing.T) {
				fake, srv := newFakeProxy(t)
				fake.inboxStatus, fake.inboxBody = tt.status, tt.body
				path := writeTestFile(t, t.TempDir(), "x.c4gh", encrypted(t, 100))
				args := []string{"upload", path}
				if mode == "direct" {
					args = append(args, "--direct")
				}
				res := run(testEnv(srv), args...)
				res.check(t, tt.wantCode)
				if got := strings.Contains(res.stderr, "duplicate check could not run"); got != tt.wantWarning {
					t.Errorf("warning = %v, want %v\nstderr: %s", got, tt.wantWarning, res.stderr)
				}
				sent := fake.chunkRequests() > 0
				if sent != (tt.wantCode == ExitOK) {
					t.Errorf("chunks sent = %v with exit code %d", sent, res.code)
				}
			})
		}
	}
}

func TestRunUploadHappyPath(t *testing.T) {
	data := encrypted(t, 5<<19) // 2.5 MiB of payload, so three 1 MiB chunks
	tests := []struct {
		name        string
		direct      bool
		resume      bool
		wantOutcome string
		wantChunks  int
	}{
		{name: "proxy", wantOutcome: "1 uploaded, 0 skipped, 0 failed", wantChunks: 3},
		{name: "direct", direct: true, wantOutcome: "1 uploaded, 0 skipped, 0 failed", wantChunks: 3},
		{name: "proxy resumed", resume: true, wantOutcome: "resumed", wantChunks: 2},
		{name: "direct resumed", direct: true, resume: true, wantOutcome: "resumed", wantChunks: 2},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			fake, srv := newFakeProxy(t)
			path := writeTestFile(t, t.TempDir(), "x.c4gh", data)
			if tt.resume {
				fake.held["x.c4gh"] = bytes.Clone(data[:1<<20])
				fake.maxChunk["x.c4gh"] = 1
				fake.resumables = []resumableJSON{{ID: testID, FileName: "x.c4gh", NextOffset: 1 << 20, MaxChunk: 1, MD5Sum: md5Hex(data[:1<<20])}}
			}
			args := []string{"upload", path}
			if tt.direct {
				args = append(args, "--direct")
			}
			res := run(testEnv(srv), args...)
			res.check(t, ExitOK)
			mustContain(t, "stdout", res.stdout, tt.wantOutcome)
			if n := fake.chunkRequests() - 1; n != tt.wantChunks {
				t.Errorf("chunks sent = %d, want %d", n, tt.wantChunks)
			}
			if !bytes.Equal(fake.held["x.c4gh"], data) {
				t.Errorf("server holds %d bytes, want the file's %d", len(fake.held["x.c4gh"]), len(data))
			}
			end := fake.finished["x.c4gh"]
			if end.Get("sha256") != sha256Hex(data) || end.Get("fileSize") != strconv.Itoa(len(data)) {
				t.Errorf("finished with sha256=%s fileSize=%s, want %s and %d", end.Get("sha256"), end.Get("fileSize"), sha256Hex(data), len(data))
			}
			if tt.direct && fake.count(http.MethodPost, "/stream/x.c4gh/notify") != 1 {
				t.Error("direct upload did not notify the proxy")
			}
		})
	}
}

func TestRunUploadSkipsInboxDuplicate(t *testing.T) {
	fake, srv := newFakeProxy(t)
	fake.inbox = []fileJSON{{FileName: "x.c4gh", Size: 1}}
	path := writeTestFile(t, t.TempDir(), "x.c4gh", encrypted(t, 100))
	res := run(testEnv(srv), "upload", path)
	res.check(t, ExitOK)
	mustContain(t, "stdout", res.stdout, "skipped", "already in the inbox (--force replaces it)", "0 uploaded, 1 skipped, 0 failed")
	if fake.chunkRequests() != 0 {
		t.Error("a skipped file was sent")
	}
	res = run(testEnv(srv), "upload", "--force", path)
	res.check(t, ExitOK)
	mustContain(t, "stdout", res.stdout, "1 uploaded")
}

func TestRunWarnsOnce(t *testing.T) {
	_, srv := newFakeProxy(t)
	res := run(testEnv(srv, "LEGA_COMMANDER_TLS_SKIP_VERIFY", "true"), "inbox", "ls")
	res.check(t, ExitOK)
	if n := strings.Count(res.stderr, "LOCAL_EGA_INSTANCE_URL is plain http"); n != 1 {
		t.Errorf("plain http warned %d times:\n%s", n, res.stderr)
	}
	if n := strings.Count(res.stderr, "TLS certificates are not checked"); n != 1 {
		t.Errorf("TLS skip warned %d times:\n%s", n, res.stderr)
	}
	for _, secret := range []string{"aai-token", "cega-pass"} {
		if strings.Contains(res.stderr+res.stdout, secret) {
			t.Errorf("output quotes %q", secret)
		}
	}
}

func TestExitCodes(t *testing.T) {
	tests := []struct {
		name   string
		setup  func(f *fakeProxy)
		env    []string
		args   func(dir string) []string
		want   int
		stderr string
	}{
		{name: "ok", args: static("version"), want: ExitOK},
		{name: "unknown flag", args: static("inbox", "ls", "--bogus"), want: ExitUsage},
		{name: "group without subcommand", args: static("inbox"), want: ExitUsage},
		{name: "missing token", env: []string{"ELIXIR_AAI_TOKEN", ""}, args: static("inbox", "ls"), want: ExitUsage},
		{name: "bad chunk size", env: []string{"LEGA_COMMANDER_CHUNK_SIZE", "0"}, args: static("outbox", "ls"), want: ExitUsage},
		{name: "retries out of range", args: static("upload", "--retries", "6", "x"), want: ExitUsage},
		{name: "rm without names", args: static("inbox", "rm"), want: ExitUsage},
		{name: "output not a directory", args: static("download", "-o", "/dev/null"), want: ExitUsage},
		{
			name:  "request failed",
			setup: func(f *fakeProxy) { f.inboxStatus = http.StatusInternalServerError },
			args:  static("inbox", "ls"),
			want:  ExitError,
		},
		{name: "upload path missing", args: static("upload", "/nonexistent/x.c4gh"), want: ExitError},
		{name: "upload empty folder", args: func(dir string) []string { return []string{"upload", dir} }, want: ExitError, stderr: "nothing to upload"},
		{
			name: "partial download",
			setup: func(f *fakeProxy) {
				f.addOutbox("a.c4gh", []byte("a"))
				f.addOutbox("b.c4gh", []byte("b"))
				f.streamStatus["b.c4gh"] = http.StatusInternalServerError
			},
			args: func(dir string) []string { return []string{"download", "-o", dir} },
			want: ExitPartial,
		},
		{
			name: "every download failed",
			setup: func(f *fakeProxy) {
				f.addOutbox("a.c4gh", []byte("a"))
				f.streamStatus["a.c4gh"] = http.StatusInternalServerError
			},
			args: func(dir string) []string { return []string{"download", "-o", dir} },
			want: ExitError,
		},
		{
			name:  "name not in the outbox",
			setup: func(f *fakeProxy) { f.addOutbox("a.c4gh", []byte("a")) },
			args:  func(dir string) []string { return []string{"download", "-o", dir, "a.c4gh", "missing.c4gh"} },
			want:  ExitPartial,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			fake, srv := newFakeProxy(t)
			if tt.setup != nil {
				tt.setup(fake)
			}
			res := run(testEnv(srv, tt.env...), tt.args(t.TempDir())...)
			res.check(t, tt.want)
			if tt.want != ExitOK && !strings.HasPrefix(res.stderr, "lega: ") {
				t.Errorf("stderr = %q, want a lega: message", res.stderr)
			}
			mustContain(t, "stderr", res.stderr, tt.stderr)
		})
	}
}

func static(args ...string) func(string) []string {
	return func(string) []string { return args }
}
