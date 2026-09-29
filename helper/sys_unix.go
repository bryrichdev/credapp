//go:build !windows

package main

import (
	"os"
	"os/exec"
	"syscall"
)

// lockFile holds an exclusive lock on path until release is called or the process ends.
func lockFile(path string) (func(), error) {
	f, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0o600)
	if err != nil {
		return nil, err
	}
	if err := syscall.Flock(int(f.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil {
		f.Close()
		return nil, err
	}
	released := false
	return func() {
		if !released {
			released = true
			_ = syscall.Flock(int(f.Fd()), syscall.LOCK_UN)
			f.Close()
		}
	}, nil
}

// detach starts a process in a session of its own, so it outlives the helper.
func detach(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{Setsid: true}
}

func stderrIsUseful() bool {
	info, err := os.Stderr.Stat()
	return err == nil && info.Mode()&os.ModeCharDevice != 0
}

// replaceExecutable swaps in a new copy. A running program's file can be renamed over.
func replaceExecutable(exe, next string) error {
	return os.Rename(next, exe)
}
