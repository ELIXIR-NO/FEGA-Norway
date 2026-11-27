// Package transfer runs an upload or download plan against a backend. It
// scans local paths, sends chunks through the Uploader interface and
// reports progress through a callback; it never prints.
package transfer
