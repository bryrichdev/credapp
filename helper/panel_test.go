package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"testing"
	"time"
)

type recorder struct {
	posts map[string]any
}

func (r *recorder) post(path string, body any, result any) error {
	r.posts[path] = body
	if strings.HasSuffix(path, "/learned") && result != nil {
		return json.Unmarshal([]byte(`{"revision":1}`), result)
	}
	return nil
}
func (r *recorder) fillFrames([]FillItem) FillReport { return FillReport{} }

func wait(t *testing.T, what string, ok func() bool) {
	t.Helper()
	for end := time.Now().Add(15 * time.Second); time.Now().Before(end); time.Sleep(100 * time.Millisecond) {
		if ok() {
			return
		}
	}
	t.Fatalf("timed out: %s", what)
}

type point struct{ X, Y float64 }

// TestTeachThenFill teaches the test portal five boxes (one inside its frame) and fills them,
// clicking the panel with real mouse events, in a headless Chrome. It needs Chrome; set
// CREDCLOUD_BROWSER to use another Chromium.
func TestTeachThenFill(t *testing.T) {
	if os.Getenv("CREDCLOUD_BROWSER_ARGS") == "" {
		os.Setenv("CREDCLOUD_BROWSER_ARGS", "--headless=new")
	}
	startURL, _ := startTestPortal("127.0.0.1:0")
	profile, _ := os.MkdirTemp("", "step4-")
	defer os.RemoveAll(profile)
	cdp, err := launchChrome(profile)
	if err != nil {
		t.Fatal(err)
	}
	b, err := NewBrowser(context.Background(), cdp)
	if err != nil {
		t.Fatal(err)
	}
	defer b.call("", "Browser.close", nil, nil)
	rec := &recorder{posts: map[string]any{}}
	job := newJobSession("local", fakeLearnJob(startURL))
	if err := b.Open(job, rec); err != nil {
		t.Fatal(err)
	}
	tab := b.tabs[0]
	session := func() string { b.mu.Lock(); defer b.mu.Unlock(); return b.targets[tab.tab].session }
	top := func() (pageContext, bool) {
		for _, c := range b.contextsOf(tab) {
			if c.session == session() {
				var isTop bool
				if b.evaluate(c.session, c.id, "window === top && location.pathname === '/enroll'", &isTop) == nil && isTop {
					return c, true
				}
			}
		}
		return pageContext{}, false
	}
	click := func(p point) {
		for _, kind := range []string{"mouseMoved", "mousePressed", "mouseReleased"} {
			params := map[string]any{"type": kind, "x": p.X, "y": p.Y}
			if kind != "mouseMoved" {
				params["button"] = "left"
				params["clickCount"] = 1
			}
			if err := b.call(session(), "Input.dispatchMouseEvent", params, nil); err != nil {
				t.Fatal(err)
			}
		}
	}
	press := func(text string) {
		t.Helper()
		var at *point
		wait(t, "button "+text, func() bool {
			c, ok := top()
			if !ok {
				return false
			}
			q, _ := json.Marshal(text)
			at = nil
			return b.evaluate(c.session, c.id, "__credcloud.buttonRect("+string(q)+")", &at) == nil && at != nil
		})
		click(*at)
	}
	waitButton := func(text string) {
		t.Helper()
		wait(t, "button "+text, func() bool {
			c, ok := top()
			var at *point
			q, _ := json.Marshal(text)
			return ok && b.evaluate(c.session, c.id, "__credcloud.buttonRect("+string(q)+")", &at) == nil && at != nil
		})
	}
	pending := func() bool { job.mu.Lock(); defer job.mu.Unlock(); return job.pending != nil }
	pick := func(js string) {
		t.Helper()
		var at point
		if err := b.evaluate(session(), 0, fmt.Sprintf(`(() => { const r = %s.getBoundingClientRect(); return {X: r.left + r.width/2, Y: r.top + r.height/2}; })()`, js), &at); err != nil {
			t.Fatal(err)
		}
		click(at)
		wait(t, "pending "+js, pending)
	}
	add := func(values map[string]string) {
		t.Helper()
		wait(t, "the chooser", func() bool {
			c, ok := top()
			var at *point
			return ok && b.evaluate(c.session, c.id, `__credcloud.buttonRect("Add")`, &at) == nil && at != nil
		})
		c, _ := top()
		data, _ := json.Marshal(values)
		b.evaluate(c.session, c.id, "__credcloud.choose("+string(data)+")", nil)
		press("Add")
		wait(t, "added: "+fmt.Sprint(values), func() bool { return !pending() })
	}

	press("Pick a box")
	waitButton("Stop picking")
	pick(`document.querySelector('label[for=first]')`)
	add(map[string]string{"Data": "provider.first_name", "Format": "UPPER"})
	pick(`document.querySelector('#npi')`)
	add(map[string]string{"Name": "NPI", "Data": "provider.npi"})
	pick(`document.querySelector('#state')`)
	add(map[string]string{"Data": "provider.state"})
	pick(`document.querySelector('input[value=F]')`)
	add(map[string]string{"Data": "provider.sex"})
	pick(`(() => { const f = document.querySelector('iframe'); return {getBoundingClientRect: () => { const r = f.getBoundingClientRect(); return {left: r.left + 60, top: r.top + 10, width: 80, height: 20}; }}; })()`)
	add(map[string]string{"Data": "group.tax_id"})
	press("Stop picking")
	press("Save template")
	wait(t, "save", func() bool { return rec.posts["/runner/api/jobs/1/learned"] != nil })
	fields := rec.posts["/runner/api/jobs/1/learned"].(map[string]any)["fields"].([]Field)
	if len(fields) != 5 || fields[0].By != "label" || fields[1].Locator != "#npi" || fields[4].Locator != "Tax ID" {
		t.Fatalf("learned: %+v", fields)
	}
	data, _ := json.Marshal(fields)
	os.WriteFile("learned.json", data, 0o644)
	defer os.Remove("learned.json")

	// Fill.
	job = newJobSession("local", fakeFillJob(startURL))
	if err := b.Open(job, rec); err != nil {
		t.Fatal(err)
	}
	tab = b.tabs[len(b.tabs)-1]
	press("Fill this page")
	wait(t, "fill", func() bool { job.mu.Lock(); defer job.mu.Unlock(); return len(job.filled) == 5 })
	var got map[string]any
	b.evaluate(session(), 0, `({first: document.querySelector('#first').value, npi: npi.value, state: state.value, f: document.querySelector('input[value=F]').checked, tax: document.querySelector('iframe').contentDocument.querySelector('#tax').value})`, &got)
	want := map[string]any{"first": "Jane", "npi": "1234567890", "state": "UT", "f": true, "tax": "12-3456789"}
	for k, v := range want {
		if got[k] != v {
			t.Errorf("%s = %v want %v", k, got[k], v)
		}
	}
	press("Done")
	wait(t, "done", func() bool { return rec.posts["/runner/api/jobs/2/filled"] != nil })
	t.Logf("filled: %v; message: %s", rec.posts["/runner/api/jobs/2/filled"], job.message)
}
