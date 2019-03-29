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

// Package logger implements a logging package designed for command line
// utilities.  It uses the standard 'log' package and function, but splits
// output between stderr and a rotating log file.
//
// In addition to the standard logger functions, Verbose[f|ln] calls only go to
// the log file by default, unless SetVerbose(true) has been called.
//
// The log file also includes extended date/time/source information, which are
// omitted from the stderr output for better readability.
//
// In order to better handle resource cleanup after a Fatal error, the Fatal
// functions panic instead of calling os.Exit(). To actually do the cleanup,
// and prevent the printing of the panic, call defer logger.Cleanup() at the
// beginning of your main function.
package logger

import (
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"os"
	"path/filepath"
	"strconv"
	"sync"
)

type Logger interface {
	// Print* prints to both stderr and the file log.
	// Arguments to Print are handled in the manner of fmt.Print.
	Print(v ...interface{})
	// Arguments to Printf are handled in the manner of fmt.Printf
	Printf(format string, v ...interface{})
	// Arguments to Println are handled in the manner of fmt.Println
	Println(v ...interface{})

	// Verbose* is equivalent to Print*, but skips stderr unless the
	// logger has been configured in verbose mode.
	Verbose(v ...interface{})
	Verbosef(format string, v ...interface{})
	Verboseln(v ...interface{})

	// Fatal* is equivalent to Print* followed by a call to panic that
	// can be converted to an error using Recover, or will be converted
	// to a call to os.Exit(1) with a deferred call to Cleanup()
	Fatal(v ...interface{})
	Fatalf(format string, v ...interface{})
	Fatalln(v ...interface{})

	// Panic is equivalent to Print* followed by a call to panic.
	Panic(v ...interface{})
	Panicf(format string, v ...interface{})
	Panicln(v ...interface{})

	// Output writes the string to both stderr and the file log.
	Output(calldepth int, str string) error
}

// fatalLog is the type used when Fatal[f|ln]
type fatalLog error

func fileRotation(from, baseName, ext string, cur, max int) error {
	newName := baseName + "." + strconv.Itoa(cur) + ext

	if _, err := os.Lstat(newName); err == nil {
		if cur+1 <= max {
			fileRotation(newName, baseName, ext, cur+1, max)
		}
	}

	if err := os.Rename(from, newName); err != nil {
		return fmt.Errorf("Failed to rotate", from, "to", newName, ".", err)
	}
	return nil
}

// CreateFileWithRotation returns a new os.File using os.Create, renaming any
// existing files to <filename>.#.<ext>, keeping up to maxCount files.
// <filename>.1.<ext> is the most recent backup, <filename>.2.<ext> is the
// second most recent backup, etc.
//
// TODO: This function is not guaranteed to be atomic, if there are multiple
// users attempting to do the same operation, the result is undefined.
func CreateFileWithRotation(filename string, maxCount int) (*os.File, error) {
	if _, err := os.Lstat(filename); err == nil {
		ext := filepath.Ext(filename)
		basename := filename[:len(filename)-len(ext)]
		if err = fileRotation(filename, basename, ext, 1, maxCount); err != nil {
			return nil, err
		}
	}

	return os.Create(filename)
}

// Recover can be used with defer in a GoRoutine to convert a Fatal panics to
// an error that can be handled.
func Recover(fn func(err error)) {
	p := recover()

	if p == nil {
		return
	} else if log, ok := p.(fatalLog); ok {
		fn(error(log))
	} else {
		panic(p)
	}
}

type stdLogger struct {
	stderr  *log.Logger
	verbose bool

	fileLogger *log.Logger
	mutex      sync.Mutex
	file       *os.File
}

var _ Logger = &stdLogger{}

// New creates a new Logger. The out variable sets the destination, commonly
// os.Stderr, but it may be a buffer for tests, or a separate log file if
// the user doesn't need to see the output.
func New(out io.Writer) *stdLogger {
	return &stdLogger{
		stderr:     log.New(out, "", log.Ltime),
		fileLogger: log.New(ioutil.Discard, "", log.Ldate|log.Lmicroseconds|log.Llongfile),
	}
}

// SetVerbose controls whether Verbose[f|ln] logs to stderr as well as the
// file-backed log.
func (s *stdLogger) SetVerbose(v bool) {
	s.verbose = v
}

