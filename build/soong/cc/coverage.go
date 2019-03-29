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

package cc

import (
	"android/soong/android"

	"github.com/google/blueprint"
)

type CoverageProperties struct {
	Native_coverage *bool

	CoverageEnabled bool `blueprint:"mutated"`
}

type coverage struct {
	Properties CoverageProperties

	// Whether binaries containing this module need --coverage added to their ldflags
	linkCoverage bool
}

func (cov *coverage) props() []interface{} {
	return []interface{}{&cov.Properties}
}

func (cov *coverage) begin(ctx BaseModuleContext) {}

func (cov *coverage) deps(ctx BaseModuleContext, deps Deps) Deps {
	return deps
}

func (cov *coverage) flags(ctx ModuleContext, flags Flags) Flags {
	if !ctx.DeviceConfig().NativeCoverageEnabled() {
		return flags
	}

	if cov.Properties.CoverageEnabled {
		flags.Coverage = true
		flags.GlobalFlags = append(flags.GlobalFlags, "--coverage", "-O0")
		cov.linkCoverage = true
	}

	// Even if we don't have coverage enabled, if any of our object files were compiled
	// with coverage, then we need to add --coverage to our ldflags.
	if !cov.linkCoverage {
		if ctx.static() && !ctx.staticBinary() {
			// For static libraries, the only thing that changes our object files
			// are included whole static libraries, so check to see if any of
			// those have coverage enabled.
			ctx.VisitDirectDeps(func(m blueprint.Module) {
				if ctx.OtherModuleDependencyTag(m) != wholeStaticDepTag {
					return
				}

				if cc, ok := m.(*Module); ok && cc.coverage != nil {
					if cc.coverage.linkCoverage {
						cov.linkCoverage = true
					}
				}
			})
		} else {
			// For executables and shared libraries, we need to check all of
			// our static dependencies.
			ctx.VisitDirectDeps(func(m blueprint.Module) {
				cc, ok := m.(*Module)
				if !ok || cc.coverage == nil {
					return
				}

				if static, ok := cc.linker.(libraryInterface); !ok || !static.static() {
					return
				}

				if cc.coverage.linkCoverage {
					cov.linkCoverage = true
				}
			})
		}
	}

	if cov.linkCoverage {
		flags.LdFlags = append(flags.LdFlags, "--coverage")
	}

	return flags
}

func coverageLinkingMutator(mctx android.BottomUpMutatorContext) {
	if c, ok := mctx.Module().(*Module); ok && c.coverage != nil {
		var enabled bool

		if !mctx.DeviceConfig().NativeCoverageEnabled() {
			// Coverage is disabled globally
		} else if mctx.Host() {
			// TODO(dwillemsen): because of -nodefaultlibs, we must depend on libclang_rt.profile-*.a
			// Just turn off for now.
		} else if c.coverage.Properties.Native_coverage != nil {
			enabled = *c.coverage.Properties.Native_coverage
		} else {
			enabled = mctx.DeviceConfig().CoverageEnabledForPath(mctx.ModuleDir())
		}

		if enabled {
			// Create a variation so that we don't need to recompile objects
			// when turning on or off coverage. We'll still relink the necessary
			// binaries, since we don't know which ones those are until later.
			m := mctx.CreateLocalVariations("cov")
			m[0].(*Module).coverage.Properties.CoverageEnabled = true
		}
	}
}
