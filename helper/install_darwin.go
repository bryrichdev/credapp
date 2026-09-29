//go:build darwin

package main

import (
	_ "embed"
	"encoding/xml"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

// On a Mac the helper lives in ~/Applications/CredCloud Helper.app. The app is a small
// AppleScript applet, made on her Mac with the tools every Mac has (osacompile, codesign):
// opening it starts the helper, and it answers credcloud:// links by passing them on. The
// helper itself sits inside it, in Contents/Resources. A LaunchAgent starts it when she logs
// in. Nothing needs an administrator.
//
// The install command downloads with curl, which doesn't mark files as downloaded from the
// internet, so Gatekeeper has nothing to ask about.

//go:embed assets/icon-128.png
var iconPNG []byte

const appName = "CredCloud Helper.app"

const appletSource = `on run
	do shell script quoted form of (POSIX path of (path to me)) & "Contents/Resources/credcloud-helper run > /dev/null 2>&1 &"
end run

on open location theURL
	do shell script quoted form of (POSIX path of (path to me)) & "Contents/Resources/credcloud-helper url " & quoted form of theURL & " > /dev/null 2>&1 &"
end open location
`

func appPath() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, "Applications", appName)
}

func isInstalled(exe string) bool {
	return strings.HasSuffix(exe, filepath.Join(appName, "Contents", "Resources", "credcloud-helper"))
}

func needsInstall() bool { return false }

func installCommand(args []string) int {
	fs := flag.NewFlagSet("install", flag.ContinueOnError)
	serverFlag := fs.String("server", defaultServer, "CredCloud's address")
	code := fs.String("code", "", "a one-time code from CredCloud that connects this Mac")
	fs.String("report", "", "unused on a Mac")
	if err := fs.Parse(args); err != nil {
		return 2
	}
	say := func(format string, a ...any) { fmt.Printf(format+"\n", a...) }
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
	say("Installing CredCloud Helper…")

	// Stop a helper that's running, so its files can be replaced.
	forwardToRunningQuick(dir, "quit")
	if release, err := waitForLock(dir, 10*time.Second); err == nil {
		release()
	}

	app := appPath()
	if err := buildApp(app, exe); err != nil {
		say("Couldn't install CredCloud Helper: %v", err)
		return 1
	}
	say("Installed in %s", app)

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

	if err := startAtLogin(filepath.Join(app, "Contents", "Resources", "credcloud-helper")); err != nil {
		say("Couldn't set it to start when you log in (%v). Open CredCloud Helper from your Applications folder after a restart.", err)
		if err := exec.Command("/usr/bin/open", "-a", app).Run(); err != nil {
			say("Open CredCloud Helper from your Applications folder to start it.")
		}
	}
	if connected {
		say("Done. Go back to CredCloud: portals you fill or teach open in Chrome from now on.")
	} else if status == 0 {
		say("Done.")
	}
	return status
}

