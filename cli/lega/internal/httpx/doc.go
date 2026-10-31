// Package httpx holds the HTTP plumbing the proxy and TSD clients share: the
// transport and its timeouts, path building, capped error bodies and the
// stall guard. It knows nothing about either API.
package httpx
