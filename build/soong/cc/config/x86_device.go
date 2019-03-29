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
	x86Cflags = []string{
		"-fno-exceptions", // from build/core/combo/select.mk
		"-Wno-multichar",  // from build/core/combo/select.mk
		"-O2",
		"-Wa,--noexecstack",
		"-Werror=format-security",
		"-D_FORTIFY_SOURCE=2",
		"-Wstrict-aliasing=2",
		"-ffunction-sections",
		"-finline-functions",
		"-finline-limit=300",
		"-fno-short-enums",
		"-fstrict-aliasing",
		"-funswitch-loops",
		"-funwind-tables",
		"-fstack-protector-strong",
		"-no-canonical-prefixes",
		"-fno-canonical-system-headers",

		// TARGET_RELEASE_CFLAGS from build/core/combo/select.mk
		"-O2",
		"-g",
		"-fno-strict-aliasing",
	}

	x86ClangCflags = append(x86Cflags, []string{
		"-msse3",

		// -mstackrealign is needed to realign stack in native code
		// that could be called from JNI, so that movaps instruction
		// will work on assumed stack aligned local variables.
		"-mstackrealign",
	}...)

	x86Cppflags = []string{}

	x86Ldflags = []string{
		"-Wl,-z,noexecstack",
		"-Wl,-z,relro",
		"-Wl,-z,now",
		"-Wl,--build-id=md5",
		"-Wl,--warn-shared-textrel",
		"-Wl,--fatal-warnings",
		"-Wl,--gc-sections",
		"-Wl,--hash-style=gnu",
		"-Wl,--no-undefined-version",
	}

	x86ArchVariantCflags = map[string][]string{
		"": []string{
			"-march=prescott",
		},
		"x86_64": []string{
			"-march=prescott",
		},
		"atom": []string{
			"-march=atom",
			"-mfpmath=sse",
		},
		"haswell": []string{
			"-march=core-avx2",
			"-mfpmath=sse",
		},
		"ivybridge": []string{
			"-march=core-avx-i",
			"-mfpmath=sse",
		},
		"sandybridge": []string{
			"-march=corei7",
			"-mfpmath=sse",
		},
		"silvermont": []string{
			"-march=slm",
			"-mfpmath=sse",
		},
	}

	x86ArchFeatureCflags = map[string][]string{
		"ssse3":  []string{"-DUSE_SSSE3", "-mssse3"},
		"sse4":   []string{"-msse4"},
		"sse4_1": []string{"-msse4.1"},
		"sse4_2": []string{"-msse4.2"},
		"avx":    []string{"-mavx"},
		"aes_ni": []string{"-maes"},
	}
)

const (
	x86GccVersion = "4.9"
)

func init() {
	android.RegisterArchVariants(android.X86,
		"atom",
		"haswell",
		"ivybridge",
		"sandybridge",
		"silvermont",
		"x86_64")
	android.RegisterArchFeatures(android.X86,
		"ssse3",
		"sse4",
		"sse4_1",
		"sse4_2",
		"aes_ni",
		"avx",
		"popcnt",
		"movbe")
	android.RegisterArchVariantFeatures(android.X86, "x86_64",
		"ssse3",
		"sse4",
		"sse4_1",
		"sse4_2",
		"popcnt")
	android.RegisterArchVariantFeatures(android.X86, "atom",
		"ssse3",
		"movbe")
	android.RegisterArchVariantFeatures(android.X86, "haswell",
		"ssse3",
		"sse4",
		"sse4_1",
		"sse4_2",
		"aes_ni",
		"avx",
		"popcnt",
		"movbe")
	android.RegisterArchVariantFeatures(android.X86, "ivybridge",
		"ssse3",
		"sse4",
		"sse4_1",
		"sse4_2",
		"aes_ni",
		"avx",
		"popcnt")
	android.RegisterArchVariantFeatures(android.X86, "sandybridge",
		"ssse3",
		"sse4",
		"sse4_1",
		"sse4_2",
		"popcnt")
	android.RegisterArchVariantFeatures(android.X86, "silvermont",
		"ssse3",
		"sse4",
		"sse4_1",
		"sse4_2",
		"aes_ni",
		"popcnt",
		"movbe")

	pctx.StaticVariable("x86GccVersion", x86GccVersion)

	pctx.SourcePathVariable("X86GccRoot",
		"prebuilts/gcc/${HostPrebuiltTag}/x86/x86_64-linux-android-${x86GccVersion}")

	pctx.StaticVariable("X86ToolchainCflags", "-m32")
	pctx.StaticVariable("X86ToolchainLdflags", "-m32")

	pctx.StaticVariable("X86Cflags", strings.Join(x86Cflags, " "))
	pctx.StaticVariable("X86Ldflags", strings.Join(x86Ldflags, " "))
	pctx.StaticVariable("X86Cppflags", strings.Join(x86Cppflags, " "))
	pctx.StaticVariable("X86IncludeFlags", bionicHeaders("x86", "x86"))

	// Clang cflags
	pctx.StaticVariable("X86ClangCflags", strings.Join(ClangFilterUnknownCflags(x86ClangCflags), " "))
	pctx.StaticVariable("X86ClangLdflags", strings.Join(ClangFilterUnknownCflags(x86Ldflags), " "))
	pctx.StaticVariable("X86ClangCppflags", strings.Join(ClangFilterUnknownCflags(x86Cppflags), " "))

	// Yasm flags
	pctx.StaticVariable("X86YasmFlags", "-f elf32 -m x86")

	// Extended cflags

	// Architecture variant cflags
	for variant, cflags := range x86ArchVariantCflags {
		pctx.StaticVariable("X86"+variant+"VariantCflags", strings.Join(cflags, " "))
		pctx.StaticVariable("X86"+variant+"VariantClangCflags",
			strings.Join(ClangFilterUnknownCflags(cflags), " "))
	}
}

