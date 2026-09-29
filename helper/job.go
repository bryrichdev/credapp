package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"sync"
)

// One job from CredCloud, open in a tab of the helper's Chrome, and what the panel on its pages
// shows. A learn job records the boxes she picks and saves them as the template's next
// version. A fill job types one provider's answers into the portal, page by page, and reports
// which boxes it filled. Neither ever submits anything.

// Field is one box of a portal template. The JSON matches CredCloud's PortalField.
type Field struct {
	Label        string `json:"label"`
	By           string `json:"by"`
	Locator      string `json:"locator"`
	Kind         string `json:"kind"`
	Source       string `json:"source"`
	Format       string `json:"format"`
	DefaultValue string `json:"defaultValue"`
	Page         string `json:"page"`
}

type Answer struct {
	Field int    `json:"field"`
	Value string `json:"value"`
}

type Source struct {
	Key   string `json:"key"`
	Label string `json:"label"`
}

type Format struct {
	Name  string `json:"name"`
	Label string `json:"label"`
}

// Formats keeps CredCloud's order, which a Go map wouldn't: it arrives as a JSON object.
type Formats []Format

func (f *Formats) UnmarshalJSON(data []byte) error {
	if bytes.Equal(bytes.TrimSpace(data), []byte("null")) {
		*f = nil
		return nil
	}
	dec := json.NewDecoder(bytes.NewReader(data))
	if tok, err := dec.Token(); err != nil || tok != json.Delim('{') {
		return fmt.Errorf("formats: want an object")
	}
	var out Formats
	for dec.More() {
		tok, err := dec.Token()
		if err != nil {
			return err
		}
		var label string
		if err := dec.Decode(&label); err != nil {
			return err
		}
		out = append(out, Format{Name: tok.(string), Label: label})
	}
	*f = out
	return nil
}

// Job is what CredCloud's /runner/api/jobs/next returns.
type Job struct {
	ID           int64    `json:"id"`
	Kind         string   `json:"kind"`
	TemplateID   int64    `json:"templateId"`
	TemplateName string   `json:"templateName"`
	PayerName    string   `json:"payerName"`
	StartURL     string   `json:"startUrl"`
	Revision     int      `json:"revision"`
	Fields       []Field  `json:"fields"`
	ProviderName string   `json:"providerName"`
	Answers      []Answer `json:"answers"`
	Sources      []Source `json:"sources"`
	Formats      Formats  `json:"formats"`
}

// FillItem is one answer to type, as the page script takes it.
type FillItem struct {
	Index int    `json:"index"`
	Field Field  `json:"field"`
	Value string `json:"value"`
}

// FillReport is what the page script did with the items it was given.
type FillReport struct {
	Filled   []int    `json:"filled"`
	Problems []string `json:"problems"`
}

// jobEnv is what a job needs from the rest of the helper: typing into the tab's frames, and
// telling CredCloud how it went.
type jobEnv interface {
	fillFrames(items []FillItem) FillReport
	post(path string, body any, result any) error
}

type jobSession struct {
	mu       sync.Mutex
	server   string
	job      Job
	fields   []Field
	filled   map[int]bool
	pending  *Field
	picking  bool
	finished bool
	message  string
	tone     string
	version  int64
}

func newJobSession(server string, job Job) *jobSession {
	s := &jobSession{server: server, job: job, fields: append([]Field(nil), job.Fields...), filled: map[int]bool{}}
	switch {
	case job.Kind == "fill":
		s.message = "Sign in, open the form, then press Fill this page."
	case len(job.Fields) > 0:
		s.message = fmt.Sprintf("Starting from version %d. Pick more boxes, or remove any that changed.", job.Revision)
	default:
		s.message = "Sign in and open the form. Then press Pick a box."
	}
	return s
}

