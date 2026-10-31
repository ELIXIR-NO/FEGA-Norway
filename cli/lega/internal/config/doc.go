// Package config turns the process environment into one validated Config.
// It reads variables only through the getenv function it is given, so a
// test passes a map and nothing here touches the real environment.
package config
