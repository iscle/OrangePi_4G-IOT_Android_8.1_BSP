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
	"fmt"
)

type StlProperties struct {
	// select the STL library to use.  Possible values are "libc++", "libc++_static",
	// "stlport", "stlport_static", "ndk", "libstdc++", or "none".  Leave blank to select the
	// default
	Stl *string

	SelectedStl string `blueprint:"mutated"`
}

type stl struct {
	Properties StlProperties
}

func (stl *stl) props() []interface{} {
	return []interface{}{&stl.Properties}
}

func (stl *stl) begin(ctx BaseModuleContext) {
	stl.Properties.SelectedStl = func() string {
		s := ""
		if stl.Properties.Stl != nil {
			s = *stl.Properties.Stl
		}
		if ctx.sdk() && ctx.Device() {
			switch s {
			case "":
				return "ndk_system"
			case "c++_shared", "c++_static",
				"stlport_shared", "stlport_static",
				"gnustl_static":
				return "ndk_lib" + s
			case "libc++":
				return "ndk_libc++_shared"
			case "libc++_static":
				return "ndk_libc++_static"
			case "none":
				return ""
			default:
				ctx.ModuleErrorf("stl: %q is not a supported STL with sdk_version set", s)
				return ""
			}
		} else if ctx.Windows() {
			switch s {
			case "libc++", "libc++_static", "libstdc++", "":
				// libc++ is not supported on mingw
				return "libstdc++"
			case "none":
				return ""
			default:
				ctx.ModuleErrorf("stl: %q is not a supported STL for windows", s)
				return ""
			}
		} else {
			switch s {
			case "libc++", "libc++_static":
				return s
			case "none":
				return ""
			case "":
				if ctx.static() {
					return "libc++_static"
				} else {
					return "libc++"
				}
			default:
				ctx.ModuleErrorf("stl: %q is not a supported STL", s)
				return ""
			}
		}
	}()
}

func (stl *stl) deps(ctx BaseModuleContext, deps Deps) Deps {
	switch stl.Properties.SelectedStl {
	case "libstdc++":
		// Nothing
	case "libc++", "libc++_static":
		if stl.Properties.SelectedStl == "libc++" {
			deps.SharedLibs = append(deps.SharedLibs, stl.Properties.SelectedStl)
		} else {
			deps.StaticLibs = append(deps.StaticLibs, stl.Properties.SelectedStl)
		}
		if ctx.toolchain().Bionic() {
			if ctx.Arch().ArchType == android.Arm {
				deps.StaticLibs = append(deps.StaticLibs, "libunwind_llvm")
			}
			if ctx.staticBinary() {
				deps.StaticLibs = append(deps.StaticLibs, "libm", "libc", "libdl")
			}
		}
	case "":
		// None or error.
	case "ndk_system":
		// TODO: Make a system STL prebuilt for the NDK.
		// The system STL doesn't have a prebuilt (it uses the system's libstdc++), but it does have
		// its own includes. The includes are handled in CCBase.Flags().
		deps.SharedLibs = append([]string{"libstdc++"}, deps.SharedLibs...)
	case "ndk_libc++_shared", "ndk_libstlport_shared":
		deps.SharedLibs = append(deps.SharedLibs, stl.Properties.SelectedStl)
	case "ndk_libc++_static", "ndk_libstlport_static", "ndk_libgnustl_static":
		deps.StaticLibs = append(deps.StaticLibs, stl.Properties.SelectedStl)
	default:
		panic(fmt.Errorf("Unknown stl: %q", stl.Properties.SelectedStl))
	}

	return deps
}

func (stl *stl) flags(ctx ModuleContext, flags Flags) Flags {
	switch stl.Properties.SelectedStl {
	case "libc++", "libc++_static":
		flags.CFlags = append(flags.CFlags, "-D_USING_LIBCXX")
		if !ctx.toolchain().Bionic() {
			flags.CppFlags = append(flags.CppFlags, "-nostdinc++")
			flags.LdFlags = append(flags.LdFlags, "-nodefaultlibs")
			flags.LdFlags = append(flags.LdFlags, "-lpthread", "-lm")
			if ctx.staticBinary() {
				flags.LdFlags = append(flags.LdFlags, hostStaticGccLibs[ctx.Os()]...)
			} else {
				flags.LdFlags = append(flags.LdFlags, hostDynamicGccLibs[ctx.Os()]...)
			}
		} else {
			if ctx.Arch().ArchType == android.Arm {
				flags.LdFlags = append(flags.LdFlags, "-Wl,--exclude-libs,libunwind_llvm.a")
			}
		}
	case "libstdc++":
		// Nothing
	case "ndk_system":
		ndkSrcRoot := android.PathForSource(ctx, "prebuilts/ndk/current/sources/cxx-stl/system/include")
		flags.CFlags = append(flags.CFlags, "-isystem "+ndkSrcRoot.String())
	case "ndk_libc++_shared", "ndk_libc++_static":
		// TODO(danalbert): This really shouldn't be here...
		flags.CppFlags = append(flags.CppFlags, "-std=c++11")
	case "ndk_libstlport_shared", "ndk_libstlport_static", "ndk_libgnustl_static":
		// Nothing
	case "":
		// None or error.
		if !ctx.toolchain().Bionic() {
			flags.CppFlags = append(flags.CppFlags, "-nostdinc++")
			flags.LdFlags = append(flags.LdFlags, "-nodefaultlibs")
			if ctx.staticBinary() {
				flags.LdFlags = append(flags.LdFlags, hostStaticGccLibs[ctx.Os()]...)
			} else {
				flags.LdFlags = append(flags.LdFlags, hostDynamicGccLibs[ctx.Os()]...)
			}
		}
	default:
		panic(fmt.Errorf("Unknown stl: %q", stl.Properties.SelectedStl))
	}

	return flags
}

var hostDynamicGccLibs, hostStaticGccLibs map[android.OsType][]string

func init() {
	hostDynamicGccLibs = map[android.OsType][]string{
		android.Linux:  []string{"-lgcc_s", "-lgcc", "-lc", "-lgcc_s", "-lgcc"},
		android.Darwin: []string{"-lc", "-lSystem"},
		android.Windows: []string{"-lmsvcr110", "-lmingw32", "-lgcc", "-lmoldname",
			"-lmingwex", "-lmsvcrt", "-ladvapi32", "-lshell32", "-luser32",
			"-lkernel32", "-lmingw32", "-lgcc", "-lmoldname", "-lmingwex",
			"-lmsvcrt"},
	}
	hostStaticGccLibs = map[android.OsType][]string{
		android.Linux:   []string{"-Wl,--start-group", "-lgcc", "-lgcc_eh", "-lc", "-Wl,--end-group"},
		android.Darwin:  []string{"NO_STATIC_HOST_BINARIES_ON_DARWIN"},
		android.Windows: []string{"NO_STATIC_HOST_BINARIES_ON_WINDOWS"},
	}
}
