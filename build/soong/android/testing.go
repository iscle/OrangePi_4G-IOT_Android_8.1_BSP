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

package android

import (
	"fmt"
	"strings"

	"github.com/google/blueprint"
)

func NewTestContext() *TestContext {
	return &TestContext{
		Context: blueprint.NewContext(),
	}
}

type TestContext struct {
	*blueprint.Context
	preArch, preDeps, postDeps []RegisterMutatorFunc
}

func (ctx *TestContext) PreArchMutators(f RegisterMutatorFunc) {
	ctx.preArch = append(ctx.preArch, f)
}

func (ctx *TestContext) PreDepsMutators(f RegisterMutatorFunc) {
	ctx.preDeps = append(ctx.preDeps, f)
}

func (ctx *TestContext) PostDepsMutators(f RegisterMutatorFunc) {
	ctx.postDeps = append(ctx.postDeps, f)
}

func (ctx *TestContext) Register() {
	registerMutators(ctx.Context, ctx.preArch, ctx.preDeps, ctx.postDeps)

	ctx.RegisterSingletonType("env", EnvSingleton)
}

func (ctx *TestContext) ModuleForTests(name, variant string) TestingModule {
	var module Module
	ctx.VisitAllModules(func(m blueprint.Module) {
		if ctx.ModuleName(m) == name && ctx.ModuleSubDir(m) == variant {
			module = m.(Module)
		}
	})

	if module == nil {
		panic(fmt.Errorf("failed to find module %q variant %q", name, variant))
	}

	return TestingModule{module}
}

type TestingModule struct {
	module Module
}

func (m TestingModule) Module() Module {
	return m.module
}

func (m TestingModule) Rule(rule string) ModuleBuildParams {
	for _, p := range m.module.BuildParamsForTests() {
		if strings.Contains(p.Rule.String(), rule) {
			return p
		}
	}
	panic(fmt.Errorf("couldn't find rule %q", rule))
}

func (m TestingModule) Output(file string) ModuleBuildParams {
	for _, p := range m.module.BuildParamsForTests() {
		outputs := append(WritablePaths(nil), p.Outputs...)
		if p.Output != nil {
			outputs = append(outputs, p.Output)
		}
		for _, f := range outputs {
			if f.Base() == file {
				return p
			}
		}
	}
	panic(fmt.Errorf("couldn't find output %q", file))
}
