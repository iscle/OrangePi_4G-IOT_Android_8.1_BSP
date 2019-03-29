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

import "testing"

func TestStripAnsiEscapes(t *testing.T) {
	testcases := []struct {
		input  string
		output string
	}{
		{
			"",
			"",
		},
		{
			"This is a test",
			"This is a test",
		},
		{
			"interrupted: \x1b[12",
			"interrupted: ",
		},
		{
			"other \x1bescape \x1b",
			"other \x1bescape \x1b",
		},
		{ // from pretty-error macro
			"\x1b[1mart/Android.mk: \x1b[31merror:\x1b[0m\x1b[1m art: test error \x1b[0m",
			"art/Android.mk: error: art: test error ",
		},
		{ // from envsetup.sh make wrapper
			"\x1b[0;31m#### make failed to build some targets (2 seconds) ####\x1b[00m",
			"#### make failed to build some targets (2 seconds) ####",
		},
		{ // from clang (via ninja testcase)
			"\x1b[1maffixmgr.cxx:286:15: \x1b[0m\x1b[0;1;35mwarning: \x1b[0m\x1b[1musing the result... [-Wparentheses]\x1b[0m",
			"affixmgr.cxx:286:15: warning: using the result... [-Wparentheses]",
		},
	}
	for _, tc := range testcases {
		got := string(stripAnsiEscapes([]byte(tc.input)))
		if got != tc.output {
			t.Errorf("output strings didn't match\n"+
				"input: %#v\n"+
				" want: %#v\n"+
				"  got: %#v", tc.input, tc.output, got)
		}
	}
}
