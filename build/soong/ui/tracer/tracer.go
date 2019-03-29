// Copyright 2017 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// This package implements a trace file writer, whose files can be opened in
// chrome://tracing.
//
// It implements the JSON Array Format defined here:
// https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/edit
package tracer

import (
	"bytes"
	"compress/gzip"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"strings"
	"sync"
	"time"

	"android/soong/ui/logger"
)

type Thread uint64

const (
	MainThread     = Thread(iota)
	MaxInitThreads = Thread(iota)
)

type Tracer interface {
	Begin(name string, thread Thread)
	End(thread Thread)
	Complete(name string, thread Thread, begin, end uint64)

	ImportMicrofactoryLog(filename string)
	ImportNinjaLog(thread Thread, filename string, startOffset time.Time)

	NewThread(name string) Thread
}

type tracerImpl struct {
	lock sync.Mutex
	log  logger.Logger

	buf  bytes.Buffer
	file *os.File
	w    io.WriteCloser

	firstEvent bool
	nextTid    uint64
}

var _ Tracer = &tracerImpl{}

type viewerEvent struct {
	Name  string      `json:"name,omitempty"`
	Phase string      `json:"ph"`
	Scope string      `json:"s,omitempty"`
	Time  uint64      `json:"ts"`
	Dur   uint64      `json:"dur,omitempty"`
	Pid   uint64      `json:"pid"`
	Tid   uint64      `json:"tid"`
	ID    uint64      `json:"id,omitempty"`
	Arg   interface{} `json:"args,omitempty"`
}

type nameArg struct {
	Name string `json:"name"`
}

type nopCloser struct{ io.Writer }

func (nopCloser) Close() error { return nil }

// New creates a new Tracer, storing log in order to log errors later.
// Events are buffered in memory until SetOutput is called.
func New(log logger.Logger) *tracerImpl {
	ret := &tracerImpl{
		log: log,

		firstEvent: true,
		nextTid:    uint64(MaxInitThreads),
	}
	ret.startBuffer()

	return ret
}

func (t *tracerImpl) startBuffer() {
	t.w = nopCloser{&t.buf}
	fmt.Fprintln(t.w, "[")

	t.defineThread(MainThread, "main")
}

func (t *tracerImpl) close() {
	if t.file != nil {
		fmt.Fprintln(t.w, "]")

		if err := t.w.Close(); err != nil {
			t.log.Println("Error closing trace writer:", err)
		}

		if err := t.file.Close(); err != nil {
			t.log.Println("Error closing trace file:", err)
		}
		t.file = nil
		t.startBuffer()
	}
}

// SetOutput creates the output file (rotating old files).
func (t *tracerImpl) SetOutput(filename string) {
	t.lock.Lock()
	defer t.lock.Unlock()

	t.close()

	// chrome://tracing requires that compressed trace files end in .gz
	if !strings.HasSuffix(filename, ".gz") {
		filename += ".gz"
	}

	f, err := logger.CreateFileWithRotation(filename, 5)
	if err != nil {
		t.log.Println("Failed to create trace file:", err)
		return
	}
	// Save the file, since closing the gzip Writer doesn't close the
	// underlying file.
	t.file = f
	t.w = gzip.NewWriter(f)

	// Write out everything that happened since the start
	if _, err := io.Copy(t.w, &t.buf); err != nil {
		t.log.Println("Failed to write trace buffer to file:", err)
	}
	t.buf = bytes.Buffer{}
}

// Close closes the output file. Any future events will be buffered until the
// next call to SetOutput.
func (t *tracerImpl) Close() {
	t.lock.Lock()
	defer t.lock.Unlock()

	t.close()
}

func (t *tracerImpl) writeEvent(event *viewerEvent) {
	t.lock.Lock()
	defer t.lock.Unlock()

	t.writeEventLocked(event)
}

func (t *tracerImpl) writeEventLocked(event *viewerEvent) {
	bytes, err := json.Marshal(event)
	if err != nil {
		t.log.Println("Failed to marshal event:", err)
		t.log.Verbosef("Event: %#v", event)
		return
	}

	if !t.firstEvent {
		fmt.Fprintln(t.w, ",")
	} else {
		t.firstEvent = false
	}

	if _, err = t.w.Write(bytes); err != nil {
		t.log.Println("Trace write error:", err)
	}
}

func (t *tracerImpl) defineThread(thread Thread, name string) {
	t.writeEventLocked(&viewerEvent{
		Name:  "thread_name",
		Phase: "M",
		Pid:   0,
		Tid:   uint64(thread),
		Arg: &nameArg{
			Name: name,
		},
	})
}

// NewThread returns a new Thread with an unused tid, writing the name out to
// the trace file.
func (t *tracerImpl) NewThread(name string) Thread {
	t.lock.Lock()
	defer t.lock.Unlock()

	ret := Thread(t.nextTid)
	t.nextTid += 1

	t.defineThread(ret, name)
	return ret
}

// Begin starts a new Duration Event. More than one Duration Event may be active
// at a time on each Thread, but they're nested.
func (t *tracerImpl) Begin(name string, thread Thread) {
	t.writeEvent(&viewerEvent{
		Name:  name,
		Phase: "B",
		Time:  uint64(time.Now().UnixNano()) / 1000,
		Pid:   0,
		Tid:   uint64(thread),
	})
}

// End finishes the most recent active Duration Event on the thread.
func (t *tracerImpl) End(thread Thread) {
	t.writeEvent(&viewerEvent{
		Phase: "E",
		Time:  uint64(time.Now().UnixNano()) / 1000,
		Pid:   0,
		Tid:   uint64(thread),
	})
}

// Complete writes a Complete Event, which are like Duration Events, but include
// a begin and end timestamp in the same event.
func (t *tracerImpl) Complete(name string, thread Thread, begin, end uint64) {
	t.writeEvent(&viewerEvent{
		Name:  name,
		Phase: "X",
		Time:  begin / 1000,
		Dur:   (end - begin) / 1000,
		Pid:   0,
		Tid:   uint64(thread),
	})
}
