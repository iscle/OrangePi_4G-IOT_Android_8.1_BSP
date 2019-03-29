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

package config

import (
	"strings"

	"android/soong/android"
)

var (
	linuxCflags = []string{
		"-fno-exceptions", // from build/core/combo/select.mk
		"-Wno-multichar",  // from build/core/combo/select.mk

		"-fdiagnostics-color",

		"-Wa,--noexecstack",

		"-fPIC",
		"-no-canonical-prefixes",

		"-U_FORTIFY_SOURCE",
		"-D_FORTIFY_SOURCE=2",
		"-fstack-protector",

		// Workaround differences in inttypes.h between host and target.
		//See bug 12708004.
		"-D__STDC_FORMAT_MACROS",
		"-D__STDC_CONSTANT_MACROS",

		// HOST_RELEASE_CFLAGS
		"-O2", // from build/core/combo/select.mk
		"-g",  // from build/core/combo/select.mk
		"-fno-strict-aliasing", // from build/core/combo/select.mk
	}

	linuxLdflags = []string{
		"-Wl,-z,noexecstack",
		"-Wl,-z,relro",
		"-Wl,-z,now",
		"-Wl,--no-undefined-version",
	}

	// Extended cflags
	linuxX86Cflags = []string{
		"-msse3",
		"-mfpmath=sse",
		"-m32",
		"-march=prescott",
		"-D_FILE_OFFSET_BITS=64",
		"-D_LARGEFILE_SOURCE=1",
	}

	linuxX8664Cflags = []string{
		"-m64",
	}

	linuxX86Ldflags = []string{
		"-m32",
	}

	linuxX8664Ldflags = []string{
		"-m64",
	}

	linuxClangCflags = append(ClangFilterUnknownCflags(linuxCflags), []string{
		"--gcc-toolchain=${LinuxGccRoot}",
		"--sysroot ${LinuxGccRoot}/sysroot",
		"-fstack-protector-strong",
	}...)

	linuxClangLdflags = append(ClangFilterUnknownCflags(linuxLdflags), []string{
		"--gcc-toolchain=${LinuxGccRoot}",
		"--sysroot ${LinuxGccRoot}/sysroot",
	}...)

	linuxX86ClangLdflags = append(ClangFilterUnknownCflags(linuxX86Ldflags), []string{
		"-B${LinuxGccRoot}/lib/gcc/${LinuxGccTriple}/${LinuxGccVersion}/32",
		"-L${LinuxGccRoot}/lib/gcc/${LinuxGccTriple}/${LinuxGccVersion}/32",
		"-L${LinuxGccRoot}/${LinuxGccTriple}/lib32",
	}...)

	linuxX8664ClangLdflags = append(ClangFilterUnknownCflags(linuxX8664Ldflags), []string{
		"-B${LinuxGccRoot}/lib/gcc/${LinuxGccTriple}/${LinuxGccVersion}",
		"-L${LinuxGccRoot}/lib/gcc/${LinuxGccTriple}/${LinuxGccVersion}",
		"-L${LinuxGccRoot}/${LinuxGccTriple}/lib64",
	}...)

	linuxClangCppflags = []string{
		"-isystem ${LinuxGccRoot}/${LinuxGccTriple}/include/c++/${LinuxGccVersion}",
		"-isystem ${LinuxGccRoot}/${LinuxGccTriple}/include/c++/${LinuxGccVersion}/backward",
	}

	linuxX86ClangCppflags = []string{
		"-isystem ${LinuxGccRoot}/${LinuxGccTriple}/include/c++/${LinuxGccVersion}/${LinuxGccTriple}/32",
	}

	linuxX8664ClangCppflags = []string{
		"-isystem ${LinuxGccRoot}/${LinuxGccTriple}/include/c++/${LinuxGccVersion}/${LinuxGccTriple}",
	}

	linuxAvailableLibraries = addPrefix([]string{
		"c",
		"dl",
		"gcc",
		"gcc_s",
		"m",
		"ncurses",
		"pthread",
		"resolv",
		"rt",
		"util",
		"z",
	}, "-l")
)

const (
	linuxGccVersion = "4.8"
)

