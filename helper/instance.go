package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

// One helper runs at a time. It holds a lock file and listens on this computer only, on a port
// it writes to instance.json with a secret; a second start (a credcloud:// link, the app icon)
// hands its request over and exits. The secret keeps other accounts on the same computer from
// sending it anything.

type instanceFile struct {
	Port   int    `json:"port"`
	Secret string `json:"secret"`
}

type instance struct {
	ln   net.Listener
	file string
}

func listen(dir string, handle func(cmd, arg string)) (*instance, error) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return nil, err
	}
	secret := make([]byte, 24)
	_, _ = rand.Read(secret)
	info := instanceFile{Port: ln.Addr().(*net.TCPAddr).Port, Secret: hex.EncodeToString(secret)}
	data, _ := json.Marshal(info)
	file := filepath.Join(dir, "instance.json")
	if err := os.WriteFile(file, data, 0o600); err != nil {
		ln.Close()
		return nil, err
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.Header.Get("X-Secret") != info.Secret {
			http.Error(w, "no", http.StatusForbidden)
			return
		}
		body, _ := io.ReadAll(io.LimitReader(r.Body, 8192))
		handle(strings.Trim(r.URL.Path, "/"), string(body))
		w.WriteHeader(http.StatusNoContent)
	})
	go http.Serve(ln, mux)
	return &instance{ln: ln, file: file}, nil
}

func (i *instance) close() {
	i.ln.Close()
	os.Remove(i.file)
}

// forwardToRunning gives a request to the helper that's already running. It waits a few
// seconds for one that's still starting up.
func forwardToRunning(dir, command, arg string) bool {
	client := &http.Client{Timeout: 3 * time.Second}
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		var info instanceFile
		if data, err := os.ReadFile(filepath.Join(dir, "instance.json")); err == nil && json.Unmarshal(data, &info) == nil && info.Port > 0 {
			req, _ := http.NewRequest(http.MethodPost, "http://127.0.0.1:"+strconv.Itoa(info.Port)+"/"+command, strings.NewReader(arg))
			req.Header.Set("X-Secret", info.Secret)
			if resp, err := client.Do(req); err == nil {
				resp.Body.Close()
				if resp.StatusCode == http.StatusNoContent {
					return true
				}
			}
		}
		time.Sleep(200 * time.Millisecond)
	}
	return false
}

// waitForLock takes the lock, once any running helper has let it go.
func waitForLock(dir string, wait time.Duration) (func(), error) {
	deadline := time.Now().Add(wait)
	for {
		release, err := lockFile(filepath.Join(dir, "helper.lock"))
		if err == nil || time.Now().After(deadline) {
			return release, err
		}
		time.Sleep(200 * time.Millisecond)
	}
}

// forwardToRunningQuick tries once, for when no helper running is the likely case.
func forwardToRunningQuick(dir, command string) {
	var info instanceFile
	data, err := os.ReadFile(filepath.Join(dir, "instance.json"))
	if err != nil || json.Unmarshal(data, &info) != nil || info.Port == 0 {
		return
	}
	req, _ := http.NewRequest(http.MethodPost, "http://127.0.0.1:"+strconv.Itoa(info.Port)+"/"+command, nil)
	req.Header.Set("X-Secret", info.Secret)
	client := &http.Client{Timeout: 2 * time.Second}
	if resp, err := client.Do(req); err == nil {
		resp.Body.Close()
	}
}
