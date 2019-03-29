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

// This file provides a command-line interface to bpfix

// TODO(jeffrygaston) should this file be consolidated with bpfmt.go?

package main

import (
	"bytes"
	"flag"
	"fmt"
	"io"
	"io/ioutil"
	"os"
	"os/exec"
	"path/filepath"

	"github.com/google/blueprint/parser"

	"android/soong/bpfix/bpfix"
)

var (
	// main operation modes
	list   = flag.Bool("l", false, "list files whose formatting differs from bpfmt's")
	write  = flag.Bool("w", false, "write result to (source) file instead of stdout")
	doDiff = flag.Bool("d", false, "display diffs instead of rewriting files")
)

var (
	exitCode = 0
)

func report(err error) {
	fmt.Fprintln(os.Stderr, err)
	exitCode = 2
}

func openAndProcess(filename string, out io.Writer, fixRequest bpfix.FixRequest) error {
	f, err := os.Open(filename)
	if err != nil {
		return err
	}
	defer f.Close()
	return processFile(filename, f, out, fixRequest)
}

// If in == nil, the source is the contents of the file with the given filename.
func processFile(filename string, in io.Reader, out io.Writer, fixRequest bpfix.FixRequest) error {
	// load the input file
	src, err := ioutil.ReadAll(in)
	if err != nil {
		return err
	}
	r := bytes.NewBuffer(src)
	file, errs := parser.Parse(filename, r, parser.NewScope(nil))
	if len(errs) > 0 {
		for _, err := range errs {
			fmt.Fprintln(os.Stderr, err)
		}
		return fmt.Errorf("%d parsing errors", len(errs))
	}

	// compute and apply any requested fixes
	fixed, err := bpfix.FixTree(file, fixRequest)
	if err != nil {
		return err
	}

	// output the results
	res, err := parser.Print(fixed)
	if err != nil {
		return err
	}
	if !bytes.Equal(src, res) {
		// contents have changed
		if *list {
			fmt.Fprintln(out, filename)
		}
		if *write {
			err = ioutil.WriteFile(filename, res, 0644)
			if err != nil {
				return err
			}
		}
		if *doDiff {
			data, err := diff(src, res)
			if err != nil {
				return fmt.Errorf("computing diff: %s", err)
			}
			fmt.Printf("diff %s bpfix/%s\n", filename, filename)
			out.Write(data)
		}
	}
	if !*list && !*write && !*doDiff {
		_, err = out.Write(res)
	}
	return err
}

func makeFileVisitor(fixRequest bpfix.FixRequest) func(string, os.FileInfo, error) error {
	return func(path string, f os.FileInfo, err error) error {
		if err == nil && (f.Name() == "Blueprints" || f.Name() == "Android.bp") {
			err = openAndProcess(path, os.Stdout, fixRequest)
		}
		if err != nil {
			report(err)
		}
		return nil
	}
}

func walkDir(path string, fixRequest bpfix.FixRequest) {
	filepath.Walk(path, makeFileVisitor(fixRequest))
}

func main() {
	flag.Parse()

	fixRequest := bpfix.NewFixRequest().AddAll()

	if flag.NArg() == 0 {
		if *write {
			fmt.Fprintln(os.Stderr, "error: cannot use -w with standard input")
			exitCode = 2
			return
		}
		if err := processFile("<standard input>", os.Stdin, os.Stdout, fixRequest); err != nil {
			report(err)
		}
		return
	}

	for i := 0; i < flag.NArg(); i++ {
		path := flag.Arg(i)
		switch dir, err := os.Stat(path); {
		case err != nil:
			report(err)
		case dir.IsDir():
			walkDir(path, fixRequest)
		default:
			if err := openAndProcess(path, os.Stdout, fixRequest); err != nil {
				report(err)
			}
		}
	}
}

func diff(b1, b2 []byte) (data []byte, err error) {
	f1, err := ioutil.TempFile("", "bpfix")
	if err != nil {
		return
	}
	defer os.Remove(f1.Name())
	defer f1.Close()

	f2, err := ioutil.TempFile("", "bpfix")
	if err != nil {
		return
	}
	defer os.Remove(f2.Name())
	defer f2.Close()

	f1.Write(b1)
	f2.Write(b2)

	data, err = exec.Command("diff", "-u", f1.Name(), f2.Name()).CombinedOutput()
	if len(data) > 0 {
		// diff exits with a non-zero status when the files don't match.
		// Ignore that failure as long as we get output.
		err = nil
	}
	return

}
