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

package cc

// This file contains utility functions to check for bad or illegal cflags
// specified by a module

import (
	"path/filepath"
	"strings"

	"android/soong/cc/config"
)

// Check for invalid c/conly/cpp/asflags and suggest alternatives. Only use this
// for flags explicitly passed by the user, since these flags may be used internally.
func CheckBadCompilerFlags(ctx BaseModuleContext, prop string, flags []string) {
	for _, flag := range flags {
		flag = strings.TrimSpace(flag)

		if !strings.HasPrefix(flag, "-") {
			ctx.PropertyErrorf(prop, "Flag `%s` must start with `-`", flag)
		} else if strings.HasPrefix(flag, "-I") || strings.HasPrefix(flag, "-isystem") {
			ctx.PropertyErrorf(prop, "Bad flag `%s`, use local_include_dirs or include_dirs instead", flag)
		} else if inList(flag, config.IllegalFlags) {
			ctx.PropertyErrorf(prop, "Illegal flag `%s`", flag)
		} else if flag == "--coverage" {
			ctx.PropertyErrorf(prop, "Bad flag: `%s`, use native_coverage instead", flag)
		} else if strings.Contains(flag, " ") {
			args := strings.Split(flag, " ")
			if args[0] == "-include" {
				if len(args) > 2 {
					ctx.PropertyErrorf(prop, "`-include` only takes one argument: `%s`", flag)
				}
				path := filepath.Clean(args[1])
				if strings.HasPrefix("/", path) {
					ctx.PropertyErrorf(prop, "Path must not be an absolute path: %s", flag)
				} else if strings.HasPrefix("../", path) {
					ctx.PropertyErrorf(prop, "Path must not start with `../`: `%s`. Use include_dirs to -include from a different directory", flag)
				}
			} else {
				ctx.PropertyErrorf(prop, "Bad flag: `%s` is not an allowed multi-word flag. Should it be split into multiple flags?", flag)
			}
		}
	}
}

// Check for bad ldflags and suggest alternatives. Only use this for flags
// explicitly passed by the user, since these flags may be used internally.
func CheckBadLinkerFlags(ctx BaseModuleContext, prop string, flags []string) {
	for _, flag := range flags {
		flag = strings.TrimSpace(flag)

		if !strings.HasPrefix(flag, "-") {
			ctx.PropertyErrorf(prop, "Flag `%s` must start with `-`", flag)
		} else if strings.HasPrefix(flag, "-l") {
			if ctx.Host() {
				ctx.PropertyErrorf(prop, "Bad flag: `%s`, use shared_libs or host_ldlibs instead", flag)
			} else {
				ctx.PropertyErrorf(prop, "Bad flag: `%s`, use shared_libs instead", flag)
			}
		} else if strings.HasPrefix(flag, "-L") {
			ctx.PropertyErrorf(prop, "Bad flag: `%s` is not allowed", flag)
		} else if strings.HasPrefix(flag, "-Wl,--version-script") {
			ctx.PropertyErrorf(prop, "Bad flag: `%s`, use version_script instead", flag)
		} else if flag == "--coverage" {
			ctx.PropertyErrorf(prop, "Bad flag: `%s`, use native_coverage instead", flag)
		} else if strings.Contains(flag, " ") {
			args := strings.Split(flag, " ")
			if args[0] == "-z" {
				if len(args) > 2 {
					ctx.PropertyErrorf(prop, "`-z` only takes one argument: `%s`", flag)
				}
			} else {
				ctx.PropertyErrorf(prop, "Bad flag: `%s` is not an allowed multi-word flag. Should it be split into multiple flags?", flag)
			}
		}
	}
}

// Check for bad host_ldlibs
func CheckBadHostLdlibs(ctx ModuleContext, prop string, flags []string) {
	allowed_ldlibs := ctx.toolchain().AvailableLibraries()

	if !ctx.Host() {
		panic("Invalid call to CheckBadHostLdlibs")
	}

	for _, flag := range flags {
		flag = strings.TrimSpace(flag)

		// TODO: Probably should just redo this property to prefix -l in Soong
		if !strings.HasPrefix(flag, "-l") && !strings.HasPrefix(flag, "-framework") {
			ctx.PropertyErrorf(prop, "Invalid flag: `%s`, must start with `-l` or `-framework`", flag)
		} else if !inList(flag, allowed_ldlibs) {
			ctx.PropertyErrorf(prop, "Host library `%s` not available", flag)
		}
	}
}

// Check for bad clang tidy flags
func CheckBadTidyFlags(ctx ModuleContext, prop string, flags []string) {
	for _, flag := range flags {
		flag = strings.TrimSpace(flag)

		if !strings.HasPrefix(flag, "-") {
			ctx.PropertyErrorf(prop, "Flag `%s` must start with `-`", flag)
		} else if strings.HasPrefix(flag, "-fix") {
			ctx.PropertyErrorf(prop, "Flag `%s` is not allowed, since it could cause multiple writes to the same source file", flag)
		} else if strings.HasPrefix(flag, "-checks=") {
			ctx.PropertyErrorf(prop, "Flag `%s` is not allowed, use `tidy_checks` property instead", flag)
		} else if strings.Contains(flag, " ") {
			ctx.PropertyErrorf(prop, "Bad flag: `%s` is not an allowed multi-word flag. Should it be split into multiple flags?", flag)
		}
	}
}

// Check for bad clang tidy checks
func CheckBadTidyChecks(ctx ModuleContext, prop string, checks []string) {
	for _, check := range checks {
		if strings.Contains(check, " ") {
			ctx.PropertyErrorf("tidy_checks", "Check `%s` invalid, cannot contain spaces", check)
		} else if strings.Contains(check, ",") {
			ctx.PropertyErrorf("tidy_checks", "Check `%s` invalid, cannot contain commas. Split each entry into it's own string instead", check)
		}
	}
}
