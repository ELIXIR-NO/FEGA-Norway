// Package proxy is the client for localega-tsd-proxy. Every call takes a
// context, every non-2xx answer becomes an *APIError carrying the proxy's
// body, and no header ever reaches an error, so a token cannot leak through
// one.
package proxy