// buildApp makes the applet, puts the helper inside it, and signs it for this Mac.
func buildApp(app, exe string) error {
	if err := os.MkdirAll(filepath.Dir(app), 0o755); err != nil {
		return err
	}
	tmp, err := os.MkdirTemp("", "credcloud-helper-")
	if err != nil {
		return err
	}
	defer os.RemoveAll(tmp)
	script := filepath.Join(tmp, "helper.applescript")
	if err := os.WriteFile(script, []byte(appletSource), 0o600); err != nil {
		return err
	}
	// Build it apart, then swap it in, so a failure leaves the old one working. osacompile makes
	// an app only when the name ends in .app.
	next := filepath.Join(tmp, appName)
	if out, err := exec.Command("/usr/bin/osacompile", "-o", next, script).CombinedOutput(); err != nil {
		return fmt.Errorf("osacompile: %v: %s", err, out)
	}
	resources := filepath.Join(next, "Contents", "Resources")
	data, err := os.ReadFile(exe)
	if err != nil {
		return err
	}
	if err := os.WriteFile(filepath.Join(resources, "credcloud-helper"), data, 0o755); err != nil {
		return err
	}
	icon := filepath.Join(tmp, "icon.png")
	if os.WriteFile(icon, iconPNG, 0o600) == nil {
		_ = exec.Command("/usr/bin/sips", "-s", "format", "icns", icon, "--out", filepath.Join(resources, "applet.icns")).Run()
	}
	plist := filepath.Join(next, "Contents", "Info.plist")
	for _, edit := range []string{
		"Set :CFBundleIdentifier app.credcloud.helper",
		`Set :CFBundleName "CredCloud Helper"`,
		"Add :LSUIElement bool true",
		"Add :CFBundleURLTypes array",
		"Add :CFBundleURLTypes:0 dict",
		"Add :CFBundleURLTypes:0:CFBundleURLName string app.credcloud.helper",
		"Add :CFBundleURLTypes:0:CFBundleURLSchemes array",
		"Add :CFBundleURLTypes:0:CFBundleURLSchemes:0 string credcloud",
	} {
		if out, err := exec.Command("/usr/libexec/PlistBuddy", "-c", edit, plist).CombinedOutput(); err != nil {
			// Set fails when osacompile didn't write the key; add it instead.
			if strings.HasPrefix(edit, "Set ") {
				parts := strings.SplitN(strings.TrimPrefix(edit, "Set "), " ", 2)
				if exec.Command("/usr/libexec/PlistBuddy", "-c", "Add "+parts[0]+" string "+parts[1], plist).Run() == nil {
					continue
				}
			}
			return fmt.Errorf("PlistBuddy %q: %v: %s", edit, err, out)
		}
	}
	if err := sign(next); err != nil {
		return err
	}
	old := app + ".old"
	os.RemoveAll(old)
	if _, err := os.Stat(app); err == nil {
		if err := os.Rename(app, old); err != nil {
			return err
		}
	}
	if err := os.Rename(next, app); err != nil {
		// A temporary folder on another volume: copy instead.
		if out, err := exec.Command("/usr/bin/ditto", next, app).CombinedOutput(); err != nil {
			os.RemoveAll(app)
			_ = os.Rename(old, app)
			return fmt.Errorf("ditto: %v: %s", err, out)
		}
	}
	os.RemoveAll(old)
	_ = exec.Command("/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister", "-f", app).Run()
	return nil
}

// sign gives the app an ad-hoc signature, which Apple silicon Macs need to run it.
func sign(app string) error {
	if out, err := exec.Command("/usr/bin/codesign", "--force", "--deep", "--sign", "-", app).CombinedOutput(); err != nil {
		return fmt.Errorf("codesign: %v: %s", err, out)
	}
	return nil
}

// afterUpdate signs the app again, since its contents changed.
func afterUpdate(exe string) {
	if isInstalled(exe) {
		_ = sign(filepath.Dir(filepath.Dir(filepath.Dir(exe))))
	}
}

func openInDefaultBrowser(url string) error {
	return exec.Command("/usr/bin/open", url).Run()
}

// uninstall removes the app and its login item. Settings and the portal browser profile stay
// in ~/Library/Application Support/CredCloud Helper until she deletes them.
func uninstall() int {
	_ = exec.Command("/bin/launchctl", "bootout", launchDomain()+"/"+agentLabel).Run()
	os.Remove(agentPath())
	forwardToRunningQuick(helperDir(), "quit")
	app := appPath()
	_ = exec.Command("/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister", "-u", app).Run()
	if err := os.RemoveAll(app); err != nil {
		fmt.Println(err)
		return 1
	}
	fmt.Println("CredCloud Helper is uninstalled.")
	return 0
}

const agentLabel = "app.credcloud.helper"

func agentPath() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, "Library", "LaunchAgents", agentLabel+".plist")
}

func launchDomain() string { return fmt.Sprintf("gui/%d", os.Getuid()) }

// startAtLogin adds a LaunchAgent that starts the helper when she logs in, and starts it now.
// macOS lists it under Login Items in System Settings, where she can see or turn it off.
func startAtLogin(exe string) error {
	var escaped strings.Builder
	if err := xml.EscapeText(&escaped, []byte(exe)); err != nil {
		return err
	}
	plist := `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>Label</key><string>` + agentLabel + `</string>
	<key>ProgramArguments</key><array><string>` + escaped.String() + `</string><string>run</string></array>
	<key>RunAtLoad</key><true/>
	<key>ProcessType</key><string>Interactive</string>
</dict>
</plist>
`
	if err := os.MkdirAll(filepath.Dir(agentPath()), 0o755); err != nil {
		return err
	}
	if err := os.WriteFile(agentPath(), []byte(plist), 0o644); err != nil {
		return err
	}
	_ = exec.Command("/bin/launchctl", "bootout", launchDomain()+"/"+agentLabel).Run()
	if out, err := exec.Command("/bin/launchctl", "bootstrap", launchDomain(), agentPath()).CombinedOutput(); err != nil {
		return fmt.Errorf("launchctl: %v: %s", err, strings.TrimSpace(string(out)))
	}
	return nil
}
