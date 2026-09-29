package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

// The helper's Chrome: her installed Chrome, with a profile of its own in the helper's folder,
// so portal sign-ins and saved passwords there stay put between runs and never mix with her
// everyday Chrome.

// findChrome is where Chrome is. CREDCLOUD_BROWSER points at another Chromium.
func findChrome() (string, error) {
	if path := os.Getenv("CREDCLOUD_BROWSER"); path != "" {
		return path, nil
	}
	var candidates []string
	switch runtime.GOOS {
	case "darwin":
		home, _ := os.UserHomeDir()
		for _, app := range []string{"Google Chrome.app/Contents/MacOS/Google Chrome",
			"Microsoft Edge.app/Contents/MacOS/Microsoft Edge"} {
			candidates = append(candidates, filepath.Join("/Applications", app), filepath.Join(home, "Applications", app))
		}
	case "windows":
		for _, base := range []string{os.Getenv("ProgramFiles"), os.Getenv("ProgramFiles(x86)"), os.Getenv("LocalAppData")} {
			if base != "" {
				candidates = append(candidates, filepath.Join(base, `Google\Chrome\Application\chrome.exe`),
					filepath.Join(base, `Microsoft\Edge\Application\msedge.exe`))
			}
		}
	default:
		for _, name := range []string{"google-chrome", "chromium", "chromium-browser"} {
			if path, err := exec.LookPath(name); err == nil {
				candidates = append(candidates, path)
			}
		}
	}
	for _, path := range candidates {
		if _, err := os.Stat(path); err == nil {
			return path, nil
		}
	}
	return "", errors.New("CredCloud Helper needs Google Chrome or Microsoft Edge")
}

// openBrowser connects to the helper's Chrome, starting it if it isn't running.
func openBrowser() (*Browser, error) {
	cdp, err := launchChrome(filepath.Join(helperDir(), "Browser"))
	if err != nil {
		return nil, err
	}
	return NewBrowser(context.Background(), cdp)
}

// launchChrome connects to the Chrome using this profile. A Chrome the helper started before
// and left open is reused: its profile says which port it listens on. Otherwise it starts one.
func launchChrome(profile string) (*CDP, error) {
	portFile := filepath.Join(profile, "DevToolsActivePort")
	if cdp, err := connectExisting(portFile); err == nil {
		return cdp, nil
	}
	chrome, err := findChrome()
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(profile, 0o700); err != nil {
		return nil, err
	}
	os.Remove(portFile) // left by a Chrome that's gone

	args := []string{"--user-data-dir=" + profile, "--remote-debugging-port=0", "--no-first-run", "--no-default-browser-check"}
	// For tests: --headless=new and the like.
	args = append(args, strings.Fields(os.Getenv("CREDCLOUD_BROWSER_ARGS"))...)
	cmd := exec.Command(chrome, append(args, "about:blank")...)
	if err := cmd.Start(); err != nil {
		return nil, err
	}
	exited := make(chan error, 1)
	go func() { exited <- cmd.Wait() }()

	for i := 0; i < 100; i++ {
		select {
		case err := <-exited:
			return nil, fmt.Errorf("Chrome exited right away (%v): is another Chrome using %s?", err, profile)
		default:
		}
		if cdp, err := connectExisting(portFile); err == nil {
			return cdp, nil
		}
		time.Sleep(200 * time.Millisecond)
	}
	return nil, errors.New("Chrome never wrote DevToolsActivePort")
}

func connectExisting(portFile string) (*CDP, error) {
	data, err := os.ReadFile(portFile)
	if err != nil {
		return nil, err
	}
	lines := strings.Split(strings.TrimSpace(string(data)), "\n")
	if len(lines) != 2 {
		return nil, errors.New("DevToolsActivePort isn't complete yet")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	return Dial(ctx, "ws://127.0.0.1:"+lines[0]+lines[1])
}
