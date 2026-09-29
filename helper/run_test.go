package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"testing"
)

// A fake CredCloud /runner/api that behaves like the real one: a code pairs once, a claimed
// job comes back until it's finished unless the helper skips it, and a revoked token gets 401.
type fakeRunnerAPI struct {
	mu      sync.Mutex
	latest  []byte // the current build of the helper, when set
	jobs    []json.RawMessage
	status  map[int64]string // "claimed", "done", "cancelled"
	posts   map[string]json.RawMessage
	revoked bool
	pairs   int
}

func (f *fakeRunnerAPI) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()
	body, _ := io.ReadAll(r.Body)
	if r.URL.Path == "/helper/download/"+downloadName() && f.latest != nil {
		w.Write(f.latest)
		return
	}
	if f.latest != nil {
		sum := sha256.Sum256(f.latest)
		w.Header().Set("X-Helper-Sha256", hex.EncodeToString(sum[:]))
	}
	if r.URL.Path == "/runner/api/pair" {
		var p struct{ Code, Name string }
		json.Unmarshal(body, &p)
		if p.Code != "good-code" || f.pairs > 0 || !strings.Contains(p.Name, "(") {
			w.WriteHeader(http.StatusBadRequest)
			w.Write([]byte(`{"error":"That code has expired or was already used. Get a new one from CredCloud."}`))
			return
		}
		f.pairs++
		w.Write([]byte(`{"token":"tok","email":"coordinator@example.com"}`))
		return
	}
	if r.Header.Get("Authorization") != "Bearer tok" || f.revoked {
		w.WriteHeader(http.StatusUnauthorized)
		return
	}
	if r.URL.Path == "/runner/api/jobs/next" {
		skip := map[int64]bool{}
		for _, part := range strings.Split(r.URL.Query().Get("skip"), ",") {
			if id, err := strconv.ParseInt(part, 10, 64); err == nil {
				skip[id] = true
			}
		}
		for _, raw := range f.jobs {
			var job struct{ ID int64 }
			json.Unmarshal(raw, &job)
			st := f.status[job.ID]
			if skip[job.ID] || st == "done" || st == "cancelled" {
				continue
			}
			f.status[job.ID] = "claimed"
			w.Write(raw)
			return
		}
		w.WriteHeader(http.StatusNoContent)
		return
	}
	parts := strings.Split(r.URL.Path, "/") // /runner/api/jobs/{id}/{what}
	id, _ := strconv.ParseInt(parts[4], 10, 64)
	f.posts[parts[5]+"/"+parts[4]] = body
	switch parts[5] {
	case "learned":
		f.status[id] = "done"
		w.Write([]byte(`{"revision":2}`))
	case "filled":
		f.status[id] = "done"
		w.WriteHeader(http.StatusNoContent)
	case "cancel":
		f.status[id] = "cancelled"
		w.WriteHeader(http.StatusNoContent)
	}
}

func TestConnectThenTakeJobsFromCredCloud(t *testing.T) {
	if os.Getenv("CREDCLOUD_BROWSER_ARGS") == "" {
		os.Setenv("CREDCLOUD_BROWSER_ARGS", "--headless=new")
	}
	home, _ := os.MkdirTemp("", "helper-home-")
	defer os.RemoveAll(home)
	os.Setenv("CREDCLOUD_HELPER_HOME", home)
	startURL, _ := startTestPortal("127.0.0.1:0")
	api := &fakeRunnerAPI{status: map[int64]string{}, posts: map[string]json.RawMessage{}}
	server := httptest.NewServer(api)
	defer server.Close()

	// Connecting: a bad code is refused with CredCloud's own message; a good one is kept.
	if _, _, err := pair(server.URL, "old-code"); err == nil || !strings.Contains(err.Error(), "expired") {
		t.Fatalf("a bad code: %v", err)
	}
	client, email, err := pair(server.URL, "good-code")
	if err != nil || email != "coordinator@example.com" || client.Token != "tok" {
		t.Fatalf("pair: %v %q", err, email)
	}
	_ = client
	api.mu.Lock()
	api.pairs = 0 // a fresh code, for the helper itself
	api.mu.Unlock()
	h := newHelper()
	if _, err := h.connect(server.URL, "good-code"); err != nil {
		t.Fatal(err)
	}
	if cfg := loadConfig(); cfg.Servers[server.URL] == nil || cfg.Servers[server.URL].Token != "tok" || cfg.Home != server.URL {
		t.Fatalf("not saved: %+v", cfg)
	}

	// A teach-again job, as CredCloud sends it: formats as a JSON object, in CredCloud's order.
	api.jobs = append(api.jobs, json.RawMessage(`{"id":41,"kind":"learn","templateId":7,"templateName":"Enrollment",
		"payerName":"Acme Health","startUrl":"`+startURL+`","revision":1,
		"fields":[{"label":"First name","by":"label","locator":"First name *","kind":"text","source":"provider.first_name","format":"UPPER","defaultValue":"","page":"/enroll"}],
		"providerName":null,"answers":[],"sources":[{"key":"provider.first_name","label":"Provider / First name"}],
		"formats":{"AS_SAVED":"As saved","UPPER":"UPPERCASE","DIGITS":"Digits only"}}`))

	h.pollOnce()
	b := h.browser
	if b == nil || len(b.OpenJobs(server.URL)) != 1 {
		t.Fatal("the job didn't open")
	}
	defer b.call("", "Browser.close", nil, nil)
	tab := b.tabs[0]
	if got := tab.job.job.Formats; len(got) != 3 || got[1].Name != "UPPER" {
		t.Errorf("formats out of order: %+v", got)
	}
	// Asking again skips the open job, so it isn't opened twice.
	h.pollOnce()
	if n := len(b.OpenJobs(server.URL)); n != 1 {
		t.Fatalf("%d tabs for one job", n)
	}

	// Save the template as it stands: CredCloud gets the boxes.
	session := func() string { b.mu.Lock(); defer b.mu.Unlock(); return b.targets[tab.tab].session }
	var at *point
	wait(t, "the Save template button", func() bool {
		for _, c := range b.contextsOf(tab) {
			var isTop bool
			if b.evaluate(c.session, c.id, "window === top && location.pathname === '/enroll'", &isTop) == nil && isTop {
				at = nil
				return b.evaluate(c.session, c.id, `__credcloud.buttonRect("Save template")`, &at) == nil && at != nil
			}
		}
		return false
	})
	for _, kind := range []string{"mouseMoved", "mousePressed", "mouseReleased"} {
		params := map[string]any{"type": kind, "x": at.X, "y": at.Y, "button": "left", "clickCount": 1}
		if err := b.call(session(), "Input.dispatchMouseEvent", params, nil); err != nil {
			t.Fatal(err)
		}
	}
	wait(t, "the template to reach CredCloud", func() bool {
		api.mu.Lock()
		defer api.mu.Unlock()
		return api.posts["learned/41"] != nil
	})
	if !strings.Contains(string(api.posts["learned/41"]), `"locator":"First name *"`) {
		t.Errorf("learned: %s", api.posts["learned/41"])
	}
	wait(t, "the saved message", func() bool {
		tab.job.mu.Lock()
		defer tab.job.mu.Unlock()
		return strings.Contains(tab.job.message, "version 2")
	})

	// Disconnected in CredCloud: the helper forgets the server.
	api.mu.Lock()
	api.revoked = true
	api.mu.Unlock()
	h.pollOnce()
	if len(loadConfig().Servers) != 0 {
		t.Error("a revoked token was kept")
	}
}

