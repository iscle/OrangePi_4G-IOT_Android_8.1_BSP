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
	"context"
	"reflect"
	"strings"
	"testing"

	"android/soong/ui/logger"
)

func testContext() Context {
	return Context{&ContextImpl{
		Context:        context.Background(),
		Logger:         logger.New(&bytes.Buffer{}),
		StdioInterface: NewCustomStdio(&bytes.Buffer{}, &bytes.Buffer{}, &bytes.Buffer{}),
	}}
}

func TestConfigParseArgsJK(t *testing.T) {
	ctx := testContext()

	testCases := []struct {
		args []string

		parallel  int
		keepGoing int
		remaining []string
	}{
		{nil, -1, -1, nil},

		{[]string{"-j"}, -1, -1, nil},
		{[]string{"-j1"}, 1, -1, nil},
		{[]string{"-j1234"}, 1234, -1, nil},

		{[]string{"-j", "1"}, 1, -1, nil},
		{[]string{"-j", "1234"}, 1234, -1, nil},
		{[]string{"-j", "1234", "abc"}, 1234, -1, []string{"abc"}},
		{[]string{"-j", "abc"}, -1, -1, []string{"abc"}},
		{[]string{"-j", "1abc"}, -1, -1, []string{"1abc"}},

		{[]string{"-k"}, -1, 0, nil},
		{[]string{"-k0"}, -1, 0, nil},
		{[]string{"-k1"}, -1, 1, nil},
		{[]string{"-k1234"}, -1, 1234, nil},

		{[]string{"-k", "0"}, -1, 0, nil},
		{[]string{"-k", "1"}, -1, 1, nil},
		{[]string{"-k", "1234"}, -1, 1234, nil},
		{[]string{"-k", "1234", "abc"}, -1, 1234, []string{"abc"}},
		{[]string{"-k", "abc"}, -1, 0, []string{"abc"}},
		{[]string{"-k", "1abc"}, -1, 0, []string{"1abc"}},

		// TODO: These are supported in Make, should we support them?
		//{[]string{"-kj"}, -1, 0},
		//{[]string{"-kj8"}, 8, 0},

		// -jk is not valid in Make
	}

	for _, tc := range testCases {
		t.Run(strings.Join(tc.args, " "), func(t *testing.T) {
			defer logger.Recover(func(err error) {
				t.Fatal(err)
			})

			c := &configImpl{
				parallel:  -1,
				keepGoing: -1,
			}
			c.parseArgs(ctx, tc.args)

			if c.parallel != tc.parallel {
				t.Errorf("for %q, parallel:\nwant: %d\n got: %d\n",
					strings.Join(tc.args, " "),
					tc.parallel, c.parallel)
			}
			if c.keepGoing != tc.keepGoing {
				t.Errorf("for %q, keep going:\nwant: %d\n got: %d\n",
					strings.Join(tc.args, " "),
					tc.keepGoing, c.keepGoing)
			}
			if !reflect.DeepEqual(c.arguments, tc.remaining) {
				t.Errorf("for %q, remaining arguments:\nwant: %q\n got: %q\n",
					strings.Join(tc.args, " "),
					tc.remaining, c.arguments)
			}
		})
	}
}

func TestConfigParseArgsVars(t *testing.T) {
	ctx := testContext()

	testCases := []struct {
		env  []string
		args []string

		expectedEnv []string
		remaining   []string
	}{
		{},
		{
			env: []string{"A=bc"},

			expectedEnv: []string{"A=bc"},
		},
		{
			args: []string{"abc"},

			remaining: []string{"abc"},
		},

		{
			args: []string{"A=bc"},

			expectedEnv: []string{"A=bc"},
		},
		{
			env:  []string{"A=a"},
			args: []string{"A=bc"},

			expectedEnv: []string{"A=bc"},
		},

		{
			env:  []string{"A=a"},
			args: []string{"A=", "=b"},

			expectedEnv: []string{"A="},
			remaining:   []string{"=b"},
		},
	}

	for _, tc := range testCases {
		t.Run(strings.Join(tc.args, " "), func(t *testing.T) {
			defer logger.Recover(func(err error) {
				t.Fatal(err)
			})

			e := Environment(tc.env)
			c := &configImpl{
				environ: &e,
			}
			c.parseArgs(ctx, tc.args)

			if !reflect.DeepEqual([]string(*c.environ), tc.expectedEnv) {
				t.Errorf("for env=%q args=%q, environment:\nwant: %q\n got: %q\n",
					tc.env, tc.args,
					tc.expectedEnv, []string(*c.environ))
			}
			if !reflect.DeepEqual(c.arguments, tc.remaining) {
				t.Errorf("for env=%q args=%q, remaining arguments:\nwant: %q\n got: %q\n",
					tc.env, tc.args,
					tc.remaining, c.arguments)
			}
		})
	}
}
