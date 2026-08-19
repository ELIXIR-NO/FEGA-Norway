// Package check provides the assertion helpers the stages use. Each helper
// returns an error rather than panicking, so the sequential pipeline aborts on
// the first failed assertion by returning that error up the call stack.
package check

import (
	"encoding/json"
	"fmt"
	"reflect"
)

// Equal returns an error unless got == want.
func Equal[T comparable](got, want T, msg string) error {
	if got != want {
		return fmt.Errorf("%s: expected %v, got %v", msg, want, got)
	}
	return nil
}

// True returns an error unless cond holds.
func True(cond bool, msg string) error {
	if !cond {
		return fmt.Errorf("assertion failed: %s", msg)
	}
	return nil
}

// False returns an error unless cond is false.
func False(cond bool, msg string) error {
	if cond {
		return fmt.Errorf("assertion failed: %s", msg)
	}
	return nil
}

// Failf builds an error.
func Failf(format string, args ...any) error {
	return fmt.Errorf(format, args...)
}

// JSONEqualLenient checks that actual contains expected as a subset: every field
// in expected must appear with an equal value in actual, but actual may carry
// extra fields. Object key order is irrelevant. Arrays must have equal length
// and their elements are matched order-insensitively.
func JSONEqualLenient(expected, actual []byte, msg string) error {
	var e, a any
	if err := json.Unmarshal(expected, &e); err != nil {
		return fmt.Errorf("%s: expected is not valid JSON: %w", msg, err)
	}
	if err := json.Unmarshal(actual, &a); err != nil {
		return fmt.Errorf("%s: actual is not valid JSON: %w", msg, err)
	}
	if !lenientMatch(e, a) {
		return fmt.Errorf("%s: JSON mismatch\nexpected (subset): %s\nactual: %s", msg, expected, actual)
	}
	return nil
}

func lenientMatch(expected, actual any) bool {
	switch exp := expected.(type) {
	case map[string]any:
		act, ok := actual.(map[string]any)
		if !ok {
			return false
		}
		for k, ev := range exp {
			av, present := act[k]
			if !present || !lenientMatch(ev, av) {
				return false
			}
		}
		return true
	case []any:
		act, ok := actual.([]any)
		if !ok || len(act) != len(exp) {
			return false
		}
		// Order-insensitive: every expected element must match some
		// not-yet-consumed actual element.
		used := make([]bool, len(act))
		for _, ev := range exp {
			matched := false
			for i, av := range act {
				if used[i] {
					continue
				}
				if lenientMatch(ev, av) {
					used[i] = true
					matched = true
					break
				}
			}
			if !matched {
				return false
			}
		}
		return true
	default:
		return reflect.DeepEqual(expected, actual)
	}
}
