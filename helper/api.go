package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

// Talking to CredCloud's /runner/api. A Client is one server and this computer's token for it.
// It's also a job's jobEnv: the job posts what happened through it.

var errSignedOut = errors.New("CredCloud no longer accepts this computer's token")

var httpClient = &http.Client{Timeout: 30 * time.Second}

type Client struct {
	Server string
	Token  string
}

func (c *Client) do(method, path string, body any) (*http.Response, error) {
	var reader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, err
		}
		reader = bytes.NewReader(data)
	}
	req, err := http.NewRequest(method, c.Server+path, reader)
	if err != nil {
		return nil, err
	}
	if c.Token != "" {
		req.Header.Set("Authorization", "Bearer "+c.Token)
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	req.Header.Set("Accept", "application/json")
	// Cloudflare turns away requests that don't say what they are.
	req.Header.Set("User-Agent", "CredCloudHelper/"+version()+" ("+platform()+")")
	// Which build of the helper CredCloud should compare this one with.
	req.Header.Set("X-CredCloud-Helper", platform())
	return httpClient.Do(req)
}

// failure turns an error response into an error. CredCloud's messages are written for her,
// so they're passed on as they are.
func failure(resp *http.Response) error {
	if resp.StatusCode == http.StatusUnauthorized {
		return errSignedOut
	}
	var problem struct {
		Error string `json:"error"`
	}
	json.NewDecoder(io.LimitReader(resp.Body, 1<<16)).Decode(&problem)
	if problem.Error == "" {
		problem.Error = fmt.Sprintf("CredCloud didn't accept that (%d). Try again.", resp.StatusCode)
	}
	return errors.New(problem.Error)
}

// Next asks for the next job, skipping ones already open. nil means there's nothing to do.
// latest is the checksum of CredCloud's current build of the helper for this computer, when
// it has one, so the helper can tell it's out of date.
func (c *Client) Next(open []int64) (job *Job, latest string, err error) {
	path := "/runner/api/jobs/next"
	if len(open) > 0 {
		ids := make([]string, len(open))
		for i, id := range open {
			ids[i] = strconv.FormatInt(id, 10)
		}
		path += "?skip=" + url.QueryEscape(strings.Join(ids, ","))
	}
	resp, err := c.do(http.MethodGet, path, nil)
	if err != nil {
		return nil, "", err
	}
	defer resp.Body.Close()
	latest = resp.Header.Get("X-Helper-Sha256")
	switch resp.StatusCode {
	case http.StatusNoContent:
		return nil, latest, nil
	case http.StatusOK:
		job = &Job{}
		if err := json.NewDecoder(resp.Body).Decode(job); err != nil {
			return nil, latest, err
		}
		return job, latest, nil
	default:
		return nil, latest, failure(resp)
	}
}

// cancel gives a job back, saying why, for the page she started it from.
func (c *Client) cancel(id int64, reason string) error {
	return c.post(fmt.Sprintf("/runner/api/jobs/%d/cancel", id), map[string]string{"reason": reason}, nil)
}

// post sends what happened with a job: filled, learned or cancel.
func (c *Client) post(path string, body any, result any) error {
	resp, err := c.do(http.MethodPost, path, body)
	if err != nil {
		return errors.New("CredCloud Helper couldn't reach CredCloud. Check the internet connection, then try again.")
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return failure(resp)
	}
	if result != nil && resp.StatusCode != http.StatusNoContent {
		return json.NewDecoder(resp.Body).Decode(result)
	}
	return nil
}

func (c *Client) fillFrames([]FillItem) FillReport { return FillReport{} }

// pair swaps a one-time code from CredCloud for this computer's token, and returns whose
// account it's now connected to.
func pair(server, code string) (*Client, string, error) {
	c := &Client{Server: server}
	var paired struct {
		Token string `json:"token"`
		Email string `json:"email"`
	}
	err := c.post("/runner/api/pair", map[string]string{"code": code, "name": computerName()}, &paired)
	if err != nil {
		return nil, "", err
	}
	c.Token = paired.Token
	return c, paired.Email, nil
}

// serverOrigin cleans up a CredCloud address to scheme://host[:port].
func serverOrigin(raw string) (string, error) {
	u, err := url.Parse(strings.TrimSpace(raw))
	if err != nil || (u.Scheme != "https" && u.Scheme != "http") || u.Host == "" {
		return "", fmt.Errorf("%q isn't a CredCloud address, like https://credcloud.app", raw)
	}
	return strings.ToLower(u.Scheme + "://" + u.Host), nil
}