// An installed helper swaps in CredCloud's newer build between jobs, after checking it
// against the checksum CredCloud sent, and then restarts.
func TestUpdatesItselfBetweenJobs(t *testing.T) {
	home, _ := os.MkdirTemp("", "helper-home-")
	defer os.RemoveAll(home)
	os.Setenv("CREDCLOUD_HELPER_HOME", home)
	api := &fakeRunnerAPI{status: map[int64]string{}, posts: map[string]json.RawMessage{}, latest: []byte("the new build")}
	server := httptest.NewServer(api)
	defer server.Close()

	h := newHelper()
	h.exe = filepath.Join(home, "credcloud-helper")
	os.WriteFile(h.exe, []byte("the old build"), 0o755)
	h.selfHash = fileSHA256(h.exe)
	h.updatable = true
	if _, err := h.connect(server.URL, "good-code"); err != nil {
		t.Fatal(err)
	}

	// A build that doesn't match its checksum is refused, and not tried again.
	api.mu.Lock()
	good := api.latest
	api.latest = []byte("tampered")
	api.mu.Unlock()
	client := &Client{Server: server.URL, Token: "tok"}
	h.maybeUpdate(client, hex.EncodeToString(func() []byte { s := sha256.Sum256(good); return s[:] }()))
	if data, _ := os.ReadFile(h.exe); string(data) != "the old build" || h.restart {
		t.Fatalf("installed a build that didn't match: %q", data)
	}

	api.mu.Lock()
	api.latest = good
	api.mu.Unlock()
	h.badHash = ""
	h.pollOnce()
	if data, _ := os.ReadFile(h.exe); string(data) != "the new build" {
		t.Fatalf("not updated: %q", data)
	}
	if !h.restart {
		t.Error("it should restart into the new build")
	}
	select {
	case <-h.quit:
	default:
		t.Error("it should quit so the new build can start")
	}

	// A helper run with go run . never replaces itself.
	h2 := newHelper()
	h2.exe = filepath.Join(home, "other")
	os.WriteFile(h2.exe, []byte("dev"), 0o755)
	h2.selfHash = fileSHA256(h2.exe)
	h2.updatable = false
	h2.pollOnce()
	if data, _ := os.ReadFile(h2.exe); string(data) != "dev" {
		t.Error("a development copy updated itself")
	}
}

// A second start hands its link to the helper already running, and only CredCloud's own
// sites are accepted from a link.
func TestLinksGoToTheRunningHelper(t *testing.T) {
	dir := t.TempDir()
	release, err := lockFile(filepath.Join(dir, "helper.lock"))
	if err != nil {
		t.Fatal(err)
	}
	defer release()
	if _, err := lockFile(filepath.Join(dir, "helper.lock")); err == nil {
		t.Fatal("two helpers got the lock")
	}
	got := make(chan string, 1)
	inst, err := listen(dir, func(cmd, arg string) { got <- cmd + " " + arg })
	if err != nil {
		t.Fatal(err)
	}
	defer inst.close()
	if !forwardToRunning(dir, "url", "credcloud://open?server=https://credcloud.app") {
		t.Fatal("not handed over")
	}
	if g := <-got; g != "url credcloud://open?server=https://credcloud.app" {
		t.Errorf("got %q", g)
	}

	for raw, ok := range map[string]bool{
		"https://credcloud.app": true, "https://staging.credcloud.app/": true, "http://localhost:8080": true,
		"https://credcloud.app.evil.com": false, "http://credcloud.app": false, "https://example.com": false,
	} {
		if _, err := allowedServer(raw); (err == nil) != ok {
			t.Errorf("allowedServer(%q) = %v", raw, err)
		}
	}
}
