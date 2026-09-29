package main

import (
	"errors"
	"fmt"
	"log"
	"os"
	"time"
)

// CredCloud Helper: fills payer portals in her own Chrome with provider data from CredCloud.
//
//	go run .                            run: open each job CredCloud sends, until stopped
//	go run . pair <server> <code>       connect this computer, with a code from CredCloud
//	go run . portal                     serve the test portal at http://127.0.0.1:8181/enroll
//	go run . demo [fill]                the step 4 demo, with no CredCloud

func main() {
	command := "run"
	if len(os.Args) > 1 {
		command = os.Args[1]
	}
	switch command {
	case "run":
		run()
	case "pair":
		if len(os.Args) != 4 {
			log.Fatal("usage: go run . pair <server> <code>   (get the code in CredCloud: My account > Connected browsers)")
		}
		pairCommand(os.Args[2], os.Args[3])
	case "portal":
		startURL, err := startTestPortal("127.0.0.1:8181")
		must(err)
		fmt.Println("test portal:", startURL, "(Ctrl+C to stop)")
		select {}
	case "demo":
		demo(len(os.Args) > 2 && os.Args[2] == "fill")
	default:
		log.Fatalf("unknown command %q", command)
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
	must(saveConfig(cfg))
	fmt.Printf("Connected to %s as %s. Now run: go run .\n", server, email)
}

// run asks every connected server for work every couple of seconds, and opens each job in the
// helper's Chrome. Chrome starts with the first job, and again if she quits it.
func run() {
	cfg := loadConfig()
	if len(cfg.Servers) == 0 {
		log.Fatal("Not connected yet. In CredCloud, go to My account > Connected browsers > Get a connection code, then run the command it shows.")
	}
	for server, sc := range cfg.Servers {
		fmt.Printf("Waiting for work from %s (%s). Ctrl+C to stop.\n", server, sc.Email)
	}
	var b *Browser
	for {
		b = poll(&cfg, b)
		if len(cfg.Servers) == 0 {
			log.Fatal("Not connected to any CredCloud now.")
		}
		time.Sleep(2 * time.Second)
	}
}

// poll asks each connected server once for a job and opens what comes back. It returns the
// browser, started if a job needed it.
func poll(cfg *config, b *Browser) *Browser {
	for server, sc := range cfg.Servers {
		client := &Client{Server: server, Token: sc.Token}
		var open []int64
		if b != nil && !b.isClosed() {
			open = b.OpenJobs(server)
		}
		job, err := client.Next(open)
		if errors.Is(err, errSignedOut) {
			log.Printf("%s: %v. Connect again with a new code.", server, err)
			delete(cfg.Servers, server)
			must(saveConfig(*cfg))
			continue
		}
		if err != nil {
			log.Printf("%s: %v", server, err)
			continue
		}
		if job == nil {
			continue
		}
		if b == nil || b.isClosed() {
			if b, err = openBrowser(); err != nil {
				log.Printf("couldn't start Chrome: %v", err)
				client.post(fmt.Sprintf("/runner/api/jobs/%d/cancel", job.ID), nil, nil)
				b = nil
				continue
			}
		}
		log.Printf("%s: opening %s job %d (%s / %s)", server, job.Kind, job.ID, job.PayerName, job.TemplateName)
		if err := b.Open(newJobSession(server, *job), client); err != nil {
			log.Printf("couldn't open job %d: %v", job.ID, err)
		}
	}
	return b
}
