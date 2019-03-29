// Copyright 2016 Google Inc. All rights reserved.
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

package config

import (
	"testing"
)

func TestTidyChecksForDir(t *testing.T) {
	testCases := []struct {
		input    string
		expected string
	}{
		{"foo/bar", tidyDefault},
		{"vendor/foo/bar", tidyExternalVendor},
		{"vendor/google", tidyDefault},
		{"vendor/google/foo", tidyDefault},
		{"vendor/google_devices/foo", tidyExternalVendor},
	}

	for _, testCase := range testCases {
		t.Run(testCase.input, func(t *testing.T) {
			output := TidyChecksForDir(testCase.input)
			if output != testCase.expected {
				t.Error("Output doesn't match expected", output, testCase.expected)
			}
		})
	}
}
