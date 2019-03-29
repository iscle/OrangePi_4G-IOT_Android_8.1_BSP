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

package config

import (
	"strings"

	"android/soong/android"
)

var (
	windowsCflags = []string{
		"-fno-exceptions", // from build/core/combo/select.mk
		"-Wno-multichar",  // from build/core/combo/select.mk

		"-DUSE_MINGW",
		"-DWIN32_LEAN_AND_MEAN",
		"-Wno-unused-parameter",

		// Workaround differences in inttypes.h between host and target.
		//See bug 12708004.
		"-D__STDC_FORMAT_MACROS",
		"-D__STDC_CONSTANT_MACROS",

		// Use C99-compliant printf functions (%zd).
		"-D__USE_MINGW_ANSI_STDIO=1",
		// Admit to using >= Vista. Both are needed because of <_mingw.h>.
		"-D_WIN32_WINNT=0x0600",
		"-DWINVER=0x0600",
		// Get 64-bit off_t and related functions.
		"-D_FILE_OFFSET_BITS=64",

		"--sysroot ${WindowsGccRoot}/${WindowsGccTriple}",

		// HOST_RELEASE_CFLAGS
		"-O2", // from build/core/combo/select.mk
		"-g",  // from build/core/combo/select.mk
		"-fno-strict-aliasing", // from build/core/combo/select.mk
	}

	windowsIncludeFlags = []string{
		"-isystem ${WindowsGccRoot}/${WindowsGccTriple}/include",
		"-isystem ${WindowsGccRoot}/lib/gcc/${WindowsGccTriple}/4.8.3/include",
	}

	windowsLdflags = []string{
		"--enable-stdcall-fixup",
	}

	windowsX86Cflags = []string{
		"-m32",
	}

	windowsX8664Cflags = []string{
		"-m64",
	}

	windowsX86Ldflags = []string{
		"-m32",
		"-Wl,--large-address-aware",
		"-L${WindowsGccRoot}/${WindowsGccTriple}/lib32",
	}

	windowsX8664Ldflags = []string{
		"-m64",
		"-L${WindowsGccRoot}/${WindowsGccTriple}/lib64",
	}

	windowsAvailableLibraries = addPrefix([]string{
		"gdi32",
		"imagehlp",
		"ole32",
		"psapi",
		"pthread",
		"userenv",
		"uuid",
		"version",
		"ws2_32",
	}, "-l")
)

const (
	windowsGccVersion = "4.8"
)

func init() {
	pctx.StaticVariable("WindowsGccVersion", windowsGccVersion)

	pctx.SourcePathVariable("WindowsGccRoot",
		"prebuilts/gcc/${HostPrebuiltTag}/host/x86_64-w64-mingw32-${WindowsGccVersion}")

	pctx.StaticVariable("WindowsGccTriple", "x86_64-w64-mingw32")

	pctx.StaticVariable("WindowsCflags", strings.Join(windowsCflags, " "))
	pctx.StaticVariable("WindowsLdflags", strings.Join(windowsLdflags, " "))

	pctx.StaticVariable("WindowsX86Cflags", strings.Join(windowsX86Cflags, " "))
	pctx.StaticVariable("WindowsX8664Cflags", strings.Join(windowsX8664Cflags, " "))
	pctx.StaticVariable("WindowsX86Ldflags", strings.Join(windowsX86Ldflags, " "))
	pctx.StaticVariable("WindowsX8664Ldflags", strings.Join(windowsX8664Ldflags, " "))

	pctx.StaticVariable("WindowsIncludeFlags", strings.Join(windowsIncludeFlags, " "))
}

type toolchainWindows struct {
	cFlags, ldFlags string
}

type toolchainWindowsX86 struct {
	toolchain32Bit
	toolchainWindows
}

type toolchainWindowsX8664 struct {
	toolchain64Bit
	toolchainWindows
}

func (t *toolchainWindowsX86) Name() string {
	return "x86"
}

func (t *toolchainWindowsX8664) Name() string {
	return "x86_64"
}

func (t *toolchainWindows) GccRoot() string {
	return "${config.WindowsGccRoot}"
}

func (t *toolchainWindows) GccTriple() string {
	return "${config.WindowsGccTriple}"
}

func (t *toolchainWindows) GccVersion() string {
	return windowsGccVersion
}

func (t *toolchainWindowsX86) Cflags() string {
	return "${config.WindowsCflags} ${config.WindowsX86Cflags}"
}

func (t *toolchainWindowsX8664) Cflags() string {
	return "${config.WindowsCflags} ${config.WindowsX8664Cflags}"
}

func (t *toolchainWindows) Cppflags() string {
	return ""
}

func (t *toolchainWindowsX86) Ldflags() string {
	return "${config.WindowsLdflags} ${config.WindowsX86Ldflags}"
}

func (t *toolchainWindowsX8664) Ldflags() string {
	return "${config.WindowsLdflags} ${config.WindowsX8664Ldflags}"
}

func (t *toolchainWindows) IncludeFlags() string {
	return "${config.WindowsIncludeFlags}"
}

func (t *toolchainWindows) ClangSupported() bool {
	return false
}

func (t *toolchainWindows) ClangTriple() string {
	panic("Clang is not supported under mingw")
}

func (t *toolchainWindows) ClangCflags() string {
	panic("Clang is not supported under mingw")
}

func (t *toolchainWindows) ClangCppflags() string {
	panic("Clang is not supported under mingw")
}

func (t *toolchainWindows) ClangLdflags() string {
	panic("Clang is not supported under mingw")
}

func (t *toolchainWindows) ShlibSuffix() string {
	return ".dll"
}

func (t *toolchainWindows) ExecutableSuffix() string {
	return ".exe"
}

func (t *toolchainWindows) AvailableLibraries() []string {
	return windowsAvailableLibraries
}

func (t *toolchainWindows) Bionic() bool {
	return false
}

var toolchainWindowsX86Singleton Toolchain = &toolchainWindowsX86{}
var toolchainWindowsX8664Singleton Toolchain = &toolchainWindowsX8664{}

func windowsX86ToolchainFactory(arch android.Arch) Toolchain {
	return toolchainWindowsX86Singleton
}

func windowsX8664ToolchainFactory(arch android.Arch) Toolchain {
	return toolchainWindowsX8664Singleton
}

func init() {
	registerToolchainFactory(android.Windows, android.X86, windowsX86ToolchainFactory)
	registerToolchainFactory(android.Windows, android.X86_64, windowsX8664ToolchainFactory)
}
