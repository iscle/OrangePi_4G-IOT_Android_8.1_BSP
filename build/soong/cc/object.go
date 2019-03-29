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
	"fmt"

	"android/soong/android"
)

//
// Objects (for crt*.o)
//

func init() {
	android.RegisterModuleType("cc_object", objectFactory)
}

type objectLinker struct {
	*baseLinker
	Properties ObjectLinkerProperties
}

func objectFactory() android.Module {
	module := newBaseModule(android.HostAndDeviceSupported, android.MultilibBoth)
	module.linker = &objectLinker{
		baseLinker: NewBaseLinker(),
	}
	module.compiler = NewBaseCompiler()
	return module.Init()
}

func (object *objectLinker) appendLdflags(flags []string) {
	panic(fmt.Errorf("appendLdflags on object Linker not supported"))
}

func (object *objectLinker) linkerProps() []interface{} {
	return []interface{}{&object.Properties}
}

func (*objectLinker) linkerInit(ctx BaseModuleContext) {}

func (object *objectLinker) linkerDeps(ctx DepsContext, deps Deps) Deps {
	deps.ObjFiles = append(deps.ObjFiles, object.Properties.Objs...)
	return deps
}

func (*objectLinker) linkerFlags(ctx ModuleContext, flags Flags) Flags {
	if flags.Clang {
		flags.LdFlags = append(flags.LdFlags, ctx.toolchain().ToolchainClangLdflags())
	} else {
		flags.LdFlags = append(flags.LdFlags, ctx.toolchain().ToolchainLdflags())
	}

	return flags
}

func (object *objectLinker) link(ctx ModuleContext,
	flags Flags, deps PathDeps, objs Objects) android.Path {

	objs = objs.Append(deps.Objs)

	var outputFile android.Path
	if len(objs.objFiles) == 1 {
		outputFile = objs.objFiles[0]
	} else {
		output := android.PathForModuleOut(ctx, ctx.ModuleName()+objectExtension)
		TransformObjsToObj(ctx, objs.objFiles, flagsToBuilderFlags(flags), output)
		outputFile = output
	}

	ctx.CheckbuildFile(outputFile)
	return outputFile
}
