package main

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"runtime"
	"strings"
	"sync"
	"time"
)

// The helper while it runs: it asks each connected CredCloud for work every couple of seconds,
// opens each job in its Chrome, answers credcloud:// links from CredCloud's pages, and keeps
// itself up to date. Once installed it starts when she logs in and keeps running quietly.

const defaultServer = "https://credcloud.app"

type Helper struct {
	mu        sync.Mutex
	cfg       config
	browser   *Browser
	pollEvery time.Duration
	asked     map[string]bool // servers whose connect page was opened this run

	exe       string // this program's file
	selfHash  string // its checksum, to compare with CredCloud's build
	updatable bool   // only an installed helper replaces itself
	updating  bool
	badHash   string // a build that failed to download or install; don't retry it
	restart   bool   // start the new build once this one has quit

	wake     chan struct{}
	quit     chan struct{}
	quitOnce sync.Once
}

func newHelper() *Helper {
	h := &Helper{cfg: loadConfig(), pollEvery: 2 * time.Second,
		asked: map[string]bool{}, wake: make(chan struct{}, 1), quit: make(chan struct{})}
	if exe, err := executable(); err == nil {
		h.exe = exe
		h.selfHash = fileSHA256(exe)
		h.updatable = isInstalled(exe)
	}
	return h
}

// poke asks for work now instead of at the next tick.
func (h *Helper) poke() {
	select {
	case h.wake <- struct{}{}:
	default:
	}
}

func (h *Helper) stop() { h.quitOnce.Do(func() { close(h.quit) }) }

// run asks for work until it's told to stop.
func (h *Helper) run() {
	h.mu.Lock()
	servers := len(h.cfg.Servers)
	home := h.cfg.Home
	h.mu.Unlock()
	if servers == 0 {
		if home == "" {
			home = defaultServer
		}
		h.askToConnect(home)
	}
	ticker := time.NewTicker(h.pollEvery)
	defer ticker.Stop()
	for {
		h.pollOnce()
		select {
		case <-h.quit:
			return
		case <-ticker.C:
		case <-h.wake:
		}
	}
}

// pollOnce asks each connected server once for a job and opens what comes back.
func (h *Helper) pollOnce() {
	h.mu.Lock()
	servers := map[string]string{}
	for server, sc := range h.cfg.Servers {
		servers[server] = sc.Token
	}
	b := h.browser
	h.mu.Unlock()

	for server, token := range servers {
		client := &Client{Server: server, Token: token}
		var open []int64
		if b != nil && !b.isClosed() {
			open = b.OpenJobs(server)
		}
		job, latest, err := client.Next(open)
		if errors.Is(err, errSignedOut) {
			log.Printf("%s: %v", server, err)
			h.forget(server)
			h.askToConnect(server)
			continue
		}
		if err != nil {
			log.Printf("%s: %v", server, err)
			continue
		}
		if job != nil {
			h.open(client, job)
		} else if latest != "" && len(open) == 0 && h.isHome(server) {
			h.maybeUpdate(client, latest)
		}
	}
}

// open shows a job in Chrome, starting Chrome if it isn't running. If it can't, CredCloud
// hears why, and shows it on the page she started the job from.
func (h *Helper) open(client *Client, job *Job) {
	h.mu.Lock()
	b := h.browser
	h.mu.Unlock()
	var err error
	if b == nil || b.isClosed() {
		if b, err = openBrowser(); err != nil {
			log.Printf("couldn't start Chrome: %v", err)
			client.cancel(job.ID, sentence(err.Error()))
			return
		}
		h.mu.Lock()
		h.browser = b
		h.mu.Unlock()
	}
	log.Printf("%s: opening %s job %d (%s / %s)", client.Server, job.Kind, job.ID, job.PayerName, job.TemplateName)
	if err := b.Open(newJobSession(client.Server, *job), client); err != nil {
		log.Printf("couldn't open job %d: %v", job.ID, err)
		client.cancel(job.ID, "CredCloud Helper couldn't open the portal. Try again.")
	}
}

// isHome says whether updates come from this server: the CredCloud it was installed from, so
// a helper connected to staging and production doesn't swap between their builds.
func (h *Helper) isHome(server string) bool {
	h.mu.Lock()
	defer h.mu.Unlock()
	return h.cfg.Home == "" || h.cfg.Home == server
}

func (h *Helper) forget(server string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	delete(h.cfg.Servers, server)
	if err := saveConfig(h.cfg); err != nil {
		log.Print(err)
	}
}

// connect swaps a one-time code for a token and keeps it.
func (h *Helper) connect(server, code string) (string, error) {
	client, email, err := pair(server, code)
	if err != nil {
		return "", err
	}
	h.mu.Lock()
	h.cfg.Servers[server] = &serverConfig{Token: client.Token, Email: email}
	if h.cfg.Home == "" {
		h.cfg.Home = server
	}
	err = saveConfig(h.cfg)
	h.mu.Unlock()
	if err != nil {
		return "", err
	}
	log.Printf("connected to %s as %s", server, email)
	h.poke()
	return email, nil
}

