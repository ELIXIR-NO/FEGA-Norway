package config

import "strings"

// Problem is one missing or invalid variable and what is wrong with it.
type Problem struct {
	Var, Reason string
}

// Error reports every missing or invalid variable of one Load. Its message
// never quotes a token, password or URL.
type Error struct {
	Problems []Problem
}

// Error names every variable at fault, in the order Load checked them.
func (e *Error) Error() string {
	parts := make([]string, len(e.Problems))
	for i, p := range e.Problems {
		parts[i] = p.Var + " " + p.Reason
	}
	return "environment: " + strings.Join(parts, "; ")
}
