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
	"os/exec"
)

// Cmd is a wrapper of os/exec.Cmd that integrates with the build context for
// logging, the config's Environment for simpler environment modification, and
// implements hooks for sandboxing
type Cmd struct {
	*exec.Cmd

	Environment *Environment
	Sandbox     Sandbox

	ctx    Context
	config Config
	name   string
}

func Command(ctx Context, config Config, name string, executable string, args ...string) *Cmd {
	ret := &Cmd{
		Cmd:         exec.CommandContext(ctx.Context, executable, args...),
		Environment: config.Environment().Copy(),
		Sandbox:     noSandbox,

		ctx:    ctx,
		config: config,
		name:   name,
	}

	return ret
}

func (c *Cmd) prepare() {
	if c.Env == nil {
		c.Env = c.Environment.Environ()
	}
	if c.sandboxSupported() {
		c.wrapSandbox()
	}

	c.ctx.Verboseln(c.Path, c.Args)
}

func (c *Cmd) Start() error {
	c.prepare()
	return c.Cmd.Start()
}

func (c *Cmd) Run() error {
	c.prepare()
	err := c.Cmd.Run()
	return err
}

func (c *Cmd) Output() ([]byte, error) {
	c.prepare()
	bytes, err := c.Cmd.Output()
	return bytes, err
}

func (c *Cmd) CombinedOutput() ([]byte, error) {
	c.prepare()
	bytes, err := c.Cmd.CombinedOutput()
	return bytes, err
}

// StartOrFatal is equivalent to Start, but handles the error with a call to ctx.Fatal
func (c *Cmd) StartOrFatal() {
	if err := c.Start(); err != nil {
		c.ctx.Fatalf("Failed to run %s: %v", c.name, err)
	}
}

func (c *Cmd) reportError(err error) {
	if err == nil {
		return
	}
	if e, ok := err.(*exec.ExitError); ok {
		c.ctx.Fatalf("%s failed with: %v", c.name, e.ProcessState.String())
	} else {
		c.ctx.Fatalf("Failed to run %s: %v", c.name, err)
	}
}

// RunOrFatal is equivalent to Run, but handles the error with a call to ctx.Fatal
func (c *Cmd) RunOrFatal() {
	c.reportError(c.Run())
}

// WaitOrFatal is equivalent to Wait, but handles the error with a call to ctx.Fatal
func (c *Cmd) WaitOrFatal() {
	c.reportError(c.Wait())
}

// OutputOrFatal is equivalent to Output, but handles the error with a call to ctx.Fatal
func (c *Cmd) OutputOrFatal() []byte {
	ret, err := c.Output()
	c.reportError(err)
	return ret
}

// CombinedOutputOrFatal is equivalent to CombinedOutput, but handles the error with
// a call to ctx.Fatal
func (c *Cmd) CombinedOutputOrFatal() []byte {
	ret, err := c.CombinedOutput()
	c.reportError(err)
	return ret
}
