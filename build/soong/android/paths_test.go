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
	"errors"
	"fmt"
	"reflect"
	"strings"
	"testing"

	"github.com/google/blueprint/pathtools"
)

type strsTestCase struct {
	in  []string
	out string
	err []error
}

var commonValidatePathTestCases = []strsTestCase{
	{
		in:  []string{""},
		out: "",
	},
	{
		in:  []string{"a/b"},
		out: "a/b",
	},
	{
		in:  []string{"a/b", "c"},
		out: "a/b/c",
	},
	{
		in:  []string{"a/.."},
		out: ".",
	},
	{
		in:  []string{"."},
		out: ".",
	},
	{
		in:  []string{".."},
		out: "",
		err: []error{errors.New("Path is outside directory: ..")},
	},
	{
		in:  []string{"../a"},
		out: "",
		err: []error{errors.New("Path is outside directory: ../a")},
	},
	{
		in:  []string{"b/../../a"},
		out: "",
		err: []error{errors.New("Path is outside directory: ../a")},
	},
	{
		in:  []string{"/a"},
		out: "",
		err: []error{errors.New("Path is outside directory: /a")},
	},
	{
		in:  []string{"a", "../b"},
		out: "",
		err: []error{errors.New("Path is outside directory: ../b")},
	},
	{
		in:  []string{"a", "b/../../c"},
		out: "",
		err: []error{errors.New("Path is outside directory: ../c")},
	},
	{
		in:  []string{"a", "./.."},
		out: "",
		err: []error{errors.New("Path is outside directory: ..")},
	},
}

var validateSafePathTestCases = append(commonValidatePathTestCases, []strsTestCase{
	{
		in:  []string{"$host/../$a"},
		out: "$a",
	},
}...)

var validatePathTestCases = append(commonValidatePathTestCases, []strsTestCase{
	{
		in:  []string{"$host/../$a"},
		out: "",
		err: []error{errors.New("Path contains invalid character($): $host/../$a")},
	},
	{
		in:  []string{"$host/.."},
		out: "",
		err: []error{errors.New("Path contains invalid character($): $host/..")},
	},
}...)

func TestValidateSafePath(t *testing.T) {
	for _, testCase := range validateSafePathTestCases {
		ctx := &configErrorWrapper{}
		out := validateSafePath(ctx, testCase.in...)
		check(t, "validateSafePath", p(testCase.in), out, ctx.errors, testCase.out, testCase.err)
	}
}

func TestValidatePath(t *testing.T) {
	for _, testCase := range validatePathTestCases {
		ctx := &configErrorWrapper{}
		out := validatePath(ctx, testCase.in...)
		check(t, "validatePath", p(testCase.in), out, ctx.errors, testCase.out, testCase.err)
	}
}

func TestOptionalPath(t *testing.T) {
	var path OptionalPath
	checkInvalidOptionalPath(t, path)

	path = OptionalPathForPath(nil)
	checkInvalidOptionalPath(t, path)
}

func checkInvalidOptionalPath(t *testing.T, path OptionalPath) {
	if path.Valid() {
		t.Errorf("Uninitialized OptionalPath should not be valid")
	}
	if path.String() != "" {
		t.Errorf("Uninitialized OptionalPath String() should return \"\", not %q", path.String())
	}
	defer func() {
		if r := recover(); r == nil {
			t.Errorf("Expected a panic when calling Path() on an uninitialized OptionalPath")
		}
	}()
	path.Path()
}

func check(t *testing.T, testType, testString string,
	got interface{}, err []error,
	expected interface{}, expectedErr []error) {

	printedTestCase := false
	e := func(s string, expected, got interface{}) {
		if !printedTestCase {
			t.Errorf("test case %s: %s", testType, testString)
			printedTestCase = true
		}
		t.Errorf("incorrect %s", s)
		t.Errorf("  expected: %s", p(expected))
		t.Errorf("       got: %s", p(got))
	}

	if !reflect.DeepEqual(expectedErr, err) {
		e("errors:", expectedErr, err)
	}

	if !reflect.DeepEqual(expected, got) {
		e("output:", expected, got)
	}
}

func p(in interface{}) string {
	if v, ok := in.([]interface{}); ok {
		s := make([]string, len(v))
		for i := range v {
			s[i] = fmt.Sprintf("%#v", v[i])
		}
		return "[" + strings.Join(s, ", ") + "]"
	} else {
		return fmt.Sprintf("%#v", in)
	}
}

