//go:build windows

package main

import (
	"os"
	"os/exec"
	"syscall"
)

// lockFile holds path open with no sharing, which keeps a second helper from opening it,
// until release is called or the process ends.
func lockFile(path string) (func(), error) {
	name, err := syscall.UTF16PtrFromString(path)
	if err != nil {
		return nil, err
	}
	handle, err := syscall.CreateFile(name, syscall.GENERIC_READ|syscall.GENERIC_WRITE, 0, nil,
		syscall.OPEN_ALWAYS, syscall.FILE_ATTRIBUTE_NORMAL, 0)
	if err != nil {
		return nil, err
	}
	released := false
	return func() {
		if !released {
			released = true
			syscall.CloseHandle(handle)
		}
	}, nil
}

const (
	createNewProcessGroup = 0x00000200
	detachedProcess       = 0x00000008
)

func detach(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{CreationFlags: createNewProcessGroup | detachedProcess}
}

// The helper is built as a Windows app, without a console window, so there's no stderr.
func stderrIsUseful() bool { return false }

// replaceExecutable swaps in a new copy. Windows won't overwrite a running program, but it
// will rename it out of the way.
func replaceExecutable(exe, next string) error {
	old := exe + ".old"
	_ = os.Remove(old)
	if err := os.Rename(exe, old); err != nil {
		return err
	}
	if err := os.Rename(next, exe); err != nil {
		_ = os.Rename(old, exe)
		return err
	}
	return nil
}
