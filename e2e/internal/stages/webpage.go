package stages

import (
	"context"
	"fmt"
	"strings"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/httpx"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/check"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
)

const (
	proxyWebpageMarker       = "FEGA Proxy E2E Test Page"
	proxyExportRequestMarker = "Export Request Form"
)

// ProxyWebpage verifies that the proxy:
//   - returns 404 for a static page that does not exist,
//   - serves the mounted index page,
//   - serves the classpath export-request page.
func ProxyWebpage(ctx context.Context, s *state.State) error {
	client := httpx.New(s.Config)
	baseURL := "https://" + s.Config.ProxyHost + ":" + s.Config.ProxyPort

	missingResponse, err := client.Do(
		ctx,
		"GET",
		baseURL+"/missing-page.html",
	)
	if err != nil {
		return fmt.Errorf("failed to request a missing proxy webpage: %w", err)
	}

	if err := check.Equal(
		missingResponse.Status,
		404,
		"missing proxy webpage HTTP status",
	); err != nil {
		return err
	}

	s.Log.Check(
		fmt.Sprintf(
			"Negative test: request for missing proxy webpage (HTTP %d)",
			missingResponse.Status,
		),
		true,
	)

	if err := check.True(
		!strings.Contains(string(missingResponse.Body), proxyWebpageMarker),
		"missing-page response unexpectedly contains the mounted index page",
	); err != nil {
		return err
	}

	indexResponse, err := client.Do(ctx, "GET", baseURL+"/")
	if err != nil {
		return fmt.Errorf("failed to retrieve the proxy index page: %w", err)
	}

	if err := check.Equal(
		indexResponse.Status,
		200,
		"proxy index page HTTP status",
	); err != nil {
		return err
	}

	indexBody := string(indexResponse.Body)

	if err := check.True(
		strings.Contains(strings.ToLower(indexBody), "<!doctype html>"),
		"proxy index response does not contain an HTML document type",
	); err != nil {
		return err
	}

	if err := check.True(
		strings.Contains(strings.ToLower(indexBody), "<html"),
		"proxy index response does not contain an HTML element",
	); err != nil {
		return err
	}

	if err := check.True(
		strings.Contains(indexBody, proxyWebpageMarker),
		fmt.Sprintf(
			"proxy index response does not contain the expected marker %q",
			proxyWebpageMarker,
		),
	); err != nil {
		return err
	}

	s.Log.Check(
		fmt.Sprintf(
			"Positive test: mounted proxy index page verified (HTTP %d)",
			indexResponse.Status,
		),
		true,
	)

	exportResponse, err := client.Do(
		ctx,
		"GET",
		baseURL+"/export-request.html",
	)
	if err != nil {
		return fmt.Errorf(
			"failed to retrieve the proxy classpath webpage: %w",
			err,
		)
	}

	if err := check.Equal(
		exportResponse.Status,
		200,
		"proxy classpath webpage HTTP status",
	); err != nil {
		return err
	}

	if err := check.True(
		strings.Contains(
			string(exportResponse.Body),
			proxyExportRequestMarker,
		),
		fmt.Sprintf(
			"proxy classpath webpage does not contain the expected marker %q",
			proxyExportRequestMarker,
		),
	); err != nil {
		return err
	}

	s.Log.Check(
		fmt.Sprintf(
			"Positive test: proxy classpath webpage verified (HTTP %d)",
			exportResponse.Status,
		),
		true,
	)

	return nil
}
