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

import (
	"strings"

	"github.com/google/blueprint/proptools"

	"android/soong/cc/config"
)

type TidyProperties struct {
	// whether to run clang-tidy over C-like sources.
	Tidy *bool

	// Extra flags to pass to clang-tidy
	Tidy_flags []string

	// Extra checks to enable or disable in clang-tidy
	Tidy_checks []string
}

type tidyFeature struct {
	Properties TidyProperties
}

func (tidy *tidyFeature) props() []interface{} {
	return []interface{}{&tidy.Properties}
}

func (tidy *tidyFeature) begin(ctx BaseModuleContext) {
}

func (tidy *tidyFeature) deps(ctx DepsContext, deps Deps) Deps {
	return deps
}

func (tidy *tidyFeature) flags(ctx ModuleContext, flags Flags) Flags {
	CheckBadTidyFlags(ctx, "tidy_flags", tidy.Properties.Tidy_flags)
	CheckBadTidyChecks(ctx, "tidy_checks", tidy.Properties.Tidy_checks)

	// Check if tidy is explicitly disabled for this module
	if tidy.Properties.Tidy != nil && !*tidy.Properties.Tidy {
		return flags
	}

	// If not explicitly set, check the global tidy flag
	if tidy.Properties.Tidy == nil && !ctx.AConfig().ClangTidy() {
		return flags
	}

	// Clang-tidy requires clang
	if !flags.Clang {
		return flags
	}

	flags.Tidy = true

	esc := proptools.NinjaAndShellEscape

	flags.TidyFlags = append(flags.TidyFlags, esc(tidy.Properties.Tidy_flags)...)
	if len(flags.TidyFlags) == 0 {
		headerFilter := "-header-filter=\"(" + ctx.ModuleDir() + "|${config.TidyDefaultHeaderDirs})\""
		flags.TidyFlags = append(flags.TidyFlags, headerFilter)
	}

	// We might be using the static analyzer through clang tidy.
	// https://bugs.llvm.org/show_bug.cgi?id=32914
	flags.TidyFlags = append(flags.TidyFlags, "-extra-arg-before=-D__clang_analyzer__")

	tidyChecks := "-checks="
	if checks := ctx.AConfig().TidyChecks(); len(checks) > 0 {
		tidyChecks += checks
	} else {
		tidyChecks += config.TidyChecksForDir(ctx.ModuleDir())
	}
	if len(tidy.Properties.Tidy_checks) > 0 {
		tidyChecks = tidyChecks + "," + strings.Join(esc(tidy.Properties.Tidy_checks), ",")
	}
	flags.TidyFlags = append(flags.TidyFlags, tidyChecks)

	return flags
}