type moduleInstallPathContextImpl struct {
	androidBaseContextImpl

	inData         bool
	inSanitizerDir bool
}

func (moduleInstallPathContextImpl) Fs() pathtools.FileSystem {
	return pathtools.MockFs(nil)
}

func (m moduleInstallPathContextImpl) Config() interface{} {
	return m.androidBaseContextImpl.config
}

func (moduleInstallPathContextImpl) AddNinjaFileDeps(deps ...string) {}

func (m moduleInstallPathContextImpl) InstallInData() bool {
	return m.inData
}

func (m moduleInstallPathContextImpl) InstallInSanitizerDir() bool {
	return m.inSanitizerDir
}

func TestPathForModuleInstall(t *testing.T) {
	testConfig := TestConfig("")

	hostTarget := Target{Os: Linux}
	deviceTarget := Target{Os: Android}

	testCases := []struct {
		name string
		ctx  *moduleInstallPathContextImpl
		in   []string
		out  string
	}{
		{
			name: "host binary",
			ctx: &moduleInstallPathContextImpl{
				androidBaseContextImpl: androidBaseContextImpl{
					target: hostTarget,
				},
			},
			in:  []string{"bin", "my_test"},
			out: "host/linux-x86/bin/my_test",
		},

		{
			name: "system binary",
			ctx: &moduleInstallPathContextImpl{
				androidBaseContextImpl: androidBaseContextImpl{
					target: deviceTarget,
				},
			},
			in:  []string{"bin", "my_test"},
			out: "target/product/test_device/system/bin/my_test",
		},
		{
			name: "vendor binary",
			ctx: &moduleInstallPathContextImpl{
				androidBaseContextImpl: androidBaseContextImpl{
					target: deviceTarget,
					vendor: true,
				},
			},
			in:  []string{"bin", "my_test"},
			out: "target/product/test_device/vendor/bin/my_test",
		},

		{
			name: "system native test binary",
			ctx: &moduleInstallPathContextImpl{
				androidBaseContextImpl: androidBaseContextImpl{
					target: deviceTarget,
				},
				inData: true,
			},
			in:  []string{"nativetest", "my_test"},
			out: "target/product/test_device/data/nativetest/my_test",
		},
		{
			name: "vendor native test binary",
			ctx: &moduleInstallPathContextImpl{
				androidBaseContextImpl: androidBaseContextImpl{
					target: deviceTarget,
					vendor: true,
				},
				inData: true,
			},
			in:  []string{"nativetest", "my_test"},
			out: "target/product/test_device/data/nativetest/my_test",
		},

		{
			name: "sanitized system binary",
			ctx: &moduleInstallPathContextImpl{
				androidBaseContextImpl: androidBaseContextImpl{
					target: deviceTarget,
				},
				inSanitizerDir: true,
			},
			in:  []string{"bin", "my_test"},
			out: "target/product/test_device/data/asan/system/bin/my_test",
		},
		{
			name: "sanitized vendor binary",
			ctx: &moduleInstallPathContextImpl{
				androidBaseContextImpl: androidBaseContextImpl{
					target: deviceTarget,
					vendor: true,
				},
				inSanitizerDir: true,
			},
			in:  []string{"bin", "my_test"},
			out: "target/product/test_device/data/asan/vendor/bin/my_test",
		},

		{
			name: "sanitized system native test binary",
			ctx: &moduleInstallPathContextImpl{
				androidBaseContextImpl: androidBaseContextImpl{
					target: deviceTarget,
				},
				inData:         true,
				inSanitizerDir: true,
			},
			in:  []string{"nativetest", "my_test"},
			out: "target/product/test_device/data/asan/data/nativetest/my_test",
		},
		{
			name: "sanitized vendor native test binary",
			ctx: &moduleInstallPathContextImpl{
				androidBaseContextImpl: androidBaseContextImpl{
					target: deviceTarget,
					vendor: true,
				},
				inData:         true,
				inSanitizerDir: true,
			},
			in:  []string{"nativetest", "my_test"},
			out: "target/product/test_device/data/asan/data/nativetest/my_test",
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			tc.ctx.androidBaseContextImpl.config = testConfig
			output := PathForModuleInstall(tc.ctx, tc.in...)
			if output.basePath.path != tc.out {
				t.Errorf("unexpected path:\n got: %q\nwant: %q\n",
					output.basePath.path,
					tc.out)
			}
		})
	}
}
