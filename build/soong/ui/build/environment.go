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
	"bufio"
	"fmt"
	"io"
	"os"
	"strings"
)

// Environment adds a number of useful manipulation functions to the list of
// strings returned by os.Environ() and used in exec.Cmd.Env.
type Environment []string

// OsEnvironment wraps the current environment returned by os.Environ()
func OsEnvironment() *Environment {
	env := Environment(os.Environ())
	return &env
}

// Get returns the value associated with the key, and whether it exists.
// It's equivalent to the os.LookupEnv function, but with this copy of the
// Environment.
func (e *Environment) Get(key string) (string, bool) {
	for _, env := range *e {
		if k, v, ok := decodeKeyValue(env); ok && k == key {
			return v, true
		}
	}
	return "", false
}

// Set sets the value associated with the key, overwriting the current value
// if it exists.
func (e *Environment) Set(key, value string) {
	e.Unset(key)
	*e = append(*e, key+"="+value)
}

// Unset removes the specified keys from the Environment.
func (e *Environment) Unset(keys ...string) {
	out := (*e)[:0]
	for _, env := range *e {
		if key, _, ok := decodeKeyValue(env); ok && inList(key, keys) {
			continue
		}
		out = append(out, env)
	}
	*e = out
}

// Environ returns the []string required for exec.Cmd.Env
func (e *Environment) Environ() []string {
	return []string(*e)
}

// Copy returns a copy of the Environment so that independent changes may be made.
func (e *Environment) Copy() *Environment {
	ret := Environment(make([]string, len(*e)))
	for i, v := range *e {
		ret[i] = v
	}
	return &ret
}

// IsTrue returns whether an environment variable is set to a positive value (1,y,yes,on,true)
func (e *Environment) IsEnvTrue(key string) bool {
	if value, ok := e.Get(key); ok {
		return value == "1" || value == "y" || value == "yes" || value == "on" || value == "true"
	}
	return false
}

// IsFalse returns whether an environment variable is set to a negative value (0,n,no,off,false)
func (e *Environment) IsFalse(key string) bool {
	if value, ok := e.Get(key); ok {
		return value == "0" || value == "n" || value == "no" || value == "off" || value == "false"
	}
	return false
}

// AppendFromKati reads a shell script written by Kati that exports or unsets
// environment variables, and applies those to the local Environment.
func (e *Environment) AppendFromKati(filename string) error {
	file, err := os.Open(filename)
	if err != nil {
		return err
	}
	defer file.Close()

	return e.appendFromKati(file)
}

func (e *Environment) appendFromKati(reader io.Reader) error {
	scanner := bufio.NewScanner(reader)
	for scanner.Scan() {
		text := strings.TrimSpace(scanner.Text())

		if len(text) == 0 || text[0] == '#' {
			continue
		}

		cmd := strings.SplitN(text, " ", 2)
		if len(cmd) != 2 {
			return fmt.Errorf("Unknown kati environment line: %q", text)
		}

		if cmd[0] == "unset" {
			str, ok := singleUnquote(cmd[1])
			if !ok {
				fmt.Errorf("Failed to unquote kati line: %q", text)
			}
			e.Unset(str)
		} else if cmd[0] == "export" {
			key, value, ok := decodeKeyValue(cmd[1])
			if !ok {
				return fmt.Errorf("Failed to parse export: %v", cmd)
			}

			key, ok = singleUnquote(key)
			if !ok {
				return fmt.Errorf("Failed to unquote kati line: %q", text)
			}
			value, ok = singleUnquote(value)
			if !ok {
				return fmt.Errorf("Failed to unquote kati line: %q", text)
			}

			e.Set(key, value)
		} else {
			return fmt.Errorf("Unknown kati environment command: %q", text)
		}
	}
	if err := scanner.Err(); err != nil {
		return err
	}
	return nil
}
