package cli

import (
	"context"
	"fmt"
	"strings"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/config"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/proxy"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/transfer"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/tsd"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/ui"
)

type uploadCommand struct {
	env      *env
	Direct   bool `long:"direct" description:"Send the data straight to the TSD file API; only the final notification goes through the proxy"`
	Force    bool `long:"force" description:"Upload even if the inbox already has a file with the same name"`
	NoResume bool `long:"no-resume" description:"Start over instead of resuming an interrupted upload"`
	Retries  int  `long:"retries" default:"0" value-name:"N" description:"Retry a failed request up to N times, 0 to 5"`
	Args     struct {
		Paths []string `positional-arg-name:"PATH" required:"1"`
	} `positional-args:"yes"`
}

func newUploadCommand(e *env) command {
	return command{
		name:  "upload",
		short: "Upload files or folders to your inbox",
		long: `Uploads files, or folders recursively, to your inbox. Every file must
already be Crypt4GH-encrypted. Files are checked before anything is sent.
If an upload was interrupted, running the same command again resumes it.`,
		data: &uploadCommand{env: e},
	}
}

// Execute checks every file, plans the batch against the inbox and the
// interrupted uploads, and sends nothing unless every file passes.
func (c *uploadCommand) Execute(args []string) error {
	if err := c.validate(args); err != nil {
		return err
	}
	cfg, err := c.env.loadConfig(c.need())
	if err != nil {
		return err
	}
	local, err := c.scan()
	if err != nil {
		return err
	}
	client := c.env.proxyClient(cfg)
	runner := c.env.runner(cfg, c.Retries)
	steps, err := c.plan(runner, client, local)
	if err != nil {
		return err
	}
	if rejected := rejectedSteps(steps); len(rejected) > 0 {
		return rejectedError(rejected)
	}
	up, err := c.uploader(cfg, client, steps)
	if err != nil {
		return err
	}
	return c.finish(runner.Upload(c.env.ctx, up, steps))
}

func (c *uploadCommand) validate(args []string) error {
	if err := noArguments("upload", args); err != nil {
		return err
	}
	return validateRetries(c.Retries)
}

// scan refuses a batch with no files, which would otherwise end in a
// summary of nothing and exit 0.
func (c *uploadCommand) scan() ([]plan.LocalFile, error) {
	local, err := transfer.Scan(c.Args.Paths)
	if err != nil {
		return nil, err
	}
	if len(local) == 0 {
		return nil, fmt.Errorf("nothing to upload: no files found in %s", strings.Join(c.Args.Paths, ", "))
	}
	return local, nil
}

// need asks for the TSD variables only in direct mode, which is the only
// place TSDProxyURL is read.
func (c *uploadCommand) need() config.Need {
	if c.Direct {
		return config.Token | config.CEGA | config.TSD
	}
	return config.Token | config.CEGA
}

// plan lists the inbox and the interrupted uploads once for the whole
// batch. Direct mode lists through the proxy too.
func (c *uploadCommand) plan(r transfer.Runner, client *proxy.Client, local []plan.LocalFile) ([]plan.Step, error) {
	inbox, err := c.inbox(r, client)
	if err != nil {
		return nil, err
	}
	resumables, err := c.resumables(r, client)
	if err != nil {
		return nil, err
	}
	return plan.PlanUpload(local, inbox, resumables, plan.UploadOptions{Force: c.Force, NoResume: c.NoResume}), nil
}

// inbox lists the inbox for the duplicate check, which --force skips.
func (c *uploadCommand) inbox(r transfer.Runner, client *proxy.Client) ([]plan.RemoteFile, error) {
	if c.Force {
		return nil, nil
	}
	var files []plan.RemoteFile
	err := r.Retry(c.env.ctx, func(ctx context.Context) (err error) {
		files, err = client.ListAllFiles(ctx, true)
		return err
	})
	if duplicateCheckMayProceed(err) {
		ui.Warnf(c.env.stderr, "the duplicate check could not run (%v); uploading anyway", err)
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("check the inbox for files with the same name: %w", err)
	}
	return files, nil
}

// duplicateCheckMayProceed lets a bodyless 403 through on the duplicate
// check alone: until the proxy sends error codes (FEGA-Norway#913) it is
// also what a first-time user's missing folder looks like. If the
// credentials are really wrong, the first chunk fails on the same 403.
func duplicateCheckMayProceed(err error) bool {
	return proxy.IsBareRefusal(err)
}

