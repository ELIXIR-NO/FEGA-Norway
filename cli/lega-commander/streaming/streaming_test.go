package streaming

import (
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"strings"
	"testing"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega-commander/files"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega-commander/requests"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega-commander/resuming"
	"github.com/chzyer/test"
	"github.com/logrusorgru/aurora/v4"
)

var uploader Streamer
var dir string
var file *os.File
var existingFile *os.File

func TestMain(m *testing.M) {
	setup()
	code := m.Run()
	teardown()
	os.Exit(code)
}

func setup() {
	var err error
	_ = os.Setenv("CENTRAL_EGA_USERNAME", "user")
	_ = os.Setenv("CENTRAL_EGA_PASSWORD", "pass")
	_ = os.Setenv("LOCAL_EGA_INSTANCE_URL", "http://localhost/")
	_ = os.Setenv("ELIXIR_AAI_TOKEN", "token")
	var client requests.Client = mockClient{}
	filesManager, err := files.NewFileManager(&client)
	if err != nil {
		log.Fatal(aurora.Red(err))
	}
	resumablesManager, err := resuming.NewResumablesManager(&client)
	if err != nil {
		log.Fatal(aurora.Red(err))
	}
	uploader, err = NewStreamer(&client, filesManager, resumablesManager, false)
	if err != nil {
		log.Fatal(aurora.Red(err))
	}
	dir = "../test/files"
	file, err = os.Open("../test/files/sample.txt.enc")
	if err != nil {
		log.Fatal(aurora.Red(err))
	}
	existingFile, err = os.Open("../test/test.enc")
	if err != nil {
		log.Fatal(aurora.Red(err))
	}
}

type mockClient struct {
}

func (mockClient) DoRequest(
    method, url string,
    _ io.Reader,
    headers, params map[string]string,
    _, _ string,
) (*http.Response, error) {
    //auth check
    if !strings.HasPrefix(headers["Proxy-Authorization"], "Bearer ") {
        return &http.Response{
            StatusCode: http.StatusUnauthorized,
            Body:       ioutil.NopCloser(strings.NewReader("")),
        }, nil
    }

    // list files (inbox / outbox)
    if strings.HasSuffix(url, "/files") {
        page := params["page"]
        if page != "" && page != "1" {
            empty := ioutil.NopCloser(strings.NewReader(`{"files":[]}`))
            return &http.Response{StatusCode: http.StatusOK, Body: empty}, nil
        }
        var body io.ReadCloser
        if params["inbox"] == "" || params["inbox"] == "true" {
            body = ioutil.NopCloser(
                strings.NewReader(`{"files":[{"fileName":"test.enc","size":100,"modifiedDate":"2010"}]}`),
            )
        } else {
            body = ioutil.NopCloser(
                strings.NewReader(`{"files":[{"fileName":"test2.enc","size":100,"modifiedDate":"2010"}]}`),
            )
        }
        return &http.Response{StatusCode: http.StatusOK, Body: body}, nil
    }

    // resumable / streaming endpoints (mock PATCH/GET)
    if strings.Contains(url, "/stream/") {
        if method == http.MethodGet {
            // Simulate a file download
            return &http.Response{
                StatusCode: http.StatusOK,
                Body: ioutil.NopCloser(strings.NewReader("test")),
            }, nil
        }
        if method == http.MethodPatch {
            body := ioutil.NopCloser(strings.NewReader(`{"id":"mock-upload-id"}`))
            return &http.Response{StatusCode: http.StatusOK, Body: body}, nil
        }
    }

    return nil, nil
}

func TestUploadedFileExists(t *testing.T) {
	err := uploader.Upload(existingFile.Name(), false, false)
	if err == nil {
		t.Error()
	}
}

func TestUploadFile(t *testing.T) {
	err := uploader.Upload(file.Name(), false, false)
	if err != nil {
		t.Error(err)
	}
}

func TestUploadFolder(t *testing.T) {
	err := uploader.Upload(dir, false, false)
	if err == nil || !strings.HasSuffix(err.Error(), "not a Crypt4GH file") {
		t.Error(err)
	}
}

func TestDownloadFileRemoteDoesntExist(t *testing.T) {
	err := uploader.Download("notfoundfile.enc")
	if err == nil || !strings.HasSuffix(err.Error(), "not found in the outbox.") {
		t.Error(err)
	}
}

func TestDownloadFileRemoteExists(t *testing.T) {
    os.Remove("test2.enc")
	err := uploader.Download("test2.enc")
	if err != nil {
		t.Error(err)
	}
	file, err := os.Open("test2.enc")
	if err != nil {
		t.Error(err)
	}
	test.ReadString(file, "test")
	err = file.Close()
	if err != nil {
		t.Error(err)
	}
	err = os.Remove("test2.enc")
	if err != nil {
		t.Error(err)
	}
}

func TestDownloadFileLocalExists(t *testing.T) {
	_, err := os.Create("test2.enc")
	if err != nil {
		t.Error(err)
	}
	err = uploader.Download("test2.enc")
	if err == nil || !strings.HasSuffix(err.Error(), "exists locally, aborting.") {
		t.Error(err)
	}
	err = os.Remove("test2.enc")
	if err != nil {
		t.Error(err)
	}
}

func teardown() {
	_ = os.Unsetenv("CENTRAL_EGA_USERNAME")
	_ = os.Unsetenv("CENTRAL_EGA_PASSWORD")
	_ = os.Unsetenv("LOCAL_EGA_INSTANCE_URL")
	_ = os.Unsetenv("ELIXIR_AAI_TOKEN")
}
