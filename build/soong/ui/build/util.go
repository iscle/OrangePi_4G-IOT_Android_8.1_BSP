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
	"bytes"
	"io"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"unsafe"
)

// indexList finds the index of a string in a []string
func indexList(s string, list []string) int {
	for i, l := range list {
		if l == s {
			return i
		}
	}

	return -1
}

// inList determines whether a string is in a []string
func inList(s string, list []string) bool {
	return indexList(s, list) != -1
}

// ensureDirectoriesExist is a shortcut to os.MkdirAll, sending errors to the ctx logger.
func ensureDirectoriesExist(ctx Context, dirs ...string) {
	for _, dir := range dirs {
		err := os.MkdirAll(dir, 0777)
		if err != nil {
			ctx.Fatalf("Error creating %s: %q\n", dir, err)
		}
	}
}

// ensureEmptyDirectoriesExist ensures that the given directories exist and are empty
func ensureEmptyDirectoriesExist(ctx Context, dirs ...string) {
	// remove all the directories
	for _, dir := range dirs {
		err := os.RemoveAll(dir)
		if err != nil {
			ctx.Fatalf("Error removing %s: %q\n", dir, err)
		}
	}
	// recreate all the directories
	ensureDirectoriesExist(ctx, dirs...)
}

// ensureEmptyFileExists ensures that the containing directory exists, and the
// specified file exists. If it doesn't exist, it will write an empty file.
func ensureEmptyFileExists(ctx Context, file string) {
	ensureDirectoriesExist(ctx, filepath.Dir(file))
	if _, err := os.Stat(file); os.IsNotExist(err) {
		f, err := os.Create(file)
		if err != nil {
			ctx.Fatalf("Error creating %s: %q\n", file, err)
		}
		f.Close()
	} else if err != nil {
		ctx.Fatalf("Error checking %s: %q\n", file, err)
	}
}

// singleUnquote is similar to strconv.Unquote, but can handle multi-character strings inside single quotes.
func singleUnquote(str string) (string, bool) {
	if len(str) < 2 || str[0] != '\'' || str[len(str)-1] != '\'' {
		return "", false
	}
	return str[1 : len(str)-1], true
}

// decodeKeyValue decodes a key=value string
func decodeKeyValue(str string) (string, string, bool) {
	idx := strings.IndexRune(str, '=')
	if idx == -1 {
		return "", "", false
	}
	return str[:idx], str[idx+1:], true
}

func isTerminal(w io.Writer) bool {
	if f, ok := w.(*os.File); ok {
		var termios syscall.Termios
		_, _, err := syscall.Syscall6(syscall.SYS_IOCTL, f.Fd(),
			ioctlGetTermios, uintptr(unsafe.Pointer(&termios)),
			0, 0, 0)
		return err == 0
	}
	return false
}

func termWidth(w io.Writer) (int, bool) {
	if f, ok := w.(*os.File); ok {
		var winsize struct {
			ws_row, ws_column    uint16
			ws_xpixel, ws_ypixel uint16
		}
		_, _, err := syscall.Syscall6(syscall.SYS_IOCTL, f.Fd(),
			syscall.TIOCGWINSZ, uintptr(unsafe.Pointer(&winsize)),
			0, 0, 0)
		return int(winsize.ws_column), err == 0
	}
	return 0, false
}

// stripAnsiEscapes strips ANSI control codes from a byte array in place.
func stripAnsiEscapes(input []byte) []byte {
	// read represents the remaining part of input that needs to be processed.
	read := input
	// write represents where we should be writing in input.
	// It will share the same backing store as input so that we make our modifications
	// in place.
	write := input

	// advance will copy count bytes from read to write and advance those slices
	advance := func(write, read []byte, count int) ([]byte, []byte) {
		copy(write, read[:count])
		return write[count:], read[count:]
	}

	for {
		// Find the next escape sequence
		i := bytes.IndexByte(read, 0x1b)
		// If it isn't found, or if there isn't room for <ESC>[, finish
		if i == -1 || i+1 >= len(read) {
			copy(write, read)
			break
		}

		// Not a CSI code, continue searching
		if read[i+1] != '[' {
			write, read = advance(write, read, i+1)
			continue
		}

		// Found a CSI code, advance up to the <ESC>
		write, read = advance(write, read, i)

		// Find the end of the CSI code
		i = bytes.IndexFunc(read, func(r rune) bool {
			return (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z')
		})
		if i == -1 {
			// We didn't find the end of the code, just remove the rest
			i = len(read) - 1
		}

		// Strip off the end marker too
		i = i + 1

		// Skip the reader forward and reduce final length by that amount
		read = read[i:]
		input = input[:len(input)-i]
	}

	return input
}
