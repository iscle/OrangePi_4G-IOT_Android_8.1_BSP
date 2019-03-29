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
	"strings"

	"android/soong/android"
)

func init() {
	// Most Android source files are not clang-tidy clean yet.
	// Global tidy checks include only google*, performance*,
	// and misc-macro-parentheses, but not google-readability*
	// or google-runtime-references.
	pctx.VariableFunc("TidyDefaultGlobalChecks", func(config interface{}) (string, error) {
		if override := config.(android.Config).Getenv("DEFAULT_GLOBAL_TIDY_CHECKS"); override != "" {
			return override, nil
		}
		return strings.Join([]string{
			"-*",
			"google*",
			"misc-macro-parentheses",
			"performance*",
			"-google-readability*",
			"-google-runtime-references",
		}, ","), nil
	})

	// There are too many clang-tidy warnings in external and vendor projects.
	// Enable only some google checks for these projects.
	pctx.VariableFunc("TidyExternalVendorChecks", func(config interface{}) (string, error) {
		if override := config.(android.Config).Getenv("DEFAULT_EXTERNAL_VENDOR_TIDY_CHECKS"); override != "" {
			return override, nil
		}
		return strings.Join([]string{
			"-*",
			"google*",
			"-google-build-using-namespace",
			"-google-default-arguments",
			"-google-explicit-constructor",
			"-google-readability*",
			"-google-runtime-int",
			"-google-runtime-references",
		}, ","), nil
	})

	// Give warnings to header files only in selected directories.
	// Do not give warnings to external or vendor header files, which contain too
	// many warnings.
	pctx.StaticVariable("TidyDefaultHeaderDirs", strings.Join([]string{
		"art/",
		"bionic/",
		"bootable/",
		"build/",
		"cts/",
		"dalvik/",
		"developers/",
		"development/",
		"frameworks/",
		"libcore/",
		"libnativehelper/",
		"system/",
	}, "|"))
}

type PathBasedTidyCheck struct {
	PathPrefix string
	Checks     string
}

const tidyDefault = "${config.TidyDefaultGlobalChecks}"
const tidyExternalVendor = "${config.TidyExternalVendorChecks}"

// This is a map of local path prefixes to the set of default clang-tidy checks
// to be used.
// The last matched local_path_prefix should be the most specific to be used.
var DefaultLocalTidyChecks = []PathBasedTidyCheck{
	{"external/", tidyExternalVendor},
	{"external/google", tidyDefault},
	{"external/webrtc", tidyDefault},
	{"frameworks/compile/mclinker/", tidyExternalVendor},
	{"hardware/qcom", tidyExternalVendor},
	{"vendor/", tidyExternalVendor},
	{"vendor/google", tidyDefault},
	{"vendor/google_devices", tidyExternalVendor},
}

var reversedDefaultLocalTidyChecks = reverseTidyChecks(DefaultLocalTidyChecks)

func reverseTidyChecks(in []PathBasedTidyCheck) []PathBasedTidyCheck {
	ret := make([]PathBasedTidyCheck, len(in))
	for i, check := range in {
		ret[len(in)-i-1] = check
	}
	return ret
}

func TidyChecksForDir(dir string) string {
	for _, pathCheck := range reversedDefaultLocalTidyChecks {
		if strings.HasPrefix(dir, pathCheck.PathPrefix) {
			return pathCheck.Checks
		}
	}
	return tidyDefault
}
