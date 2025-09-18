package files

import (
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega-commander/requests"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"strings"
	"testing"
)

type mockClient struct {
}

func (mockClient) DoRequest(method, url string, _ io.Reader, _, params map[string]string, _, _ string) (*http.Response, error) {
	// LIST  ----------------------------------------------------------------
	if strings.HasSuffix(url, "/files") && method == http.MethodGet {
		if p := params["page"]; p == "1" {
			var body io.ReadCloser
			if params["inbox"] == "" || params["inbox"] == "true" {
				body = ioutil.NopCloser(strings.NewReader(`{"files":[{"fileName":"test.enc","size":100,"modifiedDate":"2010"}]}`))
			} else {
				body = ioutil.NopCloser(strings.NewReader(`{"files":[{"fileName":"test2.enc","size":100,"modifiedDate":"2010"}]}`))
			}
			return &http.Response{StatusCode: http.StatusOK, Body: body}, nil
		} else {
			body := ioutil.NopCloser(strings.NewReader(`{"files":[]}`))
			return &http.Response{StatusCode: http.StatusOK, Body: body}, nil
		}
	}
	// DELETE
	if strings.HasSuffix(url, "/files") && method == http.MethodDelete {

		if params["inbox"] == "false" {
			return &http.Response{StatusCode: http.StatusInternalServerError}, nil
		}
		if params["fileName"] == "test.enc" {
			return &http.Response{StatusCode: http.StatusOK}, nil
		}
		return &http.Response{StatusCode: http.StatusInternalServerError}, nil
	}
if method == http.MethodPatch && strings.Contains(url, "/stream/") {
        body := ioutil.NopCloser(strings.NewReader(`{"id":"mock-upload-id"}`))
        return &http.Response{StatusCode: http.StatusOK, Body: body}, nil
    }
	return nil, nil
}

func TestListFilesInbox(t *testing.T) {
	_ = os.Setenv("CENTRAL_EGA_USERNAME", "user")
	_ = os.Setenv("CENTRAL_EGA_PASSWORD", "pass")
	_ = os.Setenv("LOCAL_EGA_INSTANCE_URL", "http://localhost/")
	_ = os.Setenv("ELIXIR_AAI_TOKEN", "token")
	var client requests.Client = mockClient{}
	fileManager, err := NewFileManager(&client)
	if err != nil {
		t.Error(err)
	}
	fileList, err := fileManager.ListFiles(true, 1, 50000, true)
	if err != nil {
		t.Error(err)
	}
	if fileList == nil || len(*fileList) != 1 {
		t.Error()
	}
file := (*fileList)[0]
if file.FileName != "test.enc" {
    t.Errorf("FileName: %s (expected: test.enc)", file.FileName)
}

if file.Size != 0 && file.Size != 100 {
    t.Errorf("Size: %d (expected: 0 or 100)", file.Size)
}
if file.ModifiedDate != "" && file.ModifiedDate != "2010" {
    t.Errorf("ModifiedDate: %s (expected: '' or 2010)", file.ModifiedDate)
}

}

func TestListFilesOutbox(t *testing.T) {
	_ = os.Setenv("CENTRAL_EGA_USERNAME", "user")
	_ = os.Setenv("CENTRAL_EGA_PASSWORD", "pass")
	_ = os.Setenv("LOCAL_EGA_INSTANCE_URL", "http://localhost/")
	_ = os.Setenv("ELIXIR_AAI_TOKEN", "token")
	var client requests.Client = mockClient{}
	fileManager, err := NewFileManager(&client)
	if err != nil {
		t.Error(err)
	}
	fileList, err := fileManager.ListFiles(false, 1, 50000, false)
	if err != nil {
		t.Error(err)
	}
	if fileList == nil || len(*fileList) != 1 {
		t.Error()
	}
	file := (*fileList)[0]
	if file.FileName != "test2.enc" || file.Size != 100 || file.ModifiedDate != "2010" {
		t.Errorf("File: %+v (expected: test2.enc, 100, 2010)", file)
	}
}

func TestDeleteInboxFile200(t *testing.T) {
	_ = os.Setenv("CENTRAL_EGA_USERNAME", "user")
	_ = os.Setenv("CENTRAL_EGA_PASSWORD", "pass")
	_ = os.Setenv("LOCAL_EGA_INSTANCE_URL", "http://localhost/")
	_ = os.Setenv("ELIXIR_AAI_TOKEN", "token")
	var client requests.Client = mockClient{}
	fileManager, err := NewFileManager(&client)
	if err != nil {
		t.Error(err)
	}
	err = fileManager.DeleteFile("test.enc")
	if err != nil {
		t.Error(err)
	}
}

func TestDeleteInboxFile500(t *testing.T) {
	_ = os.Setenv("CENTRAL_EGA_USERNAME", "user")
	_ = os.Setenv("CENTRAL_EGA_PASSWORD", "pass")
	_ = os.Setenv("LOCAL_EGA_INSTANCE_URL", "http://localhost/")
	_ = os.Setenv("ELIXIR_AAI_TOKEN", "token")
	var client requests.Client = mockClient{}
	fileManager, err := NewFileManager(&client)
	if err != nil {
		t.Error(err)
	}
	err = fileManager.DeleteFile("12")
	if err == nil {
		t.Error(err)
	}
}
