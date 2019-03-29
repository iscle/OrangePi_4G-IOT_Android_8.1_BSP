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
	"runtime"

	"github.com/google/blueprint"

	"android/soong/android"
)

func init() {
	pctx.SourcePathVariable("relocationPackerCmd", "prebuilts/misc/${config.HostPrebuiltTag}/relocation_packer/relocation_packer")
}

var relocationPackerRule = pctx.AndroidStaticRule("packRelocations",
	blueprint.RuleParams{
		Command:     "rm -f $out && cp $in $out && $relocationPackerCmd $out",
		CommandDeps: []string{"$relocationPackerCmd"},
	})

type RelocationPackerProperties struct {
	Pack_relocations *bool `android:"arch_variant"`

	// This will be true even if we're embedded in Make, in which case
	// we'll defer to make to actually do the packing.
	PackingRelocations bool `blueprint:"mutated"`
}

type relocationPacker struct {
	Properties RelocationPackerProperties
}

func (p *relocationPacker) packingInit(ctx BaseModuleContext) {
	enabled := true
	// Relocation packer isn't available on Darwin yet
	if runtime.GOOS == "darwin" {
		enabled = false
	}
	if ctx.Target().Os != android.Android {
		enabled = false
	}
	if ctx.AConfig().Getenv("DISABLE_RELOCATION_PACKER") == "true" {
		enabled = false
	}
	if ctx.sdk() {
		enabled = false
	}
	if p.Properties.Pack_relocations != nil &&
		*p.Properties.Pack_relocations == false {
		enabled = false
	}

	p.Properties.PackingRelocations = enabled
}

func (p *relocationPacker) needsPacking(ctx ModuleContext) bool {
	if ctx.AConfig().EmbeddedInMake() {
		return false
	}
	return p.Properties.PackingRelocations
}

func (p *relocationPacker) pack(ctx ModuleContext, in, out android.ModuleOutPath, flags builderFlags) {
	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        relocationPackerRule,
		Description: "pack relocations",
		Output:      out,
		Input:       in,
	})
}
