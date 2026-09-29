package main

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strconv"
	"strings"
	"sync"
	"testing"
)

// A fake CredCloud /runner/api that behaves like the real one: a code pairs once, a claimed
// job comes back until it's finished unless the helper skips it, and a revoked token gets 401.
type fakeRunnerAPI struct {
	mu      sync.Mutex
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
	cfg := loadConfig()
	cfg.Servers[server.URL] = &serverConfig{Token: client.Token, Email: email}
	if err := saveConfig(cfg); err != nil {
		t.Fatal(err)
	}
	cfg = loadConfig()
	if cfg.Servers[server.URL].Token != "tok" {
		t.Fatal("the token wasn't saved")
	}

	// A teach-again job, as CredCloud sends it: formats as a JSON object, in CredCloud's order.
	api.jobs = append(api.jobs, json.RawMessage(`{"id":41,"kind":"learn","templateId":7,"templateName":"Enrollment",
		"payerName":"Acme Health","startUrl":"`+startURL+`","revision":1,
		"fields":[{"label":"First name","by":"label","locator":"First name *","kind":"text","source":"provider.first_name","format":"UPPER","defaultValue":"","page":"/enroll"}],
		"providerName":null,"answers":[],"sources":[{"key":"provider.first_name","label":"Provider / First name"}],
		"formats":{"AS_SAVED":"As saved","UPPER":"UPPERCASE","DIGITS":"Digits only"}}`))

	b := poll(&cfg, nil)
	if b == nil || len(b.OpenJobs(server.URL)) != 1 {
		t.Fatal("the job didn't open")
	}
	defer b.call("", "Browser.close", nil, nil)
	tab := b.tabs[0]
	if got := tab.job.job.Formats; len(got) != 3 || got[1].Name != "UPPER" {
		t.Errorf("formats out of order: %+v", got)
	}
	// Asking again skips the open job, so it isn't opened twice.
	b = poll(&cfg, b)
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
	poll(&cfg, b)
	if len(loadConfig().Servers) != 0 {
		t.Error("a revoked token was kept")
	}
}
