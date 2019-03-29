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

package android

import "github.com/google/blueprint"

// This file implements common functionality for handling modules that may exist as prebuilts,
// source, or both.

type prebuiltDependencyTag struct {
	blueprint.BaseDependencyTag
}

var prebuiltDepTag prebuiltDependencyTag

type Prebuilt struct {
	Properties struct {
		Srcs []string `android:"arch_variant"`
		// When prefer is set to true the prebuilt will be used instead of any source module with
		// a matching name.
		Prefer bool `android:"arch_variant"`

		SourceExists bool `blueprint:"mutated"`
		UsePrebuilt  bool `blueprint:"mutated"`
	}
	module Module
}

func (p *Prebuilt) Name(name string) string {
	return "prebuilt_" + name
}

func (p *Prebuilt) Path(ctx ModuleContext) Path {
	if len(p.Properties.Srcs) == 0 {
		ctx.PropertyErrorf("srcs", "missing prebuilt source file")
		return nil
	}

	if len(p.Properties.Srcs) > 1 {
		ctx.PropertyErrorf("srcs", "multiple prebuilt source files")
		return nil
	}

	return PathForModuleSrc(ctx, p.Properties.Srcs[0])
}

type PrebuiltInterface interface {
	Module
	Prebuilt() *Prebuilt
}

func registerPrebuiltsPreArchMutators(ctx RegisterMutatorsContext) {
	ctx.BottomUp("prebuilts", prebuiltMutator).Parallel()
}

func registerPrebuiltsPostDepsMutators(ctx RegisterMutatorsContext) {
	ctx.TopDown("prebuilt_select", PrebuiltSelectModuleMutator).Parallel()
	ctx.BottomUp("prebuilt_replace", PrebuiltReplaceMutator).Parallel()
}

// prebuiltMutator ensures that there is always a module with an undecorated name, and marks
// prebuilt modules that have both a prebuilt and a source module.
func prebuiltMutator(ctx BottomUpMutatorContext) {
	if m, ok := ctx.Module().(PrebuiltInterface); ok && m.Prebuilt() != nil {
		p := m.Prebuilt()
		name := m.base().BaseModuleName()
		if ctx.OtherModuleExists(name) {
			ctx.AddReverseDependency(ctx.Module(), prebuiltDepTag, name)
			p.Properties.SourceExists = true
		} else {
			ctx.Rename(name)
		}
	}
}

// PrebuiltSelectModuleMutator marks prebuilts that are used, either overriding source modules or
// because the source module doesn't exist.  It also disables installing overridden source modules.
func PrebuiltSelectModuleMutator(ctx TopDownMutatorContext) {
	if m, ok := ctx.Module().(PrebuiltInterface); ok && m.Prebuilt() != nil {
		p := m.Prebuilt()
		if !p.Properties.SourceExists {
			p.Properties.UsePrebuilt = p.usePrebuilt(ctx, nil)
		}
	} else if s, ok := ctx.Module().(Module); ok {
		ctx.VisitDirectDeps(func(m blueprint.Module) {
			if ctx.OtherModuleDependencyTag(m) == prebuiltDepTag {
				p := m.(PrebuiltInterface).Prebuilt()
				if p.usePrebuilt(ctx, s) {
					p.Properties.UsePrebuilt = true
					s.SkipInstall()
				}
			}
		})
	}
}

// PrebuiltReplaceMutator replaces dependencies on the source module with dependencies on the
// prebuilt when both modules exist and the prebuilt should be used.  When the prebuilt should not
// be used, disable installing it.
func PrebuiltReplaceMutator(ctx BottomUpMutatorContext) {
	if m, ok := ctx.Module().(PrebuiltInterface); ok && m.Prebuilt() != nil {
		p := m.Prebuilt()
		name := m.base().BaseModuleName()
		if p.Properties.UsePrebuilt {
			if p.Properties.SourceExists {
				ctx.ReplaceDependencies(name)
			}
		} else {
			m.SkipInstall()
		}
	}
}

// usePrebuilt returns true if a prebuilt should be used instead of the source module.  The prebuilt
// will be used if it is marked "prefer" or if the source module is disabled.
func (p *Prebuilt) usePrebuilt(ctx TopDownMutatorContext, source Module) bool {
	if len(p.Properties.Srcs) == 0 {
		return false
	}

	// TODO: use p.Properties.Name and ctx.ModuleDir to override preference
	if p.Properties.Prefer {
		return true
	}

	return source == nil || !source.Enabled()
}
