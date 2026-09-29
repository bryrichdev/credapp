//go:build !darwin && !windows

package main

import (
	"errors"
	"fmt"
	"os"
	"os/exec"
)

// Linux: for development and tests. Run `credcloud-helper run --server <address>`; there's
// no installer and no link handler.

var errNotSupported = errors.New("installing is for a Mac or Windows; here, run `go run . pair <server> <code>` and `go run .`")

func isInstalled(string) bool { return false }

func needsInstall() bool { return false }

func afterUpdate(string) {}

func installCommand([]string) int {
	fmt.Fprintln(os.Stderr, errNotSupported)
	return 1
}

func uninstall() int {
	fmt.Fprintln(os.Stderr, errNotSupported)
	return 1
}

func openInDefaultBrowser(url string) error {
	return exec.Command("xdg-open", url).Start()
}
