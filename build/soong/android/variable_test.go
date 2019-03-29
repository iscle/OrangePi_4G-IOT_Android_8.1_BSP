// Copyright 2015 Google Inc. All rights reserved.
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

package android

import (
	"reflect"
	"testing"
)

type printfIntoPropertyTestCase struct {
	in  string
	val interface{}
	out string
	err bool
}

var printfIntoPropertyTestCases = []printfIntoPropertyTestCase{
	{
		in:  "%d",
		val: 0,
		out: "0",
	},
	{
		in:  "%d",
		val: 1,
		out: "1",
	},
	{
		in:  "%d",
		val: 2,
		out: "2",
	},
	{
		in:  "%d",
		val: false,
		out: "0",
	},
	{
		in:  "%d",
		val: true,
		out: "1",
	},
	{
		in:  "%d",
		val: -1,
		out: "-1",
	},

	{
		in:  "-DA=%d",
		val: 1,
		out: "-DA=1",
	},
	{
		in:  "-DA=%du",
		val: 1,
		out: "-DA=1u",
	},
	{
		in:  "-DA=%s",
		val: "abc",
		out: "-DA=abc",
	},
	{
		in:  `-DA="%s"`,
		val: "abc",
		out: `-DA="abc"`,
	},

	{
		in:  "%%",
		err: true,
	},
	{
		in:  "%d%s",
		err: true,
	},
	{
		in:  "%d,%s",
		err: true,
	},
	{
		in:  "%d",
		val: "",
		err: true,
	},
	{
		in:  "%d",
		val: 1.5,
		err: true,
	},
	{
		in:  "%f",
		val: 1.5,
		err: true,
	},
}

func TestPrintfIntoProperty(t *testing.T) {
	for _, testCase := range printfIntoPropertyTestCases {
		s := testCase.in
		v := reflect.ValueOf(&s).Elem()
		err := printfIntoProperty(v, testCase.val)
		if err != nil && !testCase.err {
			t.Errorf("unexpected error %s", err)
		} else if err == nil && testCase.err {
			t.Errorf("expected error")
		} else if err == nil && v.String() != testCase.out {
			t.Errorf("expected %q got %q", testCase.out, v.String())
		}
	}
}
