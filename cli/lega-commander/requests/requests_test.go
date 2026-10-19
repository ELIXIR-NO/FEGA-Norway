package requests

import (
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"os"
	"strconv"
	"strings"
	"testing"

	"github.com/logrusorgru/aurora/v4"
)

var server *httptest.Server
var client Client
var url string

func TestMain(m *testing.M) {
	setup()
	code := m.Run()
	teardown()
	os.Exit(code)
}

func setup() {
	server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		response := make(map[string]string)
		auth, password, _ := r.BasicAuth()
		response["auth"] = auth
		response["pass"] = password
		response["header"] = r.Header.Get("header")
		response["params"] = r.URL.RawQuery
		bytes, err := json.Marshal(response)
		if err != nil {
			log.Fatal(aurora.Red(err))
		}
		_, err = w.Write(bytes)
		if err != nil {
			log.Fatal(aurora.Red(err))
		}
	}))
	client = NewClient(server.Client())
	url = server.URL
}

func TestDoRequest(t *testing.T) {
	username := "username"
	password := "password"
	get, err := client.DoRequest(http.MethodGet,
		url,
		strings.NewReader("Body"),
		map[string]string{"header": "test header value"},
		map[string]string{"param": "test param value"},
		username,
		password)
	if err != nil {
		t.Error(err)
	}
	body := get.Body
	defer body.Close()
	bytes, err := io.ReadAll(body)
	if err != nil {
		t.Error(err)
	}
	response := string(bytes)
	if response != `{"auth":"username","header":"test header value","params":"param=test+param+value","pass":"password"}` {
		t.Error()
	}
}

func teardown() {
	server.Close()
}

func TestCheckResponse(t *testing.T) {
	cegaUnknownUser := `400 Bad Request: "No info for that user"`
	tests := []struct {
		name       string
		statusCode int
		body       string
		wantErr    error
		wantMsg    string
	}{
		{name: "ok", statusCode: http.StatusOK},
		{name: "wrong EGA password", statusCode: http.StatusForbidden, body: "EGA authentication failed", wantErr: ErrEGAAuthentication},
		{name: "rejected without a reason", statusCode: http.StatusForbidden, wantErr: ErrRejectedWithoutReason},
		{name: "unknown EGA username", statusCode: http.StatusForbidden, body: cegaUnknownUser, wantMsg: "request rejected by the proxy (403 Forbidden): " + cegaUnknownUser},
		{name: "credentials not sent", statusCode: http.StatusUnauthorized, wantMsg: "request rejected by the proxy (401 Unauthorized)"},
		{name: "server error", statusCode: http.StatusInternalServerError, body: "boom\n", wantMsg: "500 Internal Server Error: boom"},
		{name: "long error page", statusCode: http.StatusBadGateway, body: strings.Repeat("x", 5000), wantMsg: "502 Bad Gateway: " + strings.Repeat("x", maxBodyBytes)},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			response := &http.Response{
				StatusCode: tt.statusCode,
				Status:     strconv.Itoa(tt.statusCode) + " " + http.StatusText(tt.statusCode),
				Body:       io.NopCloser(strings.NewReader(tt.body)),
			}
			err := CheckResponse(response)
			switch {
			case tt.wantErr != nil:
				if !errors.Is(err, tt.wantErr) {
					t.Errorf("CheckResponse() = %v, want %v", err, tt.wantErr)
				}
			case tt.wantMsg != "":
				if err == nil || err.Error() != tt.wantMsg {
					t.Errorf("CheckResponse() = %v, want %q", err, tt.wantMsg)
				}
			case err != nil:
				t.Errorf("CheckResponse() = %v, want nil", err)
			}
		})
	}
}
