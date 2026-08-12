// Command e2e-gdi is a placeholder for the GDI pipeline. It exits non-zero so
// a run can never be mistaken for a pass. Selected by E2E_ENV=gdi at the
// container entrypoint.
package main

import (
	"fmt"
	"os"
)

func main() {
	fmt.Fprintln(os.Stderr, "e2e-gdi: the GDI pipeline is not implemented")
	os.Exit(1)
}