type toolchainX86 struct {
	toolchain32Bit
	toolchainCflags, toolchainClangCflags string
}

func (t *toolchainX86) Name() string {
	return "x86"
}

func (t *toolchainX86) GccRoot() string {
	return "${config.X86GccRoot}"
}

func (t *toolchainX86) GccTriple() string {
	return "x86_64-linux-android"
}

func (t *toolchainX86) GccVersion() string {
	return x86GccVersion
}

func (t *toolchainX86) ToolchainLdflags() string {
	return "${config.X86ToolchainLdflags}"
}

func (t *toolchainX86) ToolchainCflags() string {
	return t.toolchainCflags
}

func (t *toolchainX86) Cflags() string {
	return "${config.X86Cflags}"
}

func (t *toolchainX86) Cppflags() string {
	return "${config.X86Cppflags}"
}

func (t *toolchainX86) Ldflags() string {
	return "${config.X86Ldflags}"
}

func (t *toolchainX86) IncludeFlags() string {
	return "${config.X86IncludeFlags}"
}

func (t *toolchainX86) ClangTriple() string {
	return "i686-linux-android"
}

func (t *toolchainX86) ToolchainClangLdflags() string {
	return "${config.X86ToolchainLdflags}"
}

func (t *toolchainX86) ToolchainClangCflags() string {
	return t.toolchainClangCflags
}

func (t *toolchainX86) ClangCflags() string {
	return "${config.X86ClangCflags}"
}

func (t *toolchainX86) ClangCppflags() string {
	return "${config.X86ClangCppflags}"
}

func (t *toolchainX86) ClangLdflags() string {
	return "${config.X86Ldflags}"
}

func (t *toolchainX86) YasmFlags() string {
	return "${config.X86YasmFlags}"
}

func (toolchainX86) SanitizerRuntimeLibraryArch() string {
	return "i686"
}

func x86ToolchainFactory(arch android.Arch) Toolchain {
	toolchainCflags := []string{
		"${config.X86ToolchainCflags}",
		"${config.X86" + arch.ArchVariant + "VariantCflags}",
	}

	toolchainClangCflags := []string{
		"${config.X86ToolchainCflags}",
		"${config.X86" + arch.ArchVariant + "VariantClangCflags}",
	}

	for _, feature := range arch.ArchFeatures {
		toolchainCflags = append(toolchainCflags, x86ArchFeatureCflags[feature]...)
		toolchainClangCflags = append(toolchainClangCflags, x86ArchFeatureCflags[feature]...)
	}

	return &toolchainX86{
		toolchainCflags:      strings.Join(toolchainCflags, " "),
		toolchainClangCflags: strings.Join(toolchainClangCflags, " "),
	}
}

func init() {
	registerToolchainFactory(android.Android, android.X86, x86ToolchainFactory)
}
