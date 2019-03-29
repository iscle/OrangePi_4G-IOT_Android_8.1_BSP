// Copyright 2016 Google Inc. All rights reserved.
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

package main

import (
	"runtime"
)

type RateLimit struct {
	requests chan struct{}
	finished chan int
	released chan int
	stop     chan struct{}
}

// NewRateLimit starts a new rate limiter with maxExecs number of executions
// allowed to happen at a time. If maxExecs is <= 0, it will default to the
// number of logical CPUs on the system.
//
// With Finish and Release, we'll keep track of outstanding buffer sizes to be
// written. If that size goes above maxMem, we'll prevent starting new
// executions.
//
// The total memory use may be higher due to current executions. This just
// prevents runaway memory use due to slower writes.
func NewRateLimit(maxExecs int, maxMem int64) *RateLimit {
	if maxExecs <= 0 {
		maxExecs = runtime.NumCPU()
	}
	if maxMem <= 0 {
		// Default to 512MB
		maxMem = 512 * 1024 * 1024
	}

	ret := &RateLimit{
		requests: make(chan struct{}),

		// Let all of the pending executions to mark themselves as finished,
		// even if our goroutine isn't processing input.
		finished: make(chan int, maxExecs),

		released: make(chan int),
		stop:     make(chan struct{}),
	}

	go ret.goFunc(maxExecs, maxMem)

	return ret
}

// RequestExecution blocks until another execution can be allowed to run.
func (r *RateLimit) RequestExecution() Execution {
	<-r.requests
	return r.finished
}

type Execution chan<- int

// Finish will mark your execution as finished, and allow another request to be
// approved.
//
// bufferSize may be specified to count memory buffer sizes, and must be
// matched with calls to RateLimit.Release to mark the buffers as released.
func (e Execution) Finish(bufferSize int) {
	e <- bufferSize
}

// Call Release when finished with a buffer recorded with Finish.
func (r *RateLimit) Release(bufferSize int) {
	r.released <- bufferSize
}

// Stop the background goroutine
func (r *RateLimit) Stop() {
	close(r.stop)
}

func (r *RateLimit) goFunc(maxExecs int, maxMem int64) {
	var curExecs int
	var curMemory int64

	for {
		var requests chan struct{}
		if curExecs < maxExecs && curMemory < maxMem {
			requests = r.requests
		}

		select {
		case requests <- struct{}{}:
			curExecs++
		case amount := <-r.finished:
			curExecs--
			curMemory += int64(amount)
			if curExecs < 0 {
				panic("curExecs < 0")
			}
		case amount := <-r.released:
			curMemory -= int64(amount)
		case <-r.stop:
			return
		}
	}
}
