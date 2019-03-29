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

import (
	"io/ioutil"
	"os"
	"testing"

	"github.com/google/blueprint"
)

var prebuiltsTests = []struct {
	name     string
	modules  string
	prebuilt bool
}{
	{
		name: "no prebuilt",
		modules: `
			source {
				name: "bar",
			}`,
		prebuilt: false,
	},
	{
		name: "no source prebuilt not preferred",
		modules: `
			prebuilt {
				name: "bar",
				prefer: false,
				srcs: ["prebuilt"],
			}`,
		prebuilt: true,
	},
	{
		name: "no source prebuilt preferred",
		modules: `
			prebuilt {
				name: "bar",
				prefer: true,
				srcs: ["prebuilt"],
			}`,
		prebuilt: true,
	},
	{
		name: "prebuilt not preferred",
		modules: `
			source {
				name: "bar",
			}
			
			prebuilt {
				name: "bar",
				prefer: false,
				srcs: ["prebuilt"],
			}`,
		prebuilt: false,
	},
	{
		name: "prebuilt preferred",
		modules: `
			source {
				name: "bar",
			}
			
			prebuilt {
				name: "bar",
				prefer: true,
				srcs: ["prebuilt"],
			}`,
		prebuilt: true,
	},
	{
		name: "prebuilt no file not preferred",
		modules: `
			source {
				name: "bar",
			}
			
			prebuilt {
				name: "bar",
				prefer: false,
			}`,
		prebuilt: false,
	},
	{
		name: "prebuilt no file preferred",
		modules: `
			source {
				name: "bar",
			}
			
			prebuilt {
				name: "bar",
				prefer: true,
			}`,
		prebuilt: false,
	},
}

func TestPrebuilts(t *testing.T) {
	buildDir, err := ioutil.TempDir("", "soong_prebuilt_test")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(buildDir)

	config := TestConfig(buildDir)

	for _, test := range prebuiltsTests {
		t.Run(test.name, func(t *testing.T) {
			ctx := NewTestContext()
			ctx.PreArchMutators(registerPrebuiltsPreArchMutators)
			ctx.PostDepsMutators(registerPrebuiltsPostDepsMutators)
			ctx.RegisterModuleType("prebuilt", ModuleFactoryAdaptor(newPrebuiltModule))
			ctx.RegisterModuleType("source", ModuleFactoryAdaptor(newSourceModule))
			ctx.Register()
			ctx.MockFileSystem(map[string][]byte{
				"Blueprints": []byte(`
					source {
						name: "foo",
						deps: ["bar"],
					}
					` + test.modules),
			})

			_, errs := ctx.ParseBlueprintsFiles("Blueprints")
			fail(t, errs)
			_, errs = ctx.PrepareBuildActions(config)
			fail(t, errs)

			foo := ctx.ModuleForTests("foo", "")

			var dependsOnSourceModule, dependsOnPrebuiltModule bool
			ctx.VisitDirectDeps(foo.Module(), func(m blueprint.Module) {
				if _, ok := m.(*sourceModule); ok {
					dependsOnSourceModule = true
				}
				if p, ok := m.(*prebuiltModule); ok {
					dependsOnPrebuiltModule = true
					if !p.Prebuilt().Properties.UsePrebuilt {
						t.Errorf("dependency on prebuilt module not marked used")
					}
				}
			})

			if test.prebuilt {
				if !dependsOnPrebuiltModule {
					t.Errorf("doesn't depend on prebuilt module")
				}

				if dependsOnSourceModule {
					t.Errorf("depends on source module")
				}
			} else {
				if dependsOnPrebuiltModule {
					t.Errorf("depends on prebuilt module")
				}

				if !dependsOnSourceModule {
					t.Errorf("doens't depend on source module")
				}
			}
		})
	}
}

type prebuiltModule struct {
	ModuleBase
	prebuilt Prebuilt
}

func newPrebuiltModule() Module {
	m := &prebuiltModule{}
	m.AddProperties(&m.prebuilt.Properties)
	InitAndroidModule(m)
	return m
}

func (p *prebuiltModule) Name() string {
	return p.prebuilt.Name(p.ModuleBase.Name())
}

func (p *prebuiltModule) DepsMutator(ctx BottomUpMutatorContext) {
}

func (p *prebuiltModule) GenerateAndroidBuildActions(ModuleContext) {
}

func (p *prebuiltModule) Prebuilt() *Prebuilt {
	return &p.prebuilt
}

type sourceModule struct {
	ModuleBase
	properties struct {
		Deps []string
	}
	dependsOnSourceModule, dependsOnPrebuiltModule bool
}

func newSourceModule() Module {
	m := &sourceModule{}
	m.AddProperties(&m.properties)
	InitAndroidModule(m)
	return m
}

func (s *sourceModule) DepsMutator(ctx BottomUpMutatorContext) {
	for _, d := range s.properties.Deps {
		ctx.AddDependency(ctx.Module(), nil, d)
	}
}

func (s *sourceModule) GenerateAndroidBuildActions(ctx ModuleContext) {
}

func fail(t *testing.T, errs []error) {
	if len(errs) > 0 {
		for _, err := range errs {
			t.Error(err)
		}
		t.FailNow()
	}
}
