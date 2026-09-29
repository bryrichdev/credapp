package main

import (
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"sync"
	"time"
)

// The helper's Chrome, and which of its tabs belong to which job. Every page and frame gets
// page/credcloud.js in the "credcloud" isolated world. The script asks what to draw when it
// starts ("hello"); the answer is nothing unless its tab belongs to a job.

//go:embed page/credcloud.js
var pageScript string

const (
	worldName   = "credcloud"
	bindingName = "credcloudSend"
)

// target is a tab ("page") or a frame from another process ("iframe") we're attached to.
type target struct {
	id       string
	session  string
	kind     string
	url      string
	opener   string            // for a page: the page that opened it (a portal's pop-up)
	parent   string            // for an iframe: the session of the page it's in
	worlds   map[int]string    // every credcloud world in it: context id -> frame id
	contexts map[int]string    // the worlds whose script has called in: context id -> frame id
	origins  map[string]string // its frames' current origins: frame id -> origin
}

// jobTab is a job open in a tab.
type jobTab struct {
	job *jobSession
	env jobEnv
	tab string // target id of the tab
}

type Browser struct {
	ctx      context.Context
	cdp      *CDP
	mu       sync.Mutex
	targets  map[string]*target // by target id
	sessions map[string]*target // by session id
	tabs     []*jobTab
	ready    map[string]chan string // target id -> its session, once prepared
	Closed   chan struct{}
}

func NewBrowser(ctx context.Context, cdp *CDP) (*Browser, error) {
	b := &Browser{ctx: ctx, cdp: cdp, targets: map[string]*target{}, sessions: map[string]*target{},
		ready: map[string]chan string{}, Closed: make(chan struct{})}
	go b.events()
	// Hear when tabs change address or close.
	if err := b.call("", "Target.setDiscoverTargets", map[string]any{"discover": true}, nil); err != nil {
		return nil, err
	}
	// Attach to every new tab, paused before its page runs, so the script is in first.
	if err := b.call("", "Target.setAutoAttach", map[string]any{
		"autoAttach": true, "waitForDebuggerOnStart": true, "flatten": true,
	}, nil); err != nil {
		return nil, err
	}
	return b, nil
}

func (b *Browser) call(session, method string, params any, result any) error {
	ctx, cancel := context.WithTimeout(b.ctx, 30*time.Second)
	defer cancel()
	return b.cdp.Call(ctx, session, method, params, result)
}

func (b *Browser) readyChan(targetID string) chan string {
	b.mu.Lock()
	defer b.mu.Unlock()
	ch, ok := b.ready[targetID]
	if !ok {
		ch = make(chan string, 1)
		b.ready[targetID] = ch
	}
	return ch
}

type targetInfo struct {
	TargetID string `json:"targetId"`
	Type     string `json:"type"`
	URL      string `json:"url"`
	OpenerID string `json:"openerId"`
}

