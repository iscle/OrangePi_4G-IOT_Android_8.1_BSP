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
	"github.com/google/blueprint"
)

// Mutator phases:
//   Pre-arch
//   Arch
//   Pre-deps
//   Deps
//   PostDeps

func registerMutatorsToContext(ctx *blueprint.Context, mutators []*mutator) {
	for _, t := range mutators {
		var handle blueprint.MutatorHandle
		if t.bottomUpMutator != nil {
			handle = ctx.RegisterBottomUpMutator(t.name, t.bottomUpMutator)
		} else if t.topDownMutator != nil {
			handle = ctx.RegisterTopDownMutator(t.name, t.topDownMutator)
		}
		if t.parallel {
			handle.Parallel()
		}
	}
}

func registerMutators(ctx *blueprint.Context, preArch, preDeps, postDeps []RegisterMutatorFunc) {
	mctx := &registerMutatorsContext{}

	register := func(funcs []RegisterMutatorFunc) {
		for _, f := range funcs {
			f(mctx)
		}
	}

	register(preArch)

	register(preDeps)

	mctx.BottomUp("deps", depsMutator).Parallel()

	register(postDeps)

	registerMutatorsToContext(ctx, mctx.mutators)
}

type registerMutatorsContext struct {
	mutators []*mutator
}

type RegisterMutatorsContext interface {
	TopDown(name string, m AndroidTopDownMutator) MutatorHandle
	BottomUp(name string, m AndroidBottomUpMutator) MutatorHandle
}

type RegisterMutatorFunc func(RegisterMutatorsContext)

var preArch = []RegisterMutatorFunc{
	func(ctx RegisterMutatorsContext) {
		ctx.TopDown("load_hooks", loadHookMutator).Parallel()
	},
	registerPrebuiltsPreArchMutators,
	registerDefaultsPreArchMutators,
}

var preDeps = []RegisterMutatorFunc{
	func(ctx RegisterMutatorsContext) {
		ctx.BottomUp("arch", archMutator).Parallel()
		ctx.TopDown("arch_hooks", archHookMutator).Parallel()
	},
}

var postDeps = []RegisterMutatorFunc{
	registerPrebuiltsPostDepsMutators,
}

func PreArchMutators(f RegisterMutatorFunc) {
	preArch = append(preArch, f)
}

func PreDepsMutators(f RegisterMutatorFunc) {
	preDeps = append(preDeps, f)
}

func PostDepsMutators(f RegisterMutatorFunc) {
	postDeps = append(postDeps, f)
}

type AndroidTopDownMutator func(TopDownMutatorContext)

type TopDownMutatorContext interface {
	blueprint.TopDownMutatorContext
	androidBaseContext
}

type androidTopDownMutatorContext struct {
	blueprint.TopDownMutatorContext
	androidBaseContextImpl
}

type AndroidBottomUpMutator func(BottomUpMutatorContext)

type BottomUpMutatorContext interface {
	blueprint.BottomUpMutatorContext
	androidBaseContext
}

type androidBottomUpMutatorContext struct {
	blueprint.BottomUpMutatorContext
	androidBaseContextImpl
}

func (x *registerMutatorsContext) BottomUp(name string, m AndroidBottomUpMutator) MutatorHandle {
	f := func(ctx blueprint.BottomUpMutatorContext) {
		if a, ok := ctx.Module().(Module); ok {
			actx := &androidBottomUpMutatorContext{
				BottomUpMutatorContext: ctx,
				androidBaseContextImpl: a.base().androidBaseContextFactory(ctx),
			}
			m(actx)
		}
	}
	mutator := &mutator{name: name, bottomUpMutator: f}
	x.mutators = append(x.mutators, mutator)
	return mutator
}

func (x *registerMutatorsContext) TopDown(name string, m AndroidTopDownMutator) MutatorHandle {
	f := func(ctx blueprint.TopDownMutatorContext) {
		if a, ok := ctx.Module().(Module); ok {
			actx := &androidTopDownMutatorContext{
				TopDownMutatorContext:  ctx,
				androidBaseContextImpl: a.base().androidBaseContextFactory(ctx),
			}
			m(actx)
		}
	}
	mutator := &mutator{name: name, topDownMutator: f}
	x.mutators = append(x.mutators, mutator)
	return mutator
}

type MutatorHandle interface {
	Parallel() MutatorHandle
}

func (mutator *mutator) Parallel() MutatorHandle {
	mutator.parallel = true
	return mutator
}

func depsMutator(ctx BottomUpMutatorContext) {
	if m, ok := ctx.Module().(Module); ok {
		m.DepsMutator(ctx)
	}
}
