// Package plan decides what an upload or a download will do before anything
// is sent. Its functions are pure: plain values in, plain values out, with no
// I/O, no clock and no randomness, so every rule a user can trip over is a
// table test row.
package plan