// askToConnect opens CredCloud's Connect CredCloud Helper page in her usual browser, once per
// server per run. The page hands the helper a code through a credcloud:// link.
func (h *Helper) askToConnect(server string) {
	h.mu.Lock()
	asked := h.asked[server]
	h.asked[server] = true
	h.mu.Unlock()
	switch {
	case asked:
	case h.updatable: // only an installed helper can take the page's link back
		log.Printf("not connected to %s; opening its connect page", server)
		if err := openInDefaultBrowser(server + "/helper/connect"); err != nil {
			log.Print(err)
		}
	default:
		log.Printf("not connected to %s. Get an install command on its CredCloud Helper page and run: "+
			"go run . pair %s <the code in it>", server, server)
	}
}

// --- credcloud:// links ---

// allowedServer checks a CredCloud address from a link or an install command. Only
// CredCloud's own sites, and this computer for development, are accepted: a link on some
// other website can't point the helper at a server of its own.
func allowedServer(raw string) (string, error) {
	server, err := serverOrigin(raw)
	if err != nil {
		return "", err
	}
	allowed := map[string]bool{defaultServer: true, "https://staging.credcloud.app": true}
	for _, extra := range strings.Split(os.Getenv("CREDCLOUD_HELPER_SERVERS"), ",") {
		if extra = strings.TrimSpace(extra); extra != "" {
			if origin, err := serverOrigin(extra); err == nil {
				allowed[origin] = true
			}
		}
	}
	u, _ := url.Parse(server)
	local := u.Scheme == "http" && (u.Hostname() == "localhost" || u.Hostname() == "127.0.0.1")
	if !allowed[server] && !local {
		return "", errors.New(server + " isn't a CredCloud site")
	}
	return server, nil
}

// handleLink does what a link on a CredCloud page asks:
//
//	credcloud://open?server=https://credcloud.app              start, and look for work now
//	credcloud://connect?server=https://credcloud.app&code=…    connect this computer
func (h *Helper) handleLink(raw string) {
	u, err := url.Parse(raw)
	if err != nil || u.Scheme != "credcloud" {
		log.Printf("ignoring link %q", raw)
		return
	}
	action := u.Host
	if action == "" {
		action = strings.Trim(u.Opaque+u.Path, "/")
	}
	server, err := allowedServer(u.Query().Get("server"))
	if err != nil {
		log.Printf("ignoring link: %v", err)
		return
	}
	h.mu.Lock()
	_, connected := h.cfg.Servers[server]
	h.mu.Unlock()
	switch action {
	case "connect":
		if _, err := h.connect(server, u.Query().Get("code")); err != nil {
			log.Printf("connecting to %s: %v", server, err)
		}
	case "open":
		if !connected {
			h.mu.Lock()
			h.asked[server] = false
			h.mu.Unlock()
			h.askToConnect(server)
		}
	}
	h.poke()
}

// --- Keeping itself up to date ---

func platform() string { return runtime.GOOS + "-" + runtime.GOARCH }

// version is the start of this program's checksum: it changes exactly when the program does.
func version() string {
	if exe, err := executable(); err == nil {
		if sum := fileSHA256(exe); len(sum) >= 7 {
			return sum[:7]
		}
	}
	return "dev"
}

func downloadName() string {
	name := "credcloud-helper-" + platform()
	if runtime.GOOS == "windows" {
		name += ".exe"
	}
	return name
}

func fileSHA256(path string) string {
	f, err := os.Open(path)
	if err != nil {
		return ""
	}
	defer f.Close()
	sum := sha256.New()
	if _, err := io.Copy(sum, f); err != nil {
		return ""
	}
	return hex.EncodeToString(sum.Sum(nil))
}

// maybeUpdate replaces the helper with CredCloud's current build when they differ, between
// jobs, then quits so the new build starts. It checks the download against the checksum
// CredCloud sent before using it. A helper run with `go run .` never updates itself.
func (h *Helper) maybeUpdate(client *Client, latest string) {
	h.mu.Lock()
	busy := h.updating || (h.browser != nil && h.browser.Busy())
	skip := !h.updatable || h.selfHash == "" || strings.EqualFold(latest, h.selfHash) || latest == h.badHash || busy
	if !skip {
		h.updating = true
	}
	h.mu.Unlock()
	if skip {
		return
	}
	defer func() {
		h.mu.Lock()
		h.updating = false
		h.mu.Unlock()
	}()
	if err := h.update(client, latest); err != nil {
		log.Printf("couldn't update: %v", err)
		h.mu.Lock()
		h.badHash = latest
		h.mu.Unlock()
		return
	}
	log.Printf("updated to %s from %s; restarting", latest[:7], client.Server)
	h.mu.Lock()
	h.restart = true
	h.mu.Unlock()
	h.stop()
}

func (h *Helper) update(client *Client, latest string) error {
	exe := h.exe
	resp, err := client.do(http.MethodGet, "/helper/download/"+downloadName(), nil)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return errors.New("download: " + resp.Status)
	}
	next := exe + ".new"
	f, err := os.OpenFile(next, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o755)
	if err != nil {
		return err
	}
	sum := sha256.New()
	_, err = io.Copy(io.MultiWriter(f, sum), io.LimitReader(resp.Body, 100<<20))
	f.Close()
	if err == nil && !strings.EqualFold(hex.EncodeToString(sum.Sum(nil)), latest) {
		err = errors.New("the download doesn't match CredCloud's checksum")
	}
	if err == nil {
		err = replaceExecutable(exe, next)
	}
	if err != nil {
		os.Remove(next)
		return err
	}
	afterUpdate(exe)
	return nil
}

func sentence(s string) string {
	s = strings.TrimSpace(s)
	if s != "" && !strings.HasSuffix(s, ".") {
		s += "."
	}
	return s
}
