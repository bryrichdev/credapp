package main

import (
	"fmt"
	"log"
	"net"
	"net/http"
)

// A fake payer portal on this computer, to teach and fill while there's no real one wired up.
// It sends a strict Content-Security-Policy, like many real portals, which the panel has to
// work under. Page 1 has a form in a frame; page 2 is reached by a link. If anything ever
// submits the form, the terminal says so loudly.

const strictCSP = "default-src 'self'; script-src 'self'; style-src 'self'"

// startTestPortal serves the test portal at addr ("127.0.0.1:0" for any free port) and returns
// its start address.
func startTestPortal(addr string) (string, error) {
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		return "", err
	}
	mux := http.NewServeMux()
	page := func(body string) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Security-Policy", strictCSP)
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
			fmt.Fprintf(w, "<!doctype html><html><head><title>Acme Health provider portal</title></head><body>%s</body></html>", body)
		}
	}
	mux.HandleFunc("/enroll", page(`
		<h1>Provider enrollment: page 1</h1>
		<form action="/submitted" method="post">
		<p><label for="first">First name *</label> <input id="first" name="first"></p>
		<p><label for="last">Last name *</label> <input id="last" name="last"></p>
		<p><input id="npi" name="npi" placeholder="NPI"></p>
		<p><label for="dob">Date of birth</label> <input id="dob" name="dob" placeholder="MM/DD/YYYY"></p>
		<p><label for="state">State</label> <select id="state"><option value="">Choose</option><option value="UT">Utah</option><option value="NV">Nevada</option><option value="ID">Idaho</option></select></p>
		<p>Sex: <label><input type="radio" name="sex" value="F"> Female</label> <label><input type="radio" name="sex" value="M"> Male</label></p>
		<p><label><input type="checkbox" id="new"> Accepting new patients</label></p>
		<p><button type="submit">Submit application</button></p>
		</form>
		<p>Tax details (a frame):</p>
		<iframe src="/taxes" width="420" height="70"></iframe>
		<p><a href="/enroll/address">Next page &rarr;</a></p>`))
	mux.HandleFunc("/taxes", page(`<label>Tax ID <input id="tax"></label>`))
	mux.HandleFunc("/enroll/address", page(`
		<h1>Provider enrollment: page 2</h1>
		<form action="/submitted" method="post">
		<p><label for="street">Street</label> <input id="street"></p>
		<p><label for="city">City</label> <input id="city"></p>
		<p><label for="phone">Office phone</label> <input id="phone"></p>
		<p><button type="submit">Submit application</button></p>
		</form>
		<p><a href="/enroll">&larr; Back</a></p>`))
	mux.HandleFunc("/submitted", func(w http.ResponseWriter, r *http.Request) {
		log.Println("!!! THE PORTAL FORM WAS SUBMITTED. The helper must never do this; if you didn't press Submit yourself, it's a bug.")
		fmt.Fprint(w, "Submitted.")
	})
	go http.Serve(ln, mux)
	return "http://" + ln.Addr().String() + "/enroll", nil
}