// panelState is what the panel on the page draws.
type panelState struct {
	Version  int64       `json:"version"`
	Mode     string      `json:"mode"`
	Title    string      `json:"title"`
	Provider string      `json:"provider,omitempty"`
	Message  string      `json:"message"`
	Tone     string      `json:"tone"`
	Finished bool        `json:"finished"`
	Picking  bool        `json:"picking"`
	Progress string      `json:"progress,omitempty"`
	Pending  *Field      `json:"pending,omitempty"`
	Sources  []Source    `json:"sources,omitempty"`
	Formats  Formats     `json:"formats,omitempty"`
	Fields   []panelItem `json:"fields,omitempty"`
}

type panelItem struct {
	Label  string `json:"label"`
	Detail string `json:"detail"`
	Filled bool   `json:"filled,omitempty"`
}

// state is what the panel should show now. Each one is numbered: redraws travel on their own
// goroutines and can arrive out of order, and the page ignores one older than what it shows.
func (s *jobSession) state() panelState {
	s.version++
	st := panelState{
		Version: s.version, Mode: s.job.Kind, Title: s.job.PayerName + " / " + s.job.TemplateName, Provider: s.job.ProviderName,
		Message: s.message, Tone: s.tone, Finished: s.finished, Picking: s.picking,
	}
	if s.job.Kind == "fill" {
		st.Progress = fmt.Sprintf("%d of %d boxes filled", len(s.filled), len(s.fields))
		for i, f := range s.fields {
			st.Fields = append(st.Fields, panelItem{Label: f.Label, Detail: s.answer(i), Filled: s.filled[i]})
		}
		return st
	}
	st.Pending = s.pending
	st.Sources = s.job.Sources
	st.Formats = s.job.Formats
	st.Progress = boxes(len(s.fields)) + " so far"
	for _, f := range s.fields {
		st.Fields = append(st.Fields, panelItem{Label: f.Label, Detail: s.detail(f)})
	}
	return st
}

func (s *jobSession) answer(index int) string {
	for _, a := range s.job.Answers {
		if a.Field == index {
			return a.Value
		}
	}
	return ""
}

// detail says what goes in a box, in words: "Provider / NPI, Digits only".
func (s *jobSession) detail(f Field) string {
	if f.Source == "" {
		return fmt.Sprintf("always \"%s\"", f.DefaultValue)
	}
	detail := f.Source
	for _, src := range s.job.Sources {
		if src.Key == f.Source {
			detail = src.Label
		}
	}
	if f.Format != "" && f.Format != "AS_SAVED" {
		for _, format := range s.job.Formats {
			if format.Name == f.Format {
				detail += ", " + format.Label
			}
		}
	}
	if f.DefaultValue != "" {
		detail += fmt.Sprintf(", or \"%s\" when empty", f.DefaultValue)
	}
	return detail
}

func (s *jobSession) say(message, tone string) {
	s.message = message
	s.tone = tone
}

// pageMessage is a press in the panel, or a click while picking, as the page sends it.
type pageMessage struct {
	Action string          `json:"action"`
	Data   json.RawMessage `json:"data"`
	URL    string          `json:"url"`
	Top    bool            `json:"top"`
}

