//go:build windows

package main

import (
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"time"
)

// On Windows the helper installs itself for the signed-in user, with no administrator:
// the program in %LOCALAPPDATA%\Programs\CredCloud Helper, a Start menu shortcut, the
// credcloud:// link handler, starting when she signs in, and an entry in Installed apps, all
// under HKEY_CURRENT_USER.
// Run from anywhere else (a download), it installs itself first.

func installDir() string {
	base := os.Getenv("LOCALAPPDATA")
	if base == "" {
		base, _ = os.UserConfigDir()
	}
	return filepath.Join(base, "Programs", "CredCloud Helper")
}

func installedExe() string { return filepath.Join(installDir(), "credcloud-helper.exe") }

func isInstalled(exe string) bool {
	return strings.EqualFold(filepath.Clean(exe), filepath.Clean(installedExe()))
}

func needsInstall() bool {
	exe, err := executable()
	return err == nil && !isInstalled(exe) && os.Getenv("CREDCLOUD_HELPER_NO_INSTALL") == ""
}

func afterUpdate(string) {}

func installCommand(args []string) int {
	fs := flag.NewFlagSet("install", flag.ContinueOnError)
	serverFlag := fs.String("server", defaultServer, "CredCloud's address")
	code := fs.String("code", "", "a one-time code from CredCloud that connects this computer")
	report := fs.String("report", "", "a file to write what happened to")
	if err := fs.Parse(args); err != nil {
		return 2
	}
	var lines []string
	say := func(format string, a ...any) { lines = append(lines, fmt.Sprintf(format, a...)) }
	defer func() {
		if *report != "" {
			_ = os.WriteFile(*report, []byte(strings.Join(lines, "\r\n")+"\r\n"), 0o600)
		}
	}()

	server, err := allowedServer(*serverFlag)
	if err != nil {
		say("%v", err)
		return 2
	}
	exe, err := executable()
	if err != nil {
		say("Couldn't find this program: %v", err)
		return 1
	}
	dir := helperDir()
	if err := os.MkdirAll(dir, 0o700); err != nil {
		say("%v", err)
		return 1
	}
	setUpLog(dir)

	forwardToRunningQuick(dir, "quit")
	if release, err := waitForLock(dir, 10*time.Second); err == nil {
		release()
	}

	target := installedExe()
	if err := os.MkdirAll(installDir(), 0o755); err != nil {
		say("Couldn't install CredCloud Helper: %v", err)
		return 1
	}
	if !isInstalled(exe) {
		data, err := os.ReadFile(exe)
		if err == nil {
			next := target + ".new"
			if err = os.WriteFile(next, data, 0o755); err == nil {
				if _, statErr := os.Stat(target); statErr == nil {
					err = replaceExecutable(target, next)
				} else {
					err = os.Rename(next, target)
				}
			}
		}
		if err != nil {
			say("Couldn't install CredCloud Helper: %v", err)
			return 1
		}
	}
	if err := register(target); err != nil {
		say("Couldn't set up CredCloud links: %v", err)
		return 1
	}
	say("Installed in %s", installDir())
	if _, err := findChrome(); err != nil {
		say("Note: %v.", err)
	}

	status := 0
	connected := false
	email, err := connectAtInstall(server, *code)
	switch {
	case err != nil:
		say("CredCloud Helper is installed, but it isn't connected: %v.", strings.TrimSuffix(err.Error(), "."))
		say("It opens CredCloud when it starts, so you can connect it there.")
		status = 3
	case email != "":
		say("Connected to CredCloud as %s.", email)
		connected = true
	}

	start := exec.Command(target, "run")
	detach(start)
	_ = start.Start()
	if status == 0 {
		if connected {
			say("Done. Go back to CredCloud: portals you fill or teach open in Chrome from now on.")
		} else {
			say("Done. CredCloud opens in your browser to connect this computer.")
		}
	}
	return status
}

// register adds the credcloud:// handler, the Start menu shortcut and the uninstall entry.
func register(exe string) error {
	quoted := `"` + exe + `"`
	for _, args := range [][]string{
		{`HKCU\Software\Classes\credcloud`, "/ve", "/d", "URL:CredCloud Helper"},
		{`HKCU\Software\Classes\credcloud`, "/v", "URL Protocol", "/d", ""},
		{`HKCU\Software\Classes\credcloud\DefaultIcon`, "/ve", "/d", quoted + ",0"},
		{`HKCU\Software\Classes\credcloud\shell\open\command`, "/ve", "/d", quoted + ` url "%1"`},
		{runKey, "/v", "CredCloud Helper", "/d", quoted + " run"},
		{uninstallKey, "/v", "DisplayName", "/d", "CredCloud Helper"},
		{uninstallKey, "/v", "Publisher", "/d", "CredCloud"},
		{uninstallKey, "/v", "DisplayVersion", "/d", version()},
		{uninstallKey, "/v", "InstallLocation", "/d", installDir()},
		{uninstallKey, "/v", "UninstallString", "/d", quoted + " uninstall"},
		{uninstallKey, "/v", "NoModify", "/t", "REG_DWORD", "/d", "1"},
		{uninstallKey, "/v", "NoRepair", "/t", "REG_DWORD", "/d", "1"},
	} {
		if err := hidden("reg", append([]string{"add"}, append(args, "/f")...)...).Run(); err != nil {
			return fmt.Errorf("reg add %s: %w", args[0], err)
		}
	}
	shortcut := filepath.Join(os.Getenv("APPDATA"), `Microsoft\Windows\Start Menu\Programs\CredCloud Helper.lnk`)
	script := fmt.Sprintf(`$s = (New-Object -ComObject WScript.Shell).CreateShortcut('%s'); $s.TargetPath = '%s'; $s.Arguments = 'run'; $s.Save()`,
		strings.ReplaceAll(shortcut, "'", "''"), strings.ReplaceAll(exe, "'", "''"))
	_ = hidden("powershell", "-NoProfile", "-NonInteractive", "-Command", script).Run()
	return nil
}

const (
	uninstallKey = `HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\CredCloudHelper`
	runKey       = `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`
)

// uninstall removes what install added. The program's own file goes a moment after it quits.
func uninstall() int {
	dir := helperDir()
	forwardToRunningQuick(dir, "quit")
	for _, key := range []string{`HKCU\Software\Classes\credcloud`, uninstallKey} {
		_ = hidden("reg", "delete", key, "/f").Run()
	}
	_ = hidden("reg", "delete", runKey, "/v", "CredCloud Helper", "/f").Run()
	_ = os.Remove(filepath.Join(os.Getenv("APPDATA"), `Microsoft\Windows\Start Menu\Programs\CredCloud Helper.lnk`))
	cleanup := exec.Command("cmd", "/c", "ping -n 3 127.0.0.1 >nul & rmdir /s /q \""+installDir()+"\"")
	cleanup.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: createNewProcessGroup | detachedProcess}
	_ = cleanup.Start()
	return 0
}

// hidden runs a command without flashing a console window.
func hidden(name string, args ...string) *exec.Cmd {
	cmd := exec.Command(name, args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000} // CREATE_NO_WINDOW
	return cmd
}

func openInDefaultBrowser(url string) error {
	return hidden("rundll32", "url.dll,FileProtocolHandler", url).Start()
}