// events handles what Chrome tells us, in order, until Chrome closes.
func (b *Browser) events() {
	for e := range b.cdp.Events {
		switch e.Method {
		case "Target.attachedToTarget":
			var p struct {
				SessionID  string     `json:"sessionId"`
				TargetInfo targetInfo `json:"targetInfo"`
			}
			json.Unmarshal(e.Params, &p)
			if p.TargetInfo.Type != "page" && p.TargetInfo.Type != "iframe" {
				// Not ours to touch (workers, DevTools, Chrome's UI): let it run and let go.
				go func() {
					b.call(p.SessionID, "Runtime.runIfWaitingForDebugger", nil, nil)
					b.call("", "Target.detachFromTarget", map[string]any{"sessionId": p.SessionID}, nil)
				}()
				continue
			}
			t := &target{id: p.TargetInfo.TargetID, session: p.SessionID, kind: p.TargetInfo.Type,
				url: p.TargetInfo.URL, opener: p.TargetInfo.OpenerID, worlds: map[int]string{}, contexts: map[int]string{}, origins: map[string]string{}}
			if t.kind == "iframe" {
				t.parent = e.Session // the frame's attach event comes through its page's session
			}
			b.mu.Lock()
			b.targets[t.id] = t
			b.sessions[t.session] = t
			b.mu.Unlock()
			go b.prepare(t)

		case "Target.detachedFromTarget":
			var p struct {
				SessionID string `json:"sessionId"`
			}
			json.Unmarshal(e.Params, &p)
			b.mu.Lock()
			if t := b.sessions[p.SessionID]; t != nil {
				delete(b.sessions, p.SessionID)
				delete(b.targets, t.id)
			}
			b.mu.Unlock()

		case "Target.targetInfoChanged":
			var p struct {
				TargetInfo targetInfo `json:"targetInfo"`
			}
			json.Unmarshal(e.Params, &p)
			b.mu.Lock()
			if t := b.targets[p.TargetInfo.TargetID]; t != nil {
				t.url = p.TargetInfo.URL
			}
			b.mu.Unlock()

		case "Target.targetDestroyed":
			var p struct {
				TargetID string `json:"targetId"`
			}
			json.Unmarshal(e.Params, &p)
			b.tabClosed(p.TargetID)

		case "Page.frameNavigated":
			// A frame's origin changes when it navigates; a world keeps the frame it's in.
			var p struct {
				Frame struct {
					ID     string `json:"id"`
					Origin string `json:"securityOrigin"`
				} `json:"frame"`
			}
			json.Unmarshal(e.Params, &p)
			b.mu.Lock()
			if t := b.sessions[e.Session]; t != nil {
				t.origins[p.Frame.ID] = p.Frame.Origin
			}
			b.mu.Unlock()

		case "Page.domContentEventFired":
			// A tab's page finished loading. Chrome can reuse a new tab's blank window for the
			// portal, and then the script there doesn't start over and ask what to draw; so
			// draw the panel now.
			b.mu.Lock()
			var tab *jobTab
			if t := b.sessions[e.Session]; t != nil && t.kind == "page" {
				tab = b.jobForLocked(t)
			}
			b.mu.Unlock()
			if tab != nil {
				go b.renderJob(tab)
			}

		case "Runtime.executionContextCreated":
			var p struct {
				Context struct {
					ID      int    `json:"id"`
					Name    string `json:"name"`
					AuxData struct {
						FrameID string `json:"frameId"`
					} `json:"auxData"`
				} `json:"context"`
			}
			json.Unmarshal(e.Params, &p)
			if p.Context.Name == worldName {
				b.mu.Lock()
				if t := b.sessions[e.Session]; t != nil {
					t.worlds[p.Context.ID] = p.Context.AuxData.FrameID
				}
				b.mu.Unlock()
			}

		case "Runtime.executionContextDestroyed":
			var p struct {
				ID int `json:"executionContextId"`
			}
			json.Unmarshal(e.Params, &p)
			b.mu.Lock()
			if t := b.sessions[e.Session]; t != nil {
				delete(t.worlds, p.ID)
				delete(t.contexts, p.ID)
			}
			b.mu.Unlock()

		case "Runtime.executionContextsCleared":
			b.mu.Lock()
			if t := b.sessions[e.Session]; t != nil {
				t.worlds = map[int]string{}
				t.contexts = map[int]string{}
			}
			b.mu.Unlock()

		case "Runtime.bindingCalled":
			var p struct {
				Name      string `json:"name"`
				Payload   string `json:"payload"`
				ContextID int    `json:"executionContextId"`
			}
			json.Unmarshal(e.Params, &p)
			if p.Name == bindingName {
				go b.fromPage(e.Session, p.ContextID, p.Payload)
			}
		}
	}
	// Chrome closed: every open job ends as if its tab closed.
	b.mu.Lock()
	tabs := b.tabs
	b.tabs = nil
	b.mu.Unlock()
	for _, tab := range tabs {
		tab.job.closed(tab.env)
	}
	close(b.Closed)
}

// prepare puts the binding and the script into a new tab or frame, then lets it run.
func (b *Browser) prepare(t *target) {
	steps := []struct {
		method string
		params any
	}{
		{"Runtime.addBinding", map[string]any{"name": bindingName, "executionContextName": worldName}},
		{"Page.enable", nil},
		{"Page.addScriptToEvaluateOnNewDocument", map[string]any{"source": pageScript, "worldName": worldName}},
		{"Runtime.enable", nil},
		// Frames from other sites run in their own process and attach as their own targets.
		{"Target.setAutoAttach", map[string]any{"autoAttach": true, "waitForDebuggerOnStart": true, "flatten": true}},
	}
	for _, step := range steps {
		if err := b.call(t.session, step.method, step.params, nil); err != nil {
			log.Printf("preparing a %s: %s: %v", t.kind, step.method, err)
		}
	}
	b.call(t.session, "Runtime.runIfWaitingForDebugger", nil, nil)
	b.readyChan(t.id) <- t.session
}