// handle does what a press in the panel asks. pageURL is the address of the tab's page, which
// is where a taught box says it lives.
func (s *jobSession) handle(msg pageMessage, pageURL string, env jobEnv) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.finished {
		return
	}
	job := s.job
	switch msg.Action {
	case "fill":
		if job.Kind != "fill" {
			return
		}
		s.fill(pageURL, env)
	case "done":
		if job.Kind != "fill" {
			return
		}
		filled, missed := s.outcome()
		if err := env.post(fmt.Sprintf("/runner/api/jobs/%d/filled", job.ID),
			map[string]any{"filled": filled, "missed": missed}, nil); err != nil {
			s.say(err.Error(), "error")
			return
		}
		s.finished = true
		message := fmt.Sprintf("Filled %d of %d boxes.", len(filled), len(s.fields))
		if len(missed) > 0 {
			message += " Not filled: " + strings.Join(missed, ", ") + "."
		}
		s.say(message+" Check every page, then submit it in the portal yourself.", "done")
	case "cancel":
		if err := env.post(fmt.Sprintf("/runner/api/jobs/%d/cancel", job.ID), nil, nil); err != nil {
			s.say(err.Error(), "error")
			return
		}
		s.finished = true
		s.picking = false
		s.say("Cancelled. Nothing was saved.", "done")
	case "startPicking":
		if job.Kind == "fill" {
			return
		}
		s.picking = true
		s.pending = nil
		s.say("Click a box on the page. Clicks go to CredCloud, not the portal, until you stop picking.", "")
	case "stopPicking":
		s.picking = false
		s.say("", "")
	case "notABox":
		if s.picking {
			s.say("That isn't a box CredCloud can fill. It types, picks options and ticks boxes; click the box itself or its label.", "error")
		}
	case "unclear":
		if s.picking {
			s.say("CredCloud couldn't pin that box down. Try clicking its label instead.", "error")
		}
	case "picked":
		if job.Kind == "fill" || !s.picking {
			return
		}
		if !samePortal(msg.URL, job.StartURL) {
			s.say("That box is on another website. CredCloud only fills the portal's own pages.", "error")
			return
		}
		var picked Field
		if json.Unmarshal(msg.Data, &picked) != nil || picked.Locator == "" {
			return
		}
		if picked.Label == "" {
			picked.Label = fmt.Sprintf("Box %d", len(s.fields)+1)
		}
		picked.Page = pathOf(pageURL)
		s.pending = &picked
		s.picking = false
		s.say("", "")
	case "add":
		if s.pending == nil {
			return
		}
		var chosen struct {
			Label, Source, Format, DefaultValue string
		}
		_ = json.Unmarshal(msg.Data, &chosen)
		source := strings.TrimSpace(chosen.Source)
		fixed := strings.TrimSpace(chosen.DefaultValue)
		if source == "" && fixed == "" {
			s.say("Choose data, or type a fixed answer.", "error")
			return
		}
		if len(s.fields) >= 250 {
			s.say("A portal template can have up to 250 boxes.", "error")
			return
		}
		field := *s.pending
		if label := strings.TrimSpace(chosen.Label); label != "" {
			field.Label = label
		}
		field.Source = source
		field.Format = chosen.Format
		field.DefaultValue = fixed
		s.fields = append(s.fields, field)
		s.pending = nil
		s.picking = true
		s.say("Added. Click the next box, or stop picking.", "")
	case "discard":
		s.pending = nil
		s.say("", "")
	case "remove":
		var which struct{ Index int }
		if json.Unmarshal(msg.Data, &which) == nil && which.Index >= 0 && which.Index < len(s.fields) {
			s.fields = append(s.fields[:which.Index], s.fields[which.Index+1:]...)
			s.say("Removed. Save to keep the change.", "")
		}
	case "save":
		if job.Kind == "fill" {
			return
		}
		if len(s.fields) == 0 {
			s.say("Pick at least one box first.", "error")
			return
		}
		var saved struct {
			Revision int `json:"revision"`
		}
		if err := env.post(fmt.Sprintf("/runner/api/jobs/%d/learned", job.ID),
			map[string]any{"fields": s.fields}, &saved); err != nil {
			s.say(err.Error(), "error")
			return
		}
		s.finished = true
		s.picking = false
		s.say(fmt.Sprintf("Saved as version %d. It's ready to fill from a provider's Portal fills page in CredCloud. You can close this tab.", saved.Revision), "done")
	}
}

