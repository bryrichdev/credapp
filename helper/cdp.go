package main

import (
	"context"
	"encoding/json"
	"fmt"
	"sync"
	"sync/atomic"

	"github.com/coder/websocket"
)

// Event is a CDP message with no id: something Chrome is telling us.
type Event struct {
	Session string // which tab/frame it's from ("" = the browser)
	Method  string
	Params  json.RawMessage
}

type CDP struct {
	ws      *websocket.Conn
	nextID  atomic.Int64
	mu      sync.Mutex
	pending map[int64]chan message
	Events  chan Event
}

type message struct {
	ID        int64           `json:"id,omitempty"`
	SessionID string          `json:"sessionId,omitempty"`
	Method    string          `json:"method,omitempty"`
	Params    json.RawMessage `json:"params,omitempty"`
	Result    json.RawMessage `json:"result,omitempty"`
	Error     json.RawMessage `json:"error,omitempty"`
}

func Dial(ctx context.Context, url string) (*CDP, error) {
	ws, _, err := websocket.Dial(ctx, url, nil)
	if err != nil {
		return nil, err
	}
	ws.SetReadLimit(64 << 20)
	c := &CDP{ws: ws, pending: map[int64]chan message{}, Events: make(chan Event, 1000)}
	go c.readLoop()
	return c, nil
}

// readLoop is the only reader. Replies go to whoever is waiting on that id; events go to Events.
func (c *CDP) readLoop() {
	defer close(c.Events)
	for {
		_, data, err := c.ws.Read(context.Background())
		if err != nil {
			return
		}
		var m message
		if json.Unmarshal(data, &m) != nil {
			continue
		}
		if m.ID != 0 {
			c.mu.Lock()
			ch := c.pending[m.ID]
			delete(c.pending, m.ID)
			c.mu.Unlock()
			if ch != nil {
				ch <- m
			}
			continue
		}
		c.Events <- Event{Session: m.SessionID, Method: m.Method, Params: m.Params}
	}
}

// Call sends a command (to a tab if session isn't "") and waits for its reply.
func (c *CDP) Call(ctx context.Context, session, method string, params any, result any) error {
	id := c.nextID.Add(1)
	raw, _ := json.Marshal(params)
	if params == nil {
		raw = nil
	}
	data, _ := json.Marshal(message{ID: id, SessionID: session, Method: method, Params: raw})

	ch := make(chan message, 1)
	c.mu.Lock()
	c.pending[id] = ch
	c.mu.Unlock()

	if err := c.ws.Write(ctx, websocket.MessageText, data); err != nil {
		return err
	}
	select {
	case reply := <-ch:
		if reply.Error != nil {
			return fmt.Errorf("%s: %s", method, reply.Error)
		}
		if result != nil && reply.Result != nil {
			return json.Unmarshal(reply.Result, result)
		}
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}
