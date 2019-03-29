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

package build

import (
	"os"
	"os/signal"
	"runtime/debug"
	"syscall"

	"android/soong/ui/logger"
	"time"
)

// SetupSignals sets up signal handling to ensure all of our subprocesses are killed and that
// our log/trace buffers are flushed to disk.
//
// All of our subprocesses are in the same process group, so they'll receive a SIGINT at the
// same time we do. Most of the time this means we just need to ignore the signal and we'll
// just see errors from all of our subprocesses. But in case that fails, when we get a signal:
//
//   1. Wait two seconds to exit normally.
//   2. Call cancel() which is normally the cancellation of a Context. This will send a SIGKILL
//      to any subprocesses attached to that context.
//   3. Wait two seconds to exit normally.
//   4. Call cleanup() to close the log/trace buffers, then panic.
//   5. If another two seconds passes (if cleanup got stuck, etc), then panic.
//
func SetupSignals(log logger.Logger, cancel, cleanup func()) {
	signals := make(chan os.Signal, 5)
	signal.Notify(signals, os.Interrupt, syscall.SIGHUP, syscall.SIGQUIT, syscall.SIGTERM)
	go handleSignals(signals, log, cancel, cleanup)
}

func handleSignals(signals chan os.Signal, log logger.Logger, cancel, cleanup func()) {
	var timeouts int
	var timeout <-chan time.Time

	handleTimeout := func() {
		timeouts += 1
		switch timeouts {
		case 1:
			// Things didn't exit cleanly, cancel our ctx (SIGKILL to subprocesses)
			// Do this asynchronously to ensure it won't block and prevent us from
			// taking more drastic measures.
			log.Println("Still alive, killing subprocesses...")
			go cancel()
		case 2:
			// Cancel didn't work. Try to run cleanup manually, then we'll panic
			// at the next timer whether it finished or not.
			log.Println("Still alive, cleaning up...")

			// Get all stacktraces to see what was stuck
			debug.SetTraceback("all")

			go func() {
				defer log.Panicln("Timed out exiting...")
				cleanup()
			}()
		default:
			// In case cleanup() deadlocks, the next tick will panic.
			log.Panicln("Got signal, but timed out exiting...")
		}
	}

	for {
		select {
		case s := <-signals:
			log.Println("Got signal:", s)

			// Another signal triggers our next timeout handler early
			if timeout != nil {
				handleTimeout()
			}

			// Wait 2 seconds for everything to exit cleanly.
			timeout = time.Tick(time.Second * 2)
		case <-timeout:
			handleTimeout()
		}
	}
}
