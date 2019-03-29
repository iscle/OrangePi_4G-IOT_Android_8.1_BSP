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
	"context"
	"io"
	"os"
	"time"

	"android/soong/ui/logger"
	"android/soong/ui/tracer"
)

type StdioInterface interface {
	Stdin() io.Reader
	Stdout() io.Writer
	Stderr() io.Writer
}

type StdioImpl struct{}

func (StdioImpl) Stdin() io.Reader  { return os.Stdin }
func (StdioImpl) Stdout() io.Writer { return os.Stdout }
func (StdioImpl) Stderr() io.Writer { return os.Stderr }

var _ StdioInterface = StdioImpl{}

type customStdio struct {
	stdin  io.Reader
	stdout io.Writer
	stderr io.Writer
}

func NewCustomStdio(stdin io.Reader, stdout, stderr io.Writer) StdioInterface {
	return customStdio{stdin, stdout, stderr}
}

func (c customStdio) Stdin() io.Reader  { return c.stdin }
func (c customStdio) Stdout() io.Writer { return c.stdout }
func (c customStdio) Stderr() io.Writer { return c.stderr }

var _ StdioInterface = customStdio{}

// Context combines a context.Context, logger.Logger, and StdIO redirection.
// These all are agnostic of the current build, and may be used for multiple
// builds, while the Config objects contain per-build information.
type Context struct{ *ContextImpl }
type ContextImpl struct {
	context.Context
	logger.Logger

	StdioInterface

	Thread tracer.Thread
	Tracer tracer.Tracer
}

// BeginTrace starts a new Duration Event.
func (c ContextImpl) BeginTrace(name string) {
	if c.Tracer != nil {
		c.Tracer.Begin(name, c.Thread)
	}
}

// EndTrace finishes the last Duration Event.
func (c ContextImpl) EndTrace() {
	if c.Tracer != nil {
		c.Tracer.End(c.Thread)
	}
}

// CompleteTrace writes a trace with a beginning and end times.
func (c ContextImpl) CompleteTrace(name string, begin, end uint64) {
	if c.Tracer != nil {
		c.Tracer.Complete(name, c.Thread, begin, end)
	}
}

// ImportNinjaLog imports a .ninja_log file into the tracer.
func (c ContextImpl) ImportNinjaLog(filename string, startOffset time.Time) {
	if c.Tracer != nil {
		c.Tracer.ImportNinjaLog(c.Thread, filename, startOffset)
	}
}

func (c ContextImpl) IsTerminal() bool {
	if term, ok := os.LookupEnv("TERM"); ok {
		return term != "dumb" && isTerminal(c.Stdout()) && isTerminal(c.Stderr())
	}
	return false
}

func (c ContextImpl) TermWidth() (int, bool) {
	return termWidth(c.Stdout())
}
