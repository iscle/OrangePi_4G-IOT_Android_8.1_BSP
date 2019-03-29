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
	"android/soong/android"
)

func init() {
	android.RegisterModuleType("cc_prebuilt_library_shared", prebuiltSharedLibraryFactory)
	android.RegisterModuleType("cc_prebuilt_library_static", prebuiltStaticLibraryFactory)
	android.RegisterModuleType("cc_prebuilt_binary", prebuiltBinaryFactory)
}

type prebuiltLinkerInterface interface {
	Name(string) string
	prebuilt() *android.Prebuilt
}

type prebuiltLinker struct {
	android.Prebuilt
}

func (p *prebuiltLinker) prebuilt() *android.Prebuilt {
	return &p.Prebuilt
}

type prebuiltLibraryLinker struct {
	*libraryDecorator
	prebuiltLinker
}

var _ prebuiltLinkerInterface = (*prebuiltLibraryLinker)(nil)

func (p *prebuiltLibraryLinker) linkerProps() []interface{} {
	props := p.libraryDecorator.linkerProps()
	return append(props, &p.Prebuilt.Properties)
}

func (p *prebuiltLibraryLinker) link(ctx ModuleContext,
	flags Flags, deps PathDeps, objs Objects) android.Path {
	// TODO(ccross): verify shared library dependencies
	if len(p.Prebuilt.Properties.Srcs) > 0 {
		p.libraryDecorator.exportIncludes(ctx, "-I")
		p.libraryDecorator.reexportFlags(deps.ReexportedFlags)
		p.libraryDecorator.reexportDeps(deps.ReexportedFlagsDeps)
		// TODO(ccross): .toc optimization, stripping, packing
		return p.Prebuilt.Path(ctx)
	}

	return nil
}

func prebuiltSharedLibraryFactory() android.Module {
	module, _ := NewPrebuiltSharedLibrary(android.HostAndDeviceSupported)
	return module.Init()
}

func NewPrebuiltSharedLibrary(hod android.HostOrDeviceSupported) (*Module, *libraryDecorator) {
	module, library := NewLibrary(hod)
	library.BuildOnlyShared()
	module.compiler = nil

	prebuilt := &prebuiltLibraryLinker{
		libraryDecorator: library,
	}
	module.linker = prebuilt

	return module, library
}

func prebuiltStaticLibraryFactory() android.Module {
	module, _ := NewPrebuiltStaticLibrary(android.HostAndDeviceSupported)
	return module.Init()
}

func NewPrebuiltStaticLibrary(hod android.HostOrDeviceSupported) (*Module, *libraryDecorator) {
	module, library := NewLibrary(hod)
	library.BuildOnlyStatic()
	module.compiler = nil

	prebuilt := &prebuiltLibraryLinker{
		libraryDecorator: library,
	}
	module.linker = prebuilt

	return module, library
}

type prebuiltBinaryLinker struct {
	*binaryDecorator
	prebuiltLinker
}

var _ prebuiltLinkerInterface = (*prebuiltBinaryLinker)(nil)

func (p *prebuiltBinaryLinker) linkerProps() []interface{} {
	props := p.binaryDecorator.linkerProps()
	return append(props, &p.Prebuilt.Properties)
}

func (p *prebuiltBinaryLinker) link(ctx ModuleContext,
	flags Flags, deps PathDeps, objs Objects) android.Path {
	// TODO(ccross): verify shared library dependencies
	if len(p.Prebuilt.Properties.Srcs) > 0 {
		// TODO(ccross): .toc optimization, stripping, packing
		return p.Prebuilt.Path(ctx)
	}

	return nil
}

func prebuiltBinaryFactory() android.Module {
	module, _ := NewPrebuiltBinary(android.HostAndDeviceSupported)
	return module.Init()
}

func NewPrebuiltBinary(hod android.HostOrDeviceSupported) (*Module, *binaryDecorator) {
	module, binary := NewBinary(hod)
	module.compiler = nil

	prebuilt := &prebuiltBinaryLinker{
		binaryDecorator: binary,
	}
	module.linker = prebuilt

	return module, binary
}