// fill types what belongs on this page, and says what happened in the panel.
func (s *jobSession) fill(pageURL string, env jobEnv) {
	if !samePortal(pageURL, s.job.StartURL) {
		s.say("This page isn't on the portal the fill is for, so CredCloud won't type into it.", "error")
		return
	}
	open := s.forThisPage(pageURL)
	report := FillReport{}
	if len(open) > 0 {
		report = env.fillFrames(open)
	}
	for _, i := range report.Filled {
		s.filled[i] = true
	}
	var noAnswer []string
	stillToFill := 0
	for i, f := range s.fields {
		if s.answer(i) == "" {
			noAnswer = append(noAnswer, f.Label)
		} else if !s.filled[i] {
			stillToFill++
		}
	}
	switch {
	case len(open) == 0 || (stillToFill == 0 && len(report.Problems) == 0):
		message := ""
		if len(report.Filled) > 0 {
			message = "Filled " + boxes(len(report.Filled)) + " on this page. "
		}
		message += "Every box with an answer is filled."
		if len(noAnswer) > 0 {
			message += " No answer in CredCloud for: " + strings.Join(noAnswer, ", ") + "."
		}
		s.say(message+" Check each page and submit it yourself, then press Done.", "")
	case len(report.Filled) == 0 && len(report.Problems) == 0:
		s.say("CredCloud didn't find any of this template's boxes on this page. Go to the form, then press Fill this page again.", "")
	default:
		message := fmt.Sprintf("Filled %s on this page. %d still to fill.", boxes(len(report.Filled)), stillToFill)
		tone := ""
		if len(report.Problems) > 0 {
			message += " Left alone: " + strings.Join(report.Problems, "; ") + "."
			tone = "error"
		}
		s.say(message, tone)
	}
}

// forThisPage is the answers to type on this page. Each box remembers the page it was taught
// on. When some open boxes were taught on this page's address, only those (and any without a
// page) are filled, so an "Address" box from page 3 doesn't land in page 2's "Address". When
// none were, as on a portal whose steps share one address or whose addresses change per
// application, every open box found on the page is filled.
func (s *jobSession) forThisPage(pageURL string) []FillItem {
	var open []FillItem
	for i, f := range s.fields {
		if value := s.answer(i); value != "" && !s.filled[i] {
			open = append(open, FillItem{Index: i, Field: f, Value: value})
		}
	}
	here := pathOf(pageURL)
	taughtHere := false
	for _, item := range open {
		if item.Field.Page == here {
			taughtHere = true
		}
	}
	if !taughtHere {
		return open
	}
	var mine []FillItem
	for _, item := range open {
		if item.Field.Page == here || item.Field.Page == "" {
			mine = append(mine, item)
		}
	}
	return mine
}

// outcome lists the boxes filled and not filled, by label.
func (s *jobSession) outcome() (filled, missed []string) {
	filled, missed = []string{}, []string{}
	var indexes []int
	for i := range s.fields {
		indexes = append(indexes, i)
	}
	sort.Ints(indexes)
	for _, i := range indexes {
		if s.filled[i] {
			filled = append(filled, s.fields[i].Label)
		} else {
			missed = append(missed, s.fields[i].Label)
		}
	}
	return filled, missed
}

// closed runs when the job's tab goes away before she finished. A fill that typed something
// is reported, so CredCloud shows what was filled; anything else is cancelled.
func (s *jobSession) closed(env jobEnv) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.finished {
		return
	}
	s.finished = true
	if s.job.Kind == "fill" && len(s.filled) > 0 {
		filled, missed := s.outcome()
		if env.post(fmt.Sprintf("/runner/api/jobs/%d/filled", s.job.ID),
			map[string]any{"filled": filled, "missed": missed}, nil) == nil {
			return
		}
	}
	_ = env.post(fmt.Sprintf("/runner/api/jobs/%d/cancel", s.job.ID), nil, nil)
}

func (s *jobSession) isFinished() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.finished
}

func boxes(n int) string {
	if n == 1 {
		return "1 box"
	}
	return strconv.Itoa(n) + " boxes"
}

// site is the last two labels of a host: portal.payer.com and login.payer.com are one portal.
func site(host string) string {
	host = strings.ToLower(host)
	if net.ParseIP(host) != nil || !strings.Contains(host, ".") {
		return host
	}
	labels := strings.Split(host, ".")
	return labels[len(labels)-2] + "." + labels[len(labels)-1]
}

func samePortal(pageURL, startURL string) bool {
	page, err1 := url.Parse(pageURL)
	start, err2 := url.Parse(startURL)
	if err1 != nil || err2 != nil || page.Hostname() == "" || start.Hostname() == "" {
		return false
	}
	return site(page.Hostname()) == site(start.Hostname())
}

func pathOf(pageURL string) string {
	u, err := url.Parse(pageURL)
	if err != nil {
		return ""
	}
	return u.Path
}
