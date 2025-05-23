// Package files contains structures and methods for listing or deleting uploaded files.
package files

import (
"fmt"
	"errors"
	"io/ioutil"
	"net/http"
	"strconv"
	"strings"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega-commander/conf"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega-commander/requests"
	"github.com/buger/jsonparser"
)

// File structure represents uploaded File.
type File struct {
	FileName     string
	Size         int64
	ModifiedDate string
}

// FileManager interface provides method for managing uploaded files.
type FileManager interface {
	ListFiles(inbox bool, page, perPage int, fetchAll bool) (*[]File, error)
	DeleteFile(fileName string) error
}

type defaultFileManager struct {
	client requests.Client
}

// Represents an error message to handle a missing or empty folder
type FolderNotFoundError struct {
	Msg string
}

// Returns the error message in FolderNotFoundError.
func (e *FolderNotFoundError) Error() string {
	return e.Msg
}

// NewFileManager constructs FileManager using requests.Client.
func NewFileManager(client *requests.Client) (FileManager, error) {
	fileManager := defaultFileManager{}
	if client != nil {
		fileManager.client = *client
	} else {
		fileManager.client = requests.NewClient(nil)
	}
	return fileManager, nil
}

// ListFiles method lists uploaded files.
func (fm defaultFileManager) ListFiles(
	inbox bool,
	page, perPage int,
	fetchAll bool,
) (*[]File, error) {

	cfg := conf.NewConfiguration()

	user, pass := "", ""
	if inbox {
		user = cfg.GetCentralEGAUsername()
		pass = cfg.GetCentralEGAPassword()
	}

	var out []File
	current := page

	for {
		params := map[string]string{
			"inbox":    strconv.FormatBool(inbox),
			"page":     strconv.Itoa(current),
			"per_page": strconv.Itoa(perPage),
		}
		resp, err := fm.client.DoRequest(
			http.MethodGet,
			cfg.GetLocalEGAInstanceURL()+"/files",
			nil,
			map[string]string{"Proxy-Authorization": "Bearer " + cfg.GetElixirAAIToken()},
			params,
			user, pass,
		)
		if err != nil {
			return nil, err
		}

		if resp.StatusCode == http.StatusForbidden {
			body, _ := ioutil.ReadAll(resp.Body)
			_ = resp.Body.Close()
			if strings.Contains(string(body), `"tsdFiles" is null`) {
				return nil, &FolderNotFoundError{}
			}
			return nil, errors.New("authentication error")
		}
		if resp.StatusCode != http.StatusOK {
			return nil, errors.New(resp.Status)
		}

		body, _ := ioutil.ReadAll(resp.Body)
		_ = resp.Body.Close()
fmt.Println("Raw JSON response:", string(body))

		pageFiles := make([]File, 0)

_, vt, _, err := jsonparser.Get(body)
if err == nil && vt == jsonparser.Array {
    // Case 1: proxy returns simple string array ["fileA", "fileB"]
    jsonparser.ArrayEach(body, func(v []byte, _ jsonparser.ValueType, _ int, _ error) {
        if fname, err := jsonparser.ParseString(v); err == nil {
            if fname == "statusCode" || fname == "statusText" {
                return
            }
            pageFiles = append(pageFiles, File{FileName: fname})
        }
    })
} else {
    // Case 2: proxy returns wrapped objects {"files":[{...}, {...}]}
    jsonparser.ArrayEach(body, func(v []byte, _ jsonparser.ValueType, _ int, _ error) {
        name, _ := jsonparser.GetString(v, "fileName")
        size, _ := jsonparser.GetInt(v, "size")
        date, _ := jsonparser.GetString(v, "modifiedDate")
        pageFiles = append(pageFiles, File{name, size, date})
    }, "files")
}

		out = append(out, pageFiles...)

		if !fetchAll || len(pageFiles) == 0 {
			break
		}
		current++
	}

	return &out, nil
}

// DeleteFiles method deletes uploaded file by its name.
func (rm defaultFileManager) DeleteFile(fileName string) error {
	configuration := conf.NewConfiguration()
	response, err := rm.client.DoRequest(http.MethodDelete,
		configuration.GetLocalEGAInstanceURL()+"/files",
		nil,
		map[string]string{"Proxy-Authorization": "Bearer " + configuration.GetElixirAAIToken()},
		map[string]string{"fileName": fileName},
		configuration.GetCentralEGAUsername(),
		configuration.GetCentralEGAPassword())
	if err != nil {
		return err
	}
	if response.StatusCode != 200 {
		return errors.New(response.Status)
	}
	return nil
}
