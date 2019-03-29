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

package logger

import (
	"bytes"
	"fmt"
	"io"
	"io/ioutil"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"sort"
	"syscall"
	"testing"
)

func TestCreateFileWithRotation(t *testing.T) {
	dir, err := ioutil.TempDir("", "test-rotation")
	if err != nil {
		t.Fatalf("Failed to get TempDir: %v", err)
	}
	defer os.RemoveAll(dir)

	file := filepath.Join(dir, "build.log")

	writeFile := func(name string, data string) {
		f, err := CreateFileWithRotation(name, 3)
		if err != nil {
			t.Fatalf("Failed to create file: %v", err)
		}
		if n, err := io.WriteString(f, data); err == nil && n < len(data) {
			t.Fatalf("Short write")
		} else if err != nil {
			t.Fatalf("Failed to write: %v", err)
		}
		if err := f.Close(); err != nil {
			t.Fatalf("Failed to close: %v", err)
		}
	}

	writeFile(file, "a")
	writeFile(file, "b")
	writeFile(file, "c")
	writeFile(file, "d")
	writeFile(file, "e")

	d, err := os.Open(dir)
	if err != nil {
		t.Fatalf("Failed to open dir: %v", err)
	}
	names, err := d.Readdirnames(0)
	if err != nil {
		t.Fatalf("Failed to read dir: %v", err)
	}
	sort.Strings(names)
	expected := []string{"build.1.log", "build.2.log", "build.3.log", "build.log"}
	if !reflect.DeepEqual(names, expected) {
		t.Errorf("File list does not match.")
		t.Errorf("     got: %v", names)
		t.Errorf("expected: %v", expected)
		t.FailNow()
	}

	expectFileContents := func(name, expected string) {
		data, err := ioutil.ReadFile(filepath.Join(dir, name))
		if err != nil {
			t.Errorf("Error reading file: %v", err)
			return
		}
		str := string(data)
		if str != expected {
			t.Errorf("Contents of %v does not match.", name)
			t.Errorf("     got: %v", data)
			t.Errorf("expected: %v", expected)
		}
	}

	expectFileContents("build.log", "e")
	expectFileContents("build.1.log", "d")
	expectFileContents("build.2.log", "c")
	expectFileContents("build.3.log", "b")
}

func TestPanic(t *testing.T) {
	if os.Getenv("ACTUALLY_PANIC") == "1" {
		panicValue := "foo"
		log := New(&bytes.Buffer{})

		defer func() {
			p := recover()

			if p == panicValue {
				os.Exit(42)
			} else {
				fmt.Fprintln(os.Stderr, "Expected %q, got %v", panicValue, p)
				os.Exit(3)
			}
		}()
		defer log.Cleanup()

		log.Panic(panicValue)
		os.Exit(2)
		return
	}

	// Run this in an external process so that we don't pollute stderr
	cmd := exec.Command(os.Args[0], "-test.run=TestPanic")
	cmd.Env = append(os.Environ(), "ACTUALLY_PANIC=1")
	err := cmd.Run()
	if e, ok := err.(*exec.ExitError); ok && e.Sys().(syscall.WaitStatus).ExitStatus() == 42 {
		return
	}
	t.Errorf("Expected process to exit with status 42, got %v", err)
}

func TestFatal(t *testing.T) {
	if os.Getenv("ACTUALLY_FATAL") == "1" {
		log := New(&bytes.Buffer{})
		defer func() {
			// Shouldn't get here
			os.Exit(3)
		}()
		defer log.Cleanup()
		log.Fatal("Test")
		os.Exit(0)
		return
	}

	cmd := exec.Command(os.Args[0], "-test.run=TestFatal")
	cmd.Env = append(os.Environ(), "ACTUALLY_FATAL=1")
	err := cmd.Run()
	if e, ok := err.(*exec.ExitError); ok && e.Sys().(syscall.WaitStatus).ExitStatus() == 1 {
		return
	}
	t.Errorf("Expected process to exit with status 1, got %v", err)
}

func TestNonFatal(t *testing.T) {
	if os.Getenv("ACTUAL_TEST") == "1" {
		log := New(&bytes.Buffer{})
		defer log.Cleanup()
		log.Println("Test")
		return
	}

	cmd := exec.Command(os.Args[0], "-test.run=TestNonFatal")
	cmd.Env = append(os.Environ(), "ACTUAL_TEST=1")
	err := cmd.Run()
	if e, ok := err.(*exec.ExitError); ok || (ok && !e.Success()) {
		t.Errorf("Expected process to exit cleanly, got %v", err)
	}
}

func TestRecoverFatal(t *testing.T) {
	log := New(&bytes.Buffer{})
	defer func() {
		if p := recover(); p != nil {
			t.Errorf("Unexpected panic: %#v", p)
		}
	}()
	defer Recover(func(err error) {
		if err.Error() != "Test" {
			t.Errorf("Expected %q, but got %q", "Test", err.Error())
		}
	})
	log.Fatal("Test")
	t.Errorf("Should not get here")
}

func TestRecoverNonFatal(t *testing.T) {
	log := New(&bytes.Buffer{})
	defer func() {
		if p := recover(); p == nil {
			t.Errorf("Panic not thrown")
		} else if p != "Test" {
			t.Errorf("Expected %q, but got %#v", "Test", p)
		}
	}()
	defer Recover(func(err error) {
		t.Errorf("Recover function should not be called")
	})
	log.Panic("Test")
	t.Errorf("Should not get here")
}
