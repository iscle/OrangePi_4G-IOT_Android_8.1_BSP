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
	"fmt"
	"strings"

	"android/soong/android"
)

var (
	arm64Cflags = []string{
		"-fno-exceptions", // from build/core/combo/select.mk
		"-Wno-multichar",  // from build/core/combo/select.mk
		"-fno-strict-aliasing",
		"-fstack-protector-strong",
		"-ffunction-sections",
		"-fdata-sections",
		"-funwind-tables",
		"-Wa,--noexecstack",
		"-Werror=format-security",
		"-D_FORTIFY_SOURCE=2",
		"-fno-short-enums",
		"-no-canonical-prefixes",
		"-fno-canonical-system-headers",

		// Help catch common 32/64-bit errors.
		"-Werror=pointer-to-int-cast",
		"-Werror=int-to-pointer-cast",
		"-Werror=implicit-function-declaration",

		"-fno-strict-volatile-bitfields",

		// TARGET_RELEASE_CFLAGS
		"-DNDEBUG",
		"-O2 -g",
		"-Wstrict-aliasing=2",
		"-fgcse-after-reload",
		"-frerun-cse-after-loop",
		"-frename-registers",
	}

	arm64Ldflags = []string{
		"-Wl,-z,noexecstack",
		"-Wl,-z,relro",
		"-Wl,-z,now",
		"-Wl,--build-id=md5",
		"-Wl,--warn-shared-textrel",
		"-Wl,--fatal-warnings",
		"-Wl,-maarch64linux",
		"-Wl,--hash-style=gnu",
		"-Wl,--fix-cortex-a53-843419",
		"-fuse-ld=gold",
		"-Wl,--icf=safe",
		"-Wl,--no-undefined-version",
	}

	arm64Cppflags = []string{
		"-fvisibility-inlines-hidden",
	}

	arm64CpuVariantCflags = map[string][]string{
		"cortex-a53": []string{
			"-mcpu=cortex-a53",
		},
		"kryo": []string{
			// Use the cortex-a57 cpu since some compilers
			// don't support a Kryo specific target yet.
			"-mcpu=cortex-a57",
		},
	}

	arm64ClangCpuVariantCflags = copyVariantFlags(arm64CpuVariantCflags)
)

const (
	arm64GccVersion = "4.9"
)

func init() {
	android.RegisterArchVariants(android.Arm64,
		"armv8_a",
		"cortex-a53",
		"cortex-a73",
		"kryo",
		"denver64")

	// Clang supports specific Kryo targeting
	replaceFirst(arm64ClangCpuVariantCflags["kryo"], "-mcpu=cortex-a57", "-mcpu=kryo")

	pctx.StaticVariable("arm64GccVersion", arm64GccVersion)

	pctx.SourcePathVariable("Arm64GccRoot",
		"prebuilts/gcc/${HostPrebuiltTag}/aarch64/aarch64-linux-android-${arm64GccVersion}")

	pctx.StaticVariable("Arm64Cflags", strings.Join(arm64Cflags, " "))
	pctx.StaticVariable("Arm64Ldflags", strings.Join(arm64Ldflags, " "))
	pctx.StaticVariable("Arm64Cppflags", strings.Join(arm64Cppflags, " "))
	pctx.StaticVariable("Arm64IncludeFlags", bionicHeaders("arm64", "arm64"))

	pctx.StaticVariable("Arm64ClangCflags", strings.Join(ClangFilterUnknownCflags(arm64Cflags), " "))
	pctx.StaticVariable("Arm64ClangLdflags", strings.Join(ClangFilterUnknownCflags(arm64Ldflags), " "))
	pctx.StaticVariable("Arm64ClangCppflags", strings.Join(ClangFilterUnknownCflags(arm64Cppflags), " "))

	pctx.StaticVariable("Arm64CortexA53Cflags",
		strings.Join(arm64CpuVariantCflags["cortex-a53"], " "))
	pctx.StaticVariable("Arm64ClangCortexA53Cflags",
		strings.Join(arm64ClangCpuVariantCflags["cortex-a53"], " "))

	pctx.StaticVariable("Arm64KryoCflags",
		strings.Join(arm64CpuVariantCflags["kryo"], " "))
	pctx.StaticVariable("Arm64ClangKryoCflags",
		strings.Join(arm64ClangCpuVariantCflags["kryo"], " "))
}

var (
	arm64CpuVariantCflagsVar = map[string]string{
		"":           "",
		"cortex-a53": "${config.Arm64CortexA53Cflags}",
		"cortex-a73": "${config.Arm64CortexA53Cflags}",
		"kryo":       "${config.Arm64KryoCflags}",
	}

	arm64ClangCpuVariantCflagsVar = map[string]string{
		"":           "",
		"cortex-a53": "${config.Arm64ClangCortexA53Cflags}",
		"cortex-a73": "${config.Arm64ClangCortexA53Cflags}",
		"kryo":       "${config.Arm64ClangKryoCflags}",
	}
)

type toolchainArm64 struct {
	toolchain64Bit

	toolchainCflags      string
	toolchainClangCflags string
}

func (t *toolchainArm64) Name() string {
	return "arm64"
}

func (t *toolchainArm64) GccRoot() string {
	return "${config.Arm64GccRoot}"
}

func (t *toolchainArm64) GccTriple() string {
	return "aarch64-linux-android"
}

func (t *toolchainArm64) GccVersion() string {
	return arm64GccVersion
}

func (t *toolchainArm64) ToolchainCflags() string {
	return t.toolchainCflags
}

func (t *toolchainArm64) Cflags() string {
	return "${config.Arm64Cflags}"
}

func (t *toolchainArm64) Cppflags() string {
	return "${config.Arm64Cppflags}"
}

func (t *toolchainArm64) Ldflags() string {
	return "${config.Arm64Ldflags}"
}

func (t *toolchainArm64) IncludeFlags() string {
	return "${config.Arm64IncludeFlags}"
}

func (t *toolchainArm64) ClangTriple() string {
	return t.GccTriple()
}

func (t *toolchainArm64) ClangCflags() string {
	return "${config.Arm64ClangCflags}"
}

func (t *toolchainArm64) ClangCppflags() string {
	return "${config.Arm64ClangCppflags}"
}

func (t *toolchainArm64) ClangLdflags() string {
	return "${config.Arm64Ldflags}"
}

func (t *toolchainArm64) ToolchainClangCflags() string {
	return t.toolchainClangCflags
}

func (toolchainArm64) SanitizerRuntimeLibraryArch() string {
	return "aarch64"
}

func arm64ToolchainFactory(arch android.Arch) Toolchain {
	if arch.ArchVariant != "armv8-a" {
		panic(fmt.Sprintf("Unknown ARM architecture version: %q", arch.ArchVariant))
	}

	return &toolchainArm64{
		toolchainCflags:      variantOrDefault(arm64CpuVariantCflagsVar, arch.CpuVariant),
		toolchainClangCflags: variantOrDefault(arm64ClangCpuVariantCflagsVar, arch.CpuVariant),
	}
}

func init() {
	registerToolchainFactory(android.Android, android.Arm64, arm64ToolchainFactory)
}
