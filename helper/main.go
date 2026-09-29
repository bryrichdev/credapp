// CredCloud Helper fills payer portals with provider data from CredCloud, in the coordinator's
// own Chrome, on her own computer. She installs it once with a command CredCloud gives her;
// after that it starts when she logs in, opens each portal she fills or teaches in CredCloud,
// and keeps itself up to date. It never submits a form.
//
//	credcloud-helper                         run (what the app, the Start menu and links do)
//	credcloud-helper url <credcloud://…>     what a credcloud:// link does
//	credcloud-helper install --server <address> [--code <code>] [--report <file>]
//	credcloud-helper uninstall
//
// For development, from helper/:
//
//	go run . pair <server> <code>     connect, with a code from CredCloud
//	go run .                          run (never updates itself)
//	go run . portal                   the test portal, at http://127.0.0.1:8181/enroll
//	go run . demo [fill]              a fake job against the test portal, with no CredCloud
package main

import (
	"fmt"
	"io"
	"log"
	"os"
	"os/exec"
	"path/filepath"
)

func main() {
	args := os.Args[1:]
	command := "run"
	if len(args) > 0 {
		command, args = args[0], args[1:]
	}
	switch command {
	case "run":
		// A copy downloaded straight from CredCloud installs itself the first time it runs.
		if needsInstall() {
			os.Exit(installCommand([]string{"--server", defaultServer}))
		}
		os.Exit(runHelper("run", ""))
	case "url":
		link := ""
		if len(args) > 0 {
			link = args[0]
		}
		os.Exit(runHelper("url", link))
	case "install":
		os.Exit(installCommand(args))
	case "uninstall":
		os.Exit(uninstall())
	case "version":
		fmt.Println("CredCloud Helper", version(), platform())
	case "pair":
		if len(args) != 2 {
			log.Fatal("usage: go run . pair <server> <code>")
		}
		pairCommand(args[0], args[1])
	case "portal":
		startURL, err := startTestPortal("127.0.0.1:8181")
		must(err)
		fmt.Println("test portal:", startURL, "(Ctrl+C to stop)")
		select {}
	case "demo":
		demo(len(args) > 0 && args[0] == "fill")
	default:
		// Windows can pass a stray argument; treat it as a plain start.
		os.Exit(runHelper("run", ""))
	}
}

func pairCommand(rawServer, code string) {
	server, err := serverOrigin(rawServer)
	must(err)
	client, email, err := pair(server, code)
	if err != nil {
		log.Fatal(err)
	}
	cfg := loadConfig()
	cfg.Servers[server] = &serverConfig{Token: client.Token, Email: email}
	if cfg.Home == "" {
		cfg.Home = server
	}
	must(saveConfig(cfg))
	fmt.Printf("Connected to %s as %s. Now run: go run .\n", server, email)
}

// runHelper runs the helper, or hands the request to the one already running: one helper at
// a time, however it was started.
func runHelper(command, link string) int {
	dir := helperDir()
	if err := os.MkdirAll(dir, 0o700); err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	setUpLog(dir)
	release, err := lockFile(filepath.Join(dir, "helper.lock"))
	if err != nil {
		if forwardToRunning(dir, command, link) {
			return 0
		}
		log.Printf("another helper is running but didn't answer: %v", err)
		return 1
	}
	h := newHelper()
	inst, err := listen(dir, func(cmd, arg string) {
		switch cmd {
		case "quit":
			h.stop()
		case "url":
			go h.handleLink(arg)
		default:
			h.poke()
		}
	})
	if err != nil {
		log.Printf("can't listen for other starts: %v", err)
	}
	if command == "url" {
		go h.handleLink(link)
	}
	log.Printf("CredCloud Helper %s running (%s)", version(), platform())
	h.run()

	if inst != nil {
		inst.close()
	}
	release()
	if h.restart {
		if exe, err := executable(); err == nil {
			next := exec.Command(exe, "run")
			detach(next)
			if err := next.Start(); err != nil {
				log.Printf("couldn't start the new version: %v", err)
			}
		}
	}
	return 0
}

// setUpLog writes to helper.log in the helper's folder, and to the terminal when there is one.
func setUpLog(dir string) {
	path := filepath.Join(dir, "helper.log")
	if info, err := os.Stat(path); err == nil && info.Size() > 1<<20 {
		os.Rename(path, path+".1")
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600)
	if err != nil {
		return
	}
	if stderrIsUseful() {
		log.SetOutput(io.MultiWriter(f, os.Stderr))
	} else {
		log.SetOutput(f)
	}
}

func executable() (string, error) {
	exe, err := os.Executable()
	if err != nil {
		return "", err
	}
	if resolved, err := filepath.EvalSymlinks(exe); err == nil {
		return resolved, nil
	}
	return exe, nil
}
