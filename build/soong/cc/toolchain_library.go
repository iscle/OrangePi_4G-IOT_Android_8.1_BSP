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
	"github.com/google/blueprint/proptools"

	"android/soong/android"
)

//
// Device libraries shipped with gcc
//

func init() {
	android.RegisterModuleType("toolchain_library", toolchainLibraryFactory)
}

type toolchainLibraryDecorator struct {
	*libraryDecorator
}

func (*toolchainLibraryDecorator) linkerDeps(ctx DepsContext, deps Deps) Deps {
	// toolchain libraries can't have any dependencies
	return deps
}

func toolchainLibraryFactory() android.Module {
	module, library := NewLibrary(android.HostAndDeviceSupported)
	library.BuildOnlyStatic()
	toolchainLibrary := &toolchainLibraryDecorator{
		libraryDecorator: library,
	}
	module.compiler = toolchainLibrary
	module.linker = toolchainLibrary
	module.Properties.Clang = proptools.BoolPtr(false)
	module.stl = nil
	module.sanitize = nil
	module.installer = nil
	return module.Init()
}

func (library *toolchainLibraryDecorator) compile(ctx ModuleContext, flags Flags,
	deps PathDeps) Objects {
	return Objects{}
}

func (library *toolchainLibraryDecorator) link(ctx ModuleContext,
	flags Flags, deps PathDeps, objs Objects) android.Path {

	libName := ctx.ModuleName() + staticLibraryExtension
	outputFile := android.PathForModuleOut(ctx, libName)

	if flags.Clang {
		ctx.ModuleErrorf("toolchain_library must use GCC, not Clang")
	}

	CopyGccLib(ctx, libName, flagsToBuilderFlags(flags), outputFile)

	ctx.CheckbuildFile(outputFile)

	return outputFile
}
