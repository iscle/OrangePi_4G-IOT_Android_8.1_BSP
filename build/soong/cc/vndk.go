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
	"sort"
	"strings"
	"sync"

	"android/soong/android"
)

type VndkProperties struct {
	Vndk struct {
		// declared as a VNDK or VNDK-SP module. The vendor variant
		// will be installed in /system instead of /vendor partition.
		//
		// `vendor_available: true` must set to together for VNDK
		// modules.
		Enabled *bool

		// declared as a VNDK-SP module, which is a subset of VNDK.
		//
		// `vndk: { enabled: true }` must set together.
		//
		// All these modules are allowed to link to VNDK-SP or LL-NDK
		// modules only. Other dependency will cause link-type errors.
		//
		// If `support_system_process` is not set or set to false,
		// the module is VNDK-core and can link to other VNDK-core,
		// VNDK-SP or LL-NDK modules only.
		Support_system_process *bool
	}
}

type vndkdep struct {
	Properties VndkProperties
}

func (vndk *vndkdep) props() []interface{} {
	return []interface{}{&vndk.Properties}
}

func (vndk *vndkdep) begin(ctx BaseModuleContext) {}

func (vndk *vndkdep) deps(ctx BaseModuleContext, deps Deps) Deps {
	return deps
}

func (vndk *vndkdep) isVndk() bool {
	return Bool(vndk.Properties.Vndk.Enabled)
}

func (vndk *vndkdep) isVndkSp() bool {
	return Bool(vndk.Properties.Vndk.Support_system_process)
}

func (vndk *vndkdep) typeName() string {
	if !vndk.isVndk() {
		return "native:vendor"
	}
	if !vndk.isVndkSp() {
		return "native:vendor:vndk"
	}
	return "native:vendor:vndksp"
}

func (vndk *vndkdep) vndkCheckLinkType(ctx android.ModuleContext, to *Module) {
	if to.linker == nil {
		return
	}
	if lib, ok := to.linker.(*libraryDecorator); !ok || !lib.shared() {
		// Check only shared libraries.
		// Other (static and LL-NDK) libraries are allowed to link.
		return
	}
	if !to.Properties.UseVndk {
		ctx.ModuleErrorf("(%s) should not link to %q which is not a vendor-available library",
			vndk.typeName(), to.Name())
		return
	}
	if to.vndkdep == nil {
		return
	}
	if (vndk.isVndk() && !to.vndkdep.isVndk()) || (vndk.isVndkSp() && !to.vndkdep.isVndkSp()) {
		ctx.ModuleErrorf("(%s) should not link to %q(%s)",
			vndk.typeName(), to.Name(), to.vndkdep.typeName())
		return
	}
}

var (
	vndkCoreLibraries []string
	vndkSpLibraries   []string
	llndkLibraries    []string
	vndkLibrariesLock sync.Mutex
)

// gather list of vndk-core, vndk-sp, and ll-ndk libs
func vndkMutator(mctx android.BottomUpMutatorContext) {
	if m, ok := mctx.Module().(*Module); ok {
		if _, ok := m.linker.(*llndkStubDecorator); ok {
			vndkLibrariesLock.Lock()
			defer vndkLibrariesLock.Unlock()
			name := strings.TrimSuffix(m.Name(), llndkLibrarySuffix)
			if !inList(name, llndkLibraries) {
				llndkLibraries = append(llndkLibraries, name)
				sort.Strings(llndkLibraries)
			}
		} else if lib, ok := m.linker.(*libraryDecorator); ok && lib.shared() {
			if m.vndkdep.isVndk() {
				vndkLibrariesLock.Lock()
				defer vndkLibrariesLock.Unlock()
				if m.vndkdep.isVndkSp() {
					if !inList(m.Name(), vndkSpLibraries) {
						vndkSpLibraries = append(vndkSpLibraries, m.Name())
						sort.Strings(vndkSpLibraries)
					}
				} else {
					if !inList(m.Name(), vndkCoreLibraries) {
						vndkCoreLibraries = append(vndkCoreLibraries, m.Name())
						sort.Strings(vndkCoreLibraries)
					}
				}
			}
		}
	}
}