// Open starts a job in a new tab: blank first, then the portal once the script is in place.
func (b *Browser) Open(job *jobSession, env jobEnv) error {
	var created struct {
		TargetID string `json:"targetId"`
	}
	if err := b.call("", "Target.createTarget", map[string]any{"url": "about:blank"}, &created); err != nil {
		return err
	}
	b.mu.Lock()
	b.tabs = append(b.tabs, &jobTab{job: job, env: env, tab: created.TargetID})
	b.mu.Unlock()

	var session string
	select {
	case session = <-b.readyChan(created.TargetID):
	case <-time.After(15 * time.Second):
		return errors.New("the new tab never got ready")
	}
	if err := b.call(session, "Page.navigate", map[string]any{"url": job.job.StartURL}, nil); err != nil {
		return err
	}
	return b.call("", "Target.activateTarget", map[string]any{"targetId": created.TargetID}, nil)
}

// jobFor finds the job a tab or frame belongs to. A frame belongs to its page; a page to the
// job it was opened for, or to the page that opened it.
func (b *Browser) jobForLocked(t *target) *jobTab {
	for depth := 0; t != nil && depth < 20; depth++ {
		if t.kind == "iframe" {
			t = b.sessions[t.parent]
			continue
		}
		for _, tab := range b.tabs {
			if tab.tab == t.id {
				return tab
			}
		}
		if t.opener == "" {
			return nil
		}
		t = b.targets[t.opener]
	}
	return nil
}

// pageOfLocked is the tab a frame is in.
func (b *Browser) pageOfLocked(t *target) *target {
	for depth := 0; t != nil && t.kind == "iframe" && depth < 20; depth++ {
		t = b.sessions[t.parent]
	}
	return t
}

// fromPage handles one message from the page script.
func (b *Browser) fromPage(session string, contextID int, payload string) {
	var msg pageMessage
	if json.Unmarshal([]byte(payload), &msg) != nil {
		return
	}
	b.mu.Lock()
	t := b.sessions[session]
	var tab *jobTab
	pageURL, origin := "", ""
	if t != nil {
		// This world's script is alive. When Chrome reuses a tab's first blank window for the
		// portal, the script stays in the world it started in and Chrome adds an empty one
		// beside it; only the one that calls in counts for its frame.
		frame := t.worlds[contextID]
		for id, f := range t.contexts {
			if f == frame && id != contextID {
				delete(t.contexts, id)
			}
		}
		t.contexts[contextID] = frame
		tab = b.jobForLocked(t)
		origin = t.origins[frame]
		if page := b.pageOfLocked(t); page != nil {
			pageURL = page.url
		}
	}
	b.mu.Unlock()
	if tab == nil {
		if msg.Action == "hello" {
			b.render(session, contextID, nil) // not a job's page: draw nothing
		}
		return
	}
	if msg.Action == "hello" {
		tab.job.mu.Lock()
		state := tab.job.state()
		tab.job.mu.Unlock()
		b.render(session, contextID, forOrigin(state, origin, tab.job.job.StartURL))
		return
	}
	if msg.Top && msg.URL != "" {
		pageURL = msg.URL
	}
	tab.job.handle(msg, pageURL, &tabEnv{b: b, tab: tab})
	b.renderJob(tab)
}

// renderJob redraws the panel in every page and frame of a job.
func (b *Browser) renderJob(tab *jobTab) {
	tab.job.mu.Lock()
	state := tab.job.state()
	tab.job.mu.Unlock()
	for _, c := range b.contextsOf(tab) {
		b.render(c.session, c.id, forOrigin(state, c.origin, tab.job.job.StartURL))
	}
}

// forOrigin leaves a fill's answers out of what a page or frame gets unless it's on the
// portal: a sign-in page on another site, or someone else's frame, only needs the buttons.
func forOrigin(state panelState, origin, startURL string) panelState {
	if state.Mode == "fill" && !samePortal(origin, startURL) {
		state.Fields = nil
	}
	return state
}

