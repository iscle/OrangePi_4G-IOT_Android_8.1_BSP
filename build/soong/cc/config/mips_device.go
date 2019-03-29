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
	mipsCflags = []string{
		"-fno-exceptions", // from build/core/combo/select.mk
		"-Wno-multichar",  // from build/core/combo/select.mk
		"-O2",
		"-fomit-frame-pointer",
		"-fno-strict-aliasing",
		"-funswitch-loops",
		"-U__unix",
		"-U__unix__",
		"-Umips",
		"-ffunction-sections",
		"-fdata-sections",
		"-funwind-tables",
		"-fstack-protector-strong",
		"-Wa,--noexecstack",
		"-Werror=format-security",
		"-D_FORTIFY_SOURCE=2",
		"-no-canonical-prefixes",
		"-fno-canonical-system-headers",

		// TARGET_RELEASE_CFLAGS
		"-DNDEBUG",
		"-g",
		"-Wstrict-aliasing=2",
		"-fgcse-after-reload",
		"-frerun-cse-after-loop",
		"-frename-registers",
	}

	mipsClangCflags = append(mipsCflags, []string{
		"-fPIC",
		"-fintegrated-as",
	}...)

	mipsCppflags = []string{
		"-fvisibility-inlines-hidden",
	}

	mipsLdflags = []string{
		"-Wl,-z,noexecstack",
		"-Wl,-z,relro",
		"-Wl,-z,now",
		"-Wl,--build-id=md5",
		"-Wl,--warn-shared-textrel",
		"-Wl,--fatal-warnings",
		"-Wl,--allow-shlib-undefined",
		"-Wl,--no-undefined-version",
	}

	mipsToolchainLdflags = []string{
		"-Wl,-melf32ltsmip",
	}

	mipsArchVariantCflags = map[string][]string{
		"mips32-fp": []string{
			"-mips32",
			"-mfp32",
			"-modd-spreg",
			"-mno-synci",
		},
		"mips32r2-fp": []string{
			"-mips32r2",
			"-mfp32",
			"-modd-spreg",
			"-msynci",
		},
		"mips32r2-fp-xburst": []string{
			"-mips32r2",
			"-mfp32",
			"-modd-spreg",
			"-mno-fused-madd",
			"-mno-synci",
		},
		"mips32r2dsp-fp": []string{
			"-mips32r2",
			"-mfp32",
			"-modd-spreg",
			"-mdsp",
			"-msynci",
		},
		"mips32r2dspr2-fp": []string{
			"-mips32r2",
			"-mfp32",
			"-modd-spreg",
			"-mdspr2",
			"-msynci",
		},
		"mips32r6": []string{
			"-mips32r6",
			"-mfp64",
			"-mno-odd-spreg",
			"-msynci",
		},
	}
)

const (
	mipsGccVersion = "4.9"
)

func init() {
	android.RegisterArchVariants(android.Mips,
		"mips32_fp",
		"mips32r2_fp",
		"mips32r2_fp_xburst",
		"mips32r2dsp_fp",
		"mips32r2dspr2_fp",
		"mips32r6")
	android.RegisterArchFeatures(android.Mips,
		"dspr2",
		"rev6",
		"msa")
	android.RegisterArchVariantFeatures(android.Mips, "mips32r2dspr2_fp",
		"dspr2")
	android.RegisterArchVariantFeatures(android.Mips, "mips32r6",
		"rev6")

	pctx.StaticVariable("mipsGccVersion", mipsGccVersion)

	pctx.SourcePathVariable("MipsGccRoot",
		"prebuilts/gcc/${HostPrebuiltTag}/mips/mips64el-linux-android-${mipsGccVersion}")

	pctx.StaticVariable("MipsToolchainLdflags", strings.Join(mipsToolchainLdflags, " "))
	pctx.StaticVariable("MipsCflags", strings.Join(mipsCflags, " "))
	pctx.StaticVariable("MipsLdflags", strings.Join(mipsLdflags, " "))
	pctx.StaticVariable("MipsCppflags", strings.Join(mipsCppflags, " "))
	pctx.StaticVariable("MipsIncludeFlags", bionicHeaders("mips", "mips"))

	// Clang cflags
	pctx.StaticVariable("MipsClangCflags", strings.Join(ClangFilterUnknownCflags(mipsClangCflags), " "))
	pctx.StaticVariable("MipsClangLdflags", strings.Join(ClangFilterUnknownCflags(mipsLdflags), " "))
	pctx.StaticVariable("MipsClangCppflags", strings.Join(ClangFilterUnknownCflags(mipsCppflags), " "))

	// Extended cflags

	// Architecture variant cflags
	for variant, cflags := range mipsArchVariantCflags {
		pctx.StaticVariable("Mips"+variant+"VariantCflags", strings.Join(cflags, " "))
		pctx.StaticVariable("Mips"+variant+"VariantClangCflags",
			strings.Join(ClangFilterUnknownCflags(cflags), " "))
	}
}

type toolchainMips struct {
	toolchain32Bit
	cflags, clangCflags                   string
	toolchainCflags, toolchainClangCflags string
}

func (t *toolchainMips) Name() string {
	return "mips"
}

func (t *toolchainMips) GccRoot() string {
	return "${config.MipsGccRoot}"
}

func (t *toolchainMips) GccTriple() string {
	return "mips64el-linux-android"
}

func (t *toolchainMips) GccVersion() string {
	return mipsGccVersion
}

func (t *toolchainMips) ToolchainLdflags() string {
	return "${config.MipsToolchainLdflags}"
}

func (t *toolchainMips) ToolchainCflags() string {
	return t.toolchainCflags
}

func (t *toolchainMips) Cflags() string {
	return t.cflags
}

func (t *toolchainMips) Cppflags() string {
	return "${config.MipsCppflags}"
}

func (t *toolchainMips) Ldflags() string {
	return "${config.MipsLdflags}"
}

func (t *toolchainMips) IncludeFlags() string {
	return "${config.MipsIncludeFlags}"
}

func (t *toolchainMips) ClangTriple() string {
	return "mipsel-linux-android"
}

func (t *toolchainMips) ToolchainClangLdflags() string {
	return "${config.MipsToolchainLdflags}"
}

func (t *toolchainMips) ToolchainClangCflags() string {
	return t.toolchainClangCflags
}

func (t *toolchainMips) ClangAsflags() string {
	return "-fPIC -fno-integrated-as"
}

func (t *toolchainMips) ClangCflags() string {
	return t.clangCflags
}

func (t *toolchainMips) ClangCppflags() string {
	return "${config.MipsClangCppflags}"
}

func (t *toolchainMips) ClangLdflags() string {
	return "${config.MipsClangLdflags}"
}

func (toolchainMips) SanitizerRuntimeLibraryArch() string {
	return "mips"
}

func mipsToolchainFactory(arch android.Arch) Toolchain {
	return &toolchainMips{
		cflags:               "${config.MipsCflags}",
		clangCflags:          "${config.MipsClangCflags}",
		toolchainCflags:      "${config.Mips" + arch.ArchVariant + "VariantCflags}",
		toolchainClangCflags: "${config.Mips" + arch.ArchVariant + "VariantClangCflags}",
	}
}

func init() {
	registerToolchainFactory(android.Android, android.Mips, mipsToolchainFactory)
}
