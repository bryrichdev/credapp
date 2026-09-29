package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"strings"
)

// The step 4 demo: a fake job against the test portal, with no CredCloud at all.
//
//	go run . demo         teach the test portal (writes learned.json)
//	go run . demo fill    fill what you taught, with a made-up provider

func demo(fill bool) {
	startURL, err := startTestPortal("127.0.0.1:0")
	must(err)
	fmt.Println("test portal:", startURL)
	b, err := openBrowser()
	must(err)
	job := fakeLearnJob(startURL)
	if fill {
		job = fakeFillJob(startURL)
	}
	must(b.Open(newJobSession("demo", job), fakeCredCloud{}))
	fmt.Println("opened a", job.Kind, "job. Quit the helper's Chrome (⌘Q) to stop.")
	<-b.Closed
}

// --- A fake CredCloud, until step 5 talks to the real one ---

var fakeSources = []Source{
	{"provider.first_name", "Provider / First name"},
	{"provider.last_name", "Provider / Last name"},
	{"provider.npi", "Provider / NPI"},
	{"provider.dob", "Provider / Date of birth"},
	{"provider.sex", "Provider / Sex"},
	{"provider.state", "Provider / State"},
	{"provider.street_1", "Provider / Street"},
	{"provider.city", "Provider / City"},
	{"provider.phone_number", "Provider / Phone"},
	{"group.tax_id", "Practice / Tax ID"},
}

var fakeFormats = Formats{
	{"AS_SAVED", "As saved"},
	{"DATE_MDY", "Date MM/DD/YYYY"},
	{"DIGITS", "Digits only"},
	{"PHONE_DASHES", "Phone 801-555-0142"},
	{"UPPER", "UPPERCASE"},
}

// One made-up provider, as CredCloud stores the data.
var fakeProvider = map[string]string{
	"provider.first_name":   "Jane",
	"provider.last_name":    "Doe",
	"provider.npi":          "1234567890",
	"provider.dob":          "1985-04-12",
	"provider.sex":          "F",
	"provider.state":        "Utah",
	"provider.street_1":     "100 Main St",
	"provider.city":         "Salt Lake City",
	"provider.phone_number": "8015550142",
	"group.tax_id":          "12-3456789",
}

func must(err error) {
	if err != nil {
		panic(err)
	}
}

func fakeLearnJob(startURL string) Job {
	return Job{ID: 1, Kind: "learn", TemplateName: "Enrollment", PayerName: "Acme Health",
		StartURL: startURL, Sources: fakeSources, Formats: fakeFormats}
}

// fakeFillJob fills what you taught. Formats aren't applied here; the server does that.
func fakeFillJob(startURL string) Job {
	data, err := os.ReadFile("learned.json")
	if err != nil {
		log.Fatal("teach first: go run . (then Save template)")
	}
	var fields []Field
	must(json.Unmarshal(data, &fields))
	var answers []Answer
	for i, f := range fields {
		value := fakeProvider[f.Source]
		if value == "" {
			value = f.DefaultValue
		}
		answers = append(answers, Answer{Field: i, Value: value})
	}
	return Job{ID: 2, Kind: "fill", TemplateName: "Enrollment", PayerName: "Acme Health",
		StartURL: startURL, Revision: 1, Fields: fields, ProviderName: "Jane Doe", Answers: answers}
}

// fakeCredCloud prints what the helper would send to CredCloud, and keeps a taught template.
type fakeCredCloud struct{}

func (fakeCredCloud) post(path string, body any, result any) error {
	data, _ := json.MarshalIndent(body, "", "  ")
	fmt.Printf("POST %s\n%s\n", path, data)
	if strings.HasSuffix(path, "/learned") {
		fields, _ := json.MarshalIndent(body.(map[string]any)["fields"], "", "  ")
		if err := os.WriteFile("learned.json", fields, 0o644); err != nil {
			return err
		}
		fmt.Println("saved learned.json: now try go run . fill")
		if result != nil {
			return json.Unmarshal([]byte(`{"revision":1}`), result)
		}
	}
	return nil
}

func (fakeCredCloud) fillFrames([]FillItem) FillReport { return FillReport{} }