type pageContext struct {
	session string
	id      int
	origin  string
}

// contextsOf lists the credcloud worlds in a job's pages and frames.
func (b *Browser) contextsOf(tab *jobTab) []pageContext {
	b.mu.Lock()
	defer b.mu.Unlock()
	var out []pageContext
	for _, t := range b.targets {
		if b.jobForLocked(t) != tab {
			continue
		}
		for id, frame := range t.contexts {
			out = append(out, pageContext{session: t.session, id: id, origin: t.origins[frame]})
		}
	}
	return out
}

// render calls __credcloud.render(state) in one world. JSON goes in as a quoted string, so
// nothing in it can break out of the expression.
func (b *Browser) render(session string, contextID int, state any) {
	data, _ := json.Marshal(state)
	quoted, _ := json.Marshal(string(data))
	b.evaluate(session, contextID, "globalThis.__credcloud && __credcloud.render("+string(quoted)+")", nil)
}

// evaluate runs an expression in one world and decodes what it returns.
func (b *Browser) evaluate(session string, contextID int, expression string, result any) error {
	var reply struct {
		Result struct {
			Value json.RawMessage `json:"value"`
		} `json:"result"`
		ExceptionDetails *struct {
			Text string `json:"text"`
		} `json:"exceptionDetails"`
	}
	params := map[string]any{"expression": expression, "returnByValue": true}
	if contextID != 0 {
		params["contextId"] = contextID
	}
	if err := b.call(session, "Runtime.evaluate", params, &reply); err != nil {
		return err
	}
	if reply.ExceptionDetails != nil {
		return fmt.Errorf("script error: %s", reply.ExceptionDetails.Text)
	}
	if result != nil && len(reply.Result.Value) > 0 {
		return json.Unmarshal(reply.Result.Value, result)
	}
	return nil
}

// tabClosed ends the job whose tab this was, if it was one.
func (b *Browser) tabClosed(targetID string) {
	b.mu.Lock()
	var closed *jobTab
	for i, tab := range b.tabs {
		if tab.tab == targetID {
			closed = tab
			b.tabs = append(b.tabs[:i], b.tabs[i+1:]...)
			break
		}
	}
	b.mu.Unlock()
	if closed != nil {
		closed.job.closed(closed.env)
	}
}

// tabEnv types into a job's frames, and passes CredCloud calls through to the job's env.
type tabEnv struct {
	b   *Browser
	tab *jobTab
}

func (e *tabEnv) post(path string, body any, result any) error {
	return e.tab.env.post(path, body, result)
}

// fillFrames types into every page and frame of the tab that's on the job's portal, and
// only those: someone else's frame on the page never gets an answer.
func (e *tabEnv) fillFrames(items []FillItem) FillReport {
	report := FillReport{Filled: []int{}, Problems: []string{}}
	left := items
	for _, c := range e.b.contextsOf(e.tab) {
		if len(left) == 0 {
			break
		}
		if !samePortal(c.origin, e.tab.job.job.StartURL) {
			continue
		}
		data, _ := json.Marshal(left)
		quoted, _ := json.Marshal(string(data))
		var raw string
		if err := e.b.evaluate(c.session, c.id, "__credcloud.fill("+string(quoted)+")", &raw); err != nil {
			continue // the frame navigated or went away mid-fill; the next press gets it
		}
		var got FillReport
		json.Unmarshal([]byte(raw), &got)
		done := map[int]bool{}
		for _, i := range got.Filled {
			done[i] = true
		}
		report.Filled = append(report.Filled, got.Filled...)
		report.Problems = append(report.Problems, got.Problems...)
		var still []FillItem
		for _, item := range left {
			if !done[item.Index] {
				still = append(still, item)
			}
		}
		left = still
	}
	return report
}

// OpenJobs lists the jobs from a server that are open in a tab now.
func (b *Browser) OpenJobs(server string) []int64 {
	b.mu.Lock()
	defer b.mu.Unlock()
	var ids []int64
	for _, tab := range b.tabs {
		if tab.job.server == server {
			ids = append(ids, tab.job.job.ID)
		}
	}
	return ids
}

func (b *Browser) isClosed() bool {
	select {
	case <-b.Closed:
		return true
	default:
		return false
	}
}