func init() {
	pctx.StaticVariable("LinuxGccVersion", linuxGccVersion)

	pctx.SourcePathVariable("LinuxGccRoot",
		"prebuilts/gcc/${HostPrebuiltTag}/host/x86_64-linux-glibc2.15-${LinuxGccVersion}")

	pctx.StaticVariable("LinuxGccTriple", "x86_64-linux")

	pctx.StaticVariable("LinuxCflags", strings.Join(linuxCflags, " "))
	pctx.StaticVariable("LinuxLdflags", strings.Join(linuxLdflags, " "))

	pctx.StaticVariable("LinuxClangCflags", strings.Join(linuxClangCflags, " "))
	pctx.StaticVariable("LinuxClangLdflags", strings.Join(linuxClangLdflags, " "))
	pctx.StaticVariable("LinuxClangCppflags", strings.Join(linuxClangCppflags, " "))

	// Extended cflags
	pctx.StaticVariable("LinuxX86Cflags", strings.Join(linuxX86Cflags, " "))
	pctx.StaticVariable("LinuxX8664Cflags", strings.Join(linuxX8664Cflags, " "))
	pctx.StaticVariable("LinuxX86Ldflags", strings.Join(linuxX86Ldflags, " "))
	pctx.StaticVariable("LinuxX8664Ldflags", strings.Join(linuxX8664Ldflags, " "))

	pctx.StaticVariable("LinuxX86ClangCflags",
		strings.Join(ClangFilterUnknownCflags(linuxX86Cflags), " "))
	pctx.StaticVariable("LinuxX8664ClangCflags",
		strings.Join(ClangFilterUnknownCflags(linuxX8664Cflags), " "))
	pctx.StaticVariable("LinuxX86ClangLdflags", strings.Join(linuxX86ClangLdflags, " "))
	pctx.StaticVariable("LinuxX8664ClangLdflags", strings.Join(linuxX8664ClangLdflags, " "))
	pctx.StaticVariable("LinuxX86ClangCppflags", strings.Join(linuxX86ClangCppflags, " "))
	pctx.StaticVariable("LinuxX8664ClangCppflags", strings.Join(linuxX8664ClangCppflags, " "))
	// Yasm flags
	pctx.StaticVariable("LinuxX86YasmFlags", "-f elf32 -m x86")
	pctx.StaticVariable("LinuxX8664YasmFlags", "-f elf64 -m amd64")
}

type toolchainLinux struct {
	cFlags, ldFlags string
}

type toolchainLinuxX86 struct {
	toolchain32Bit
	toolchainLinux
}

type toolchainLinuxX8664 struct {
	toolchain64Bit
	toolchainLinux
}

func (t *toolchainLinuxX86) Name() string {
	return "x86"
}

func (t *toolchainLinuxX8664) Name() string {
	return "x86_64"
}

func (t *toolchainLinux) GccRoot() string {
	return "${config.LinuxGccRoot}"
}

func (t *toolchainLinux) GccTriple() string {
	return "${config.LinuxGccTriple}"
}

func (t *toolchainLinux) GccVersion() string {
	return linuxGccVersion
}

func (t *toolchainLinuxX86) Cflags() string {
	return "${config.LinuxCflags} ${config.LinuxX86Cflags}"
}

func (t *toolchainLinuxX8664) Cflags() string {
	return "${config.LinuxCflags} ${config.LinuxX8664Cflags}"
}

func (t *toolchainLinux) Cppflags() string {
	return ""
}

func (t *toolchainLinuxX86) Ldflags() string {
	return "${config.LinuxLdflags} ${config.LinuxX86Ldflags}"
}

func (t *toolchainLinuxX8664) Ldflags() string {
	return "${config.LinuxLdflags} ${config.LinuxX8664Ldflags}"
}

func (t *toolchainLinux) IncludeFlags() string {
	return ""
}

func (t *toolchainLinuxX86) ClangTriple() string {
	return "i686-linux-gnu"
}

func (t *toolchainLinuxX86) ClangCflags() string {
	return "${config.LinuxClangCflags} ${config.LinuxX86ClangCflags}"
}

func (t *toolchainLinuxX86) ClangCppflags() string {
	return "${config.LinuxClangCppflags} ${config.LinuxX86ClangCppflags}"
}

func (t *toolchainLinuxX8664) ClangTriple() string {
	return "x86_64-linux-gnu"
}

func (t *toolchainLinuxX8664) ClangCflags() string {
	return "${config.LinuxClangCflags} ${config.LinuxX8664ClangCflags}"
}

func (t *toolchainLinuxX8664) ClangCppflags() string {
	return "${config.LinuxClangCppflags} ${config.LinuxX8664ClangCppflags}"
}

func (t *toolchainLinuxX86) ClangLdflags() string {
	return "${config.LinuxClangLdflags} ${config.LinuxX86ClangLdflags}"
}

func (t *toolchainLinuxX8664) ClangLdflags() string {
	return "${config.LinuxClangLdflags} ${config.LinuxX8664ClangLdflags}"
}

func (t *toolchainLinuxX86) YasmFlags() string {
	return "${config.LinuxX86YasmFlags}"
}

func (t *toolchainLinuxX8664) YasmFlags() string {
	return "${config.LinuxX8664YasmFlags}"
}

func (t *toolchainLinux) AvailableLibraries() []string {
	return linuxAvailableLibraries
}

func (t *toolchainLinux) Bionic() bool {
	return false
}

var toolchainLinuxX86Singleton Toolchain = &toolchainLinuxX86{}
var toolchainLinuxX8664Singleton Toolchain = &toolchainLinuxX8664{}

func linuxX86ToolchainFactory(arch android.Arch) Toolchain {
	return toolchainLinuxX86Singleton
}

func linuxX8664ToolchainFactory(arch android.Arch) Toolchain {
	return toolchainLinuxX8664Singleton
}

func init() {
	registerToolchainFactory(android.Linux, android.X86, linuxX86ToolchainFactory)
	registerToolchainFactory(android.Linux, android.X86_64, linuxX8664ToolchainFactory)
}
