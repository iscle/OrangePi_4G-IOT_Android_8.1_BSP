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

// soong_javac_wrapper expects a javac command line and argments, executes
// it, and produces an ANSI colorized version of the output on stdout.
//
// It also hides the unhelpful and unhideable "warning there is a warning"
// messages.
package main

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"os/exec"
	"regexp"
	"syscall"
)

// Regular expressions are based on
// https://chromium.googlesource.com/chromium/src/+/master/build/android/gyp/javac.py
// Colors are based on clang's output
var (
	filelinePrefix = `^([-.\w/\\]+.java:[0-9]+: )`
	warningRe      = regexp.MustCompile(filelinePrefix + `?(warning:) .*$`)
	errorRe        = regexp.MustCompile(filelinePrefix + `(.*?:) .*$`)
	markerRe       = regexp.MustCompile(`()\s*(\^)\s*$`)

	escape  = "\x1b"
	reset   = escape + "[0m"
	bold    = escape + "[1m"
	red     = escape + "[31m"
	green   = escape + "[32m"
	magenta = escape + "[35m"
)

func main() {
	exitCode, err := Main(os.Stdout, os.Args[0], os.Args[1:])
	if err != nil {
		fmt.Fprintln(os.Stderr, err.Error())
	}
	os.Exit(exitCode)
}

func Main(out io.Writer, name string, args []string) (int, error) {
	if len(args) < 1 {
		return 1, fmt.Errorf("usage: %s javac ...", name)
	}

	pr, pw, err := os.Pipe()
	if err != nil {
		return 1, fmt.Errorf("creating output pipe: %s", err)
	}

	cmd := exec.Command(args[0], args[1:]...)
	cmd.Stdin = os.Stdin
	cmd.Stdout = pw
	cmd.Stderr = pw
	err = cmd.Start()
	if err != nil {
		return 1, fmt.Errorf("starting subprocess: %s", err)
	}

	pw.Close()

	// Process subprocess stdout asynchronously
	errCh := make(chan error)
	go func() {
		errCh <- process(pr, out)
	}()

	// Wait for subprocess to finish
	cmdErr := cmd.Wait()

	// Wait for asynchronous stdout processing to finish
	err = <-errCh

	// Check for subprocess exit code
	if cmdErr != nil {
		if exitErr, ok := cmdErr.(*exec.ExitError); ok {
			if status, ok := exitErr.Sys().(syscall.WaitStatus); ok {
				if status.Exited() {
					return status.ExitStatus(), nil
				} else if status.Signaled() {
					exitCode := 128 + int(status.Signal())
					return exitCode, nil
				} else {
					return 1, exitErr
				}
			} else {
				return 1, nil
			}
		}
	}

	if err != nil {
		return 1, err
	}

	return 0, nil
}

func process(r io.Reader, w io.Writer) error {
	scanner := bufio.NewScanner(r)
	// Some javac wrappers output the entire list of java files being
	// compiled on a single line, which can be very large, set the maximum
	// buffer size to 2MB.
	scanner.Buffer(nil, 2*1024*1024)
	for scanner.Scan() {
		processLine(w, scanner.Text())
	}
	err := scanner.Err()
	if err != nil {
		return fmt.Errorf("scanning input: %s", err)
	}
	return nil
}

func processLine(w io.Writer, line string) {
	for _, f := range filters {
		if f.MatchString(line) {
			return
		}
	}
	for _, p := range colorPatterns {
		var matched bool
		if line, matched = applyColor(line, p.color, p.re); matched {
			break
		}
	}
	fmt.Fprintln(w, line)
}

// If line matches re, make it bold and apply color to the first submatch
// Returns line, modified if it matched, and true if it matched.
func applyColor(line, color string, re *regexp.Regexp) (string, bool) {
	if m := re.FindStringSubmatchIndex(line); m != nil {
		tagStart, tagEnd := m[4], m[5]
		line = bold + line[:tagStart] +
			color + line[tagStart:tagEnd] + reset + bold +
			line[tagEnd:] + reset
		return line, true
	}
	return line, false
}

var colorPatterns = []struct {
	re    *regexp.Regexp
	color string
}{
	{warningRe, magenta},
	{errorRe, red},
	{markerRe, green},
}

var filters = []*regexp.Regexp{
	regexp.MustCompile(`Note: (Some input files|.*\.java) uses? or overrides? a deprecated API.`),
	regexp.MustCompile(`Note: Recompile with -Xlint:deprecation for details.`),
	regexp.MustCompile(`Note: (Some input files|.*\.java) uses? unchecked or unsafe operations.`),
	regexp.MustCompile(`Note: Recompile with -Xlint:unchecked for details.`),
}