// SetOutput controls where the file-backed log will be saved. It will keep
// some number of backups of old log files.
func (s *stdLogger) SetOutput(path string) {
	if f, err := CreateFileWithRotation(path, 5); err == nil {
		s.mutex.Lock()
		defer s.mutex.Unlock()

		if s.file != nil {
			s.file.Close()
		}
		s.file = f
		s.fileLogger.SetOutput(f)
	} else {
		s.Fatal(err.Error())
	}
}

// Close disables logging to the file and closes the file handle.
func (s *stdLogger) Close() {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	if s.file != nil {
		s.fileLogger.SetOutput(ioutil.Discard)
		s.file.Close()
		s.file = nil
	}
}

// Cleanup should be used with defer in your main function. It will close the
// log file and convert any Fatal panics back to os.Exit(1)
func (s *stdLogger) Cleanup() {
	fatal := false
	p := recover()

	if _, ok := p.(fatalLog); ok {
		fatal = true
		p = nil
	} else if p != nil {
		s.Println(p)
	}

	s.Close()

	if p != nil {
		panic(p)
	} else if fatal {
		os.Exit(1)
	}
}

// Output writes string to both stderr and the file log.
func (s *stdLogger) Output(calldepth int, str string) error {
	s.stderr.Output(calldepth+1, str)
	return s.fileLogger.Output(calldepth+1, str)
}

// VerboseOutput is equivalent to Output, but only goes to the file log
// unless SetVerbose(true) has been called.
func (s *stdLogger) VerboseOutput(calldepth int, str string) error {
	if s.verbose {
		s.stderr.Output(calldepth+1, str)
	}
	return s.fileLogger.Output(calldepth+1, str)
}

// Print prints to both stderr and the file log.
// Arguments are handled in the manner of fmt.Print.
func (s *stdLogger) Print(v ...interface{}) {
	output := fmt.Sprint(v...)
	s.Output(2, output)
}

// Printf prints to both stderr and the file log.
// Arguments are handled in the manner of fmt.Printf.
func (s *stdLogger) Printf(format string, v ...interface{}) {
	output := fmt.Sprintf(format, v...)
	s.Output(2, output)
}

// Println prints to both stderr and the file log.
// Arguments are handled in the manner of fmt.Println.
func (s *stdLogger) Println(v ...interface{}) {
	output := fmt.Sprintln(v...)
	s.Output(2, output)
}

// Verbose is equivalent to Print, but only goes to the file log unless
// SetVerbose(true) has been called.
func (s *stdLogger) Verbose(v ...interface{}) {
	output := fmt.Sprint(v...)
	s.VerboseOutput(2, output)
}

// Verbosef is equivalent to Printf, but only goes to the file log unless
// SetVerbose(true) has been called.
func (s *stdLogger) Verbosef(format string, v ...interface{}) {
	output := fmt.Sprintf(format, v...)
	s.VerboseOutput(2, output)
}

// Verboseln is equivalent to Println, but only goes to the file log unless
// SetVerbose(true) has been called.
func (s *stdLogger) Verboseln(v ...interface{}) {
	output := fmt.Sprintln(v...)
	s.VerboseOutput(2, output)
}

// Fatal is equivalent to Print() followed by a call to panic() that
// Cleanup will convert to a os.Exit(1).
func (s *stdLogger) Fatal(v ...interface{}) {
	output := fmt.Sprint(v...)
	s.Output(2, output)
	panic(fatalLog(errors.New(output)))
}

// Fatalf is equivalent to Printf() followed by a call to panic() that
// Cleanup will convert to a os.Exit(1).
func (s *stdLogger) Fatalf(format string, v ...interface{}) {
	output := fmt.Sprintf(format, v...)
	s.Output(2, output)
	panic(fatalLog(errors.New(output)))
}

// Fatalln is equivalent to Println() followed by a call to panic() that
// Cleanup will convert to a os.Exit(1).
func (s *stdLogger) Fatalln(v ...interface{}) {
	output := fmt.Sprintln(v...)
	s.Output(2, output)
	panic(fatalLog(errors.New(output)))
}

// Panic is equivalent to Print() followed by a call to panic().
func (s *stdLogger) Panic(v ...interface{}) {
	output := fmt.Sprint(v...)
	s.Output(2, output)
	panic(output)
}

// Panicf is equivalent to Printf() followed by a call to panic().
func (s *stdLogger) Panicf(format string, v ...interface{}) {
	output := fmt.Sprintf(format, v...)
	s.Output(2, output)
	panic(output)
}

// Panicln is equivalent to Println() followed by a call to panic().
func (s *stdLogger) Panicln(v ...interface{}) {
	output := fmt.Sprintln(v...)
	s.Output(2, output)
	panic(output)
}
