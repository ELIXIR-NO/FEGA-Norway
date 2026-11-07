package cli

import (
	"context"
	"fmt"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/config"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/proxy"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/ui"
)

// PageOptions are the paging flags of inbox ls and outbox ls.
type PageOptions struct {
	All     bool `long:"all" description:"List every file, ignoring --page"`
	Page    int  `long:"page" default:"1" value-name:"N" description:"Page to list, from 1"`
	PerPage int  `short:"p" long:"per-page" default:"100" value-name:"N" description:"Files per page, 1 to 50000"`
}

func (o PageOptions) validate() error {
	switch {
	case o.All:
		return nil
	case o.Page < 1:
		return usageError{msg: fmt.Sprintf("--page must be 1 or more, got %d", o.Page)}
	case o.PerPage < 1 || o.PerPage > proxy.MaxPerPage:
		return usageError{msg: fmt.Sprintf("--per-page must be from 1 to %d, got %d", proxy.MaxPerPage, o.PerPage)}
	default:
		return nil
	}
}

func (o PageOptions) page() ui.Page {
	if o.All {
		return ui.Page{}
	}
	return ui.Page{Number: o.Page, PerPage: o.PerPage}
}

// listFiles is inbox ls and outbox ls. The outbox needs no CEGA
// credentials: Data Out checks only the ELIXIR token.
func (e *env) listFiles(args []string, inbox bool, o PageOptions) error {
	if err := noArguments("ls", args); err != nil {
		return err
	}
	if err := o.validate(); err != nil {
		return err
	}
	cfg, err := e.loadConfig(listingNeed(inbox))
	if err != nil {
		return err
	}
	files, err := fetchFiles(e.ctx, e.proxyClient(cfg), inbox, o)
	if err != nil {
		return err
	}
	return e.renderFiles(uiFiles(files), o.page(), emptyMessage(inbox))
}

func listingNeed(inbox bool) config.Need {
	if inbox {
		return config.Token | config.CEGA
	}
	return config.Token
}

func fetchFiles(ctx context.Context, c *proxy.Client, inbox bool, o PageOptions) ([]plan.RemoteFile, error) {
	if o.All {
		return c.ListAllFiles(ctx, inbox)
	}
	return c.ListFiles(ctx, inbox, o.Page, o.PerPage)
}

func emptyMessage(inbox bool) string {
	if inbox {
		return "Your inbox is empty."
	}
	return "Your outbox is empty."
}

func (e *env) renderFiles(files []ui.File, page ui.Page, empty string) error {
	if e.json {
		return e.renderJSON(files)
	}
	return e.styles.Files(e.stdout, files, page, empty)
}

// uiFiles never returns nil, so an empty listing is [] in JSON.
func uiFiles(files []plan.RemoteFile) []ui.File {
	out := make([]ui.File, len(files))
	for i, f := range files {
		out[i] = ui.File{Name: f.Name, Size: f.Size, Modified: f.Modified}
	}
	return out
}
