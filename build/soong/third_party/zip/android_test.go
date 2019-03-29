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

package zip

import (
	"bytes"
	"testing"
)

var stripZip64Testcases = []struct {
	name string
	in   []byte
	out  []byte
}{
	{
		name: "empty",
		in:   []byte{},
		out:  []byte{},
	},
	{
		name: "trailing data",
		in:   []byte{1, 2, 3},
		out:  []byte{1, 2, 3},
	},
	{
		name: "valid non-zip64 extra",
		in:   []byte{2, 0, 2, 0, 1, 2},
		out:  []byte{2, 0, 2, 0, 1, 2},
	},
	{
		name: "two valid non-zip64 extras",
		in:   []byte{2, 0, 2, 0, 1, 2, 2, 0, 0, 0},
		out:  []byte{2, 0, 2, 0, 1, 2, 2, 0, 0, 0},
	},
	{
		name: "simple zip64 extra",
		in:   []byte{1, 0, 8, 0, 1, 2, 3, 4, 5, 6, 7, 8},
		out:  []byte{},
	},
	{
		name: "zip64 extra and valid non-zip64 extra",
		in:   []byte{1, 0, 8, 0, 1, 2, 3, 4, 5, 6, 7, 8, 2, 0, 0, 0},
		out:  []byte{2, 0, 0, 0},
	},
	{
		name: "invalid extra",
		in:   []byte{0, 0, 8, 0, 0, 0},
		out:  []byte{0, 0, 8, 0, 0, 0},
	},
}

func TestStripZip64Extras(t *testing.T) {
	for _, testcase := range stripZip64Testcases {
		got := stripZip64Extras(testcase.in)
		if !bytes.Equal(got, testcase.out) {
			t.Errorf("Failed testcase %s\ninput: %v\n want: %v\n  got: %v\n", testcase.name, testcase.in, testcase.out, got)
		}
	}
}