// resumables lists the interrupted uploads, which --no-resume ignores.
func (c *uploadCommand) resumables(r transfer.Runner, client *proxy.Client) ([]plan.Resumable, error) {
	if c.NoResume {
		return nil, nil
	}
	var rs []plan.Resumable
	err := r.Retry(c.env.ctx, func(ctx context.Context) (err error) {
		rs, err = client.ListResumables(ctx)
		return err
	})
	if err != nil {
		return nil, fmt.Errorf("list interrupted uploads: %w", err)
	}
	return rs, nil
}

// uploader is the proxy itself, or in direct mode TSD with the proxy kept
// for the resumables lookup and the notification. The TSD token is fetched
// only when there is something to send.
func (c *uploadCommand) uploader(cfg config.Config, client *proxy.Client, steps []plan.Step) (transfer.Uploader, error) {
	if !c.Direct || !sendsAnything(steps) {
		return client, nil
	}
	token, err := tsd.Exchange(c.env.ctx, client.HTTP, cfg.TSDProxyURL, cfg.TSDProjName, cfg.ElixirAAIToken)
	if err != nil {
		return nil, fmt.Errorf("get a TSD token: %w", err)
	}
	direct := &tsd.Client{BaseURL: cfg.TSDProxyURL, Project: cfg.TSDProjName, Token: token, HTTP: client.HTTP}
	return transfer.DirectUploader{TSD: direct, Proxy: client}, nil
}

func sendsAnything(steps []plan.Step) bool {
	for _, s := range steps {
		if s.Action == plan.Upload || s.Action == plan.Resume {
			return true
		}
	}
	return false
}

func (c *uploadCommand) finish(rep transfer.Report) error {
	if err := c.env.renderBatch(uploadBatch(rep)); err != nil {
		return err
	}
	return interrupted(c.env.ctx, reportError(rep), "run the same command again to resume")
}

func rejectedSteps(steps []plan.Step) []plan.Step {
	var rejected []plan.Step
	for _, s := range steps {
		if s.Action == plan.Reject {
			rejected = append(rejected, s)
		}
	}
	return rejected
}

// rejectedError lists every file that stopped the batch, so the user can
// fix them all before trying again.
func rejectedError(rejected []plan.Step) error {
	rows := make([][]string, len(rejected))
	for i, s := range rejected {
		rows[i] = []string{s.File.Path, s.Reason}
	}
	noun := "files need"
	if len(rejected) == 1 {
		noun = "file needs"
	}
	return fmt.Errorf("nothing was uploaded; %d %s attention:%s", len(rejected), noun, ui.Indented(rows))
}

func uploadBatch(rep transfer.Report) ui.Batch {
	lines := make([]ui.Line, len(rep.Results))
	for i, res := range rep.Results {
		lines[i] = ui.Line{Outcome: res.Outcome.String(), Name: res.Name, Detail: uploadDetail(res)}
	}
	return ui.Batch{Lines: lines, Tallies: []ui.Tally{
		{Word: "uploaded", N: rep.Count(transfer.Uploaded, transfer.Resumed)},
		{Word: "skipped", N: rep.Count(transfer.Skipped)},
		{Word: "failed", N: rep.Failed()},
	}}
}

func uploadDetail(res transfer.Result) string {
	switch res.Outcome {
	case transfer.Uploaded:
		return ui.HumanSize(res.Size)
	case transfer.Resumed:
		return fmt.Sprintf("%s, from %s", ui.HumanSize(res.Size), ui.HumanSize(res.From))
	case transfer.Skipped:
		return skipDetail(res.Reason)
	case transfer.Failed:
		return res.Err.Error()
	default:
		return res.Reason
	}
}

func skipDetail(reason string) string {
	if reason == plan.InInbox {
		return reason + " (--force replaces it)"
	}
	return reason
}

var (
	_ transfer.Uploader   = (*proxy.Client)(nil)
	_ transfer.Downloader = (*proxy.Client)(nil)
	_ transfer.Notifier   = (*proxy.Client)(nil)
	_ transfer.TSDClient  = (*tsd.Client)(nil)
)
