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
	armToolchainCflags = []string{
		"-mthumb-interwork",
		"-msoft-float",
	}

	armCflags = []string{
		"-fno-exceptions", // from build/core/combo/select.mk
		"-Wno-multichar",  // from build/core/combo/select.mk
		"-ffunction-sections",
		"-fdata-sections",
		"-funwind-tables",
		"-fstack-protector-strong",
		"-Wa,--noexecstack",
		"-Werror=format-security",
		"-D_FORTIFY_SOURCE=2",
		"-fno-short-enums",
		"-no-canonical-prefixes",
		"-fno-canonical-system-headers",

		"-fno-builtin-sin",
		"-fno-strict-volatile-bitfields",

		// TARGET_RELEASE_CFLAGS
		"-DNDEBUG",
		"-g",
		"-Wstrict-aliasing=2",
		"-fgcse-after-reload",
		"-frerun-cse-after-loop",
		"-frename-registers",
	}

	armCppflags = []string{
		"-fvisibility-inlines-hidden",
	}

	armLdflags = []string{
		"-Wl,-z,noexecstack",
		"-Wl,-z,relro",
		"-Wl,-z,now",
		"-Wl,--build-id=md5",
		"-Wl,--warn-shared-textrel",
		"-Wl,--fatal-warnings",
		"-Wl,--icf=safe",
		"-Wl,--hash-style=gnu",
		"-Wl,--no-undefined-version",
	}

	armArmCflags = []string{
		"-O2",
		"-fomit-frame-pointer",
		"-fstrict-aliasing",
		"-funswitch-loops",
	}

	armThumbCflags = []string{
		"-mthumb",
		"-Os",
		"-fomit-frame-pointer",
		"-fno-strict-aliasing",
	}

	armArchVariantCflags = map[string][]string{
		"armv5te": []string{
			"-march=armv5te",
			"-mtune=xscale",
			"-D__ARM_ARCH_5__",
			"-D__ARM_ARCH_5T__",
			"-D__ARM_ARCH_5E__",
			"-D__ARM_ARCH_5TE__",
		},
		"armv7-a": []string{
			"-march=armv7-a",
			"-mfloat-abi=softfp",
			"-mfpu=vfpv3-d16",
		},
		"armv7-a-neon": []string{
			"-mfloat-abi=softfp",
			"-mfpu=neon",
		},
	}

	armCpuVariantCflags = map[string][]string{
		"": []string{
			"-march=armv7-a",
		},
		"cortex-a7": []string{
			"-mcpu=cortex-a7",
			"-mfpu=neon-vfpv4",
			// Fake an ARM compiler flag as these processors support LPAE which GCC/clang
			// don't advertise.
			// TODO This is a hack and we need to add it for each processor that supports LPAE until some
			// better solution comes around. See Bug 27340895
			"-D__ARM_FEATURE_LPAE=1",
		},
		"cortex-a8": []string{
			"-mcpu=cortex-a8",
		},
		"cortex-a15": []string{
			"-mcpu=cortex-a15",
			"-mfpu=neon-vfpv4",
			// Fake an ARM compiler flag as these processors support LPAE which GCC/clang
			// don't advertise.
			// TODO This is a hack and we need to add it for each processor that supports LPAE until some
			// better solution comes around. See Bug 27340895
			"-D__ARM_FEATURE_LPAE=1",
		},
		"cortex-a53": []string{
			"-mcpu=cortex-a53",
			"-mfpu=neon-fp-armv8",
			// Fake an ARM compiler flag as these processors support LPAE which GCC/clang
			// don't advertise.
			// TODO This is a hack and we need to add it for each processor that supports LPAE until some
			// better solution comes around. See Bug 27340895
			"-D__ARM_FEATURE_LPAE=1",
		},
		"krait": []string{
			"-mcpu=cortex-a15",
			"-mfpu=neon-vfpv4",
			// Fake an ARM compiler flag as these processors support LPAE which GCC/clang
			// don't advertise.
			// TODO This is a hack and we need to add it for each processor that supports LPAE until some
			// better solution comes around. See Bug 27340895
			"-D__ARM_FEATURE_LPAE=1",
		},
		"kryo": []string{
			"-mcpu=cortex-a15",
			"-mfpu=neon-fp-armv8",
			// Fake an ARM compiler flag as these processors support LPAE which GCC/clang
			// don't advertise.
			// TODO This is a hack and we need to add it for each processor that supports LPAE until some
			// better solution comes around. See Bug 27340895
			"-D__ARM_FEATURE_LPAE=1",
		},
	}

	armClangCpuVariantCflags  = copyVariantFlags(armCpuVariantCflags)
	armClangArchVariantCflags = copyVariantFlags(armArchVariantCflags)
)

const (
	armGccVersion = "4.9"
)

func init() {
	android.RegisterArchFeatures(android.Arm,
		"neon")

	android.RegisterArchVariants(android.Arm,
		"armv5te",
		"armv7-a",
		"armv7-a-neon",
		"cortex-a7",
		"cortex-a8",
		"cortex-a9",
		"cortex-a15",
		"cortex-a53",
		"cortex-a53-a57",
		"cortex-a73",
		"krait",
		"kryo",
		"denver")

	android.RegisterArchVariantFeatures(android.Arm, "armv7-a-neon", "neon")

	// Krait and Kryo targets are not supported by GCC, but are supported by Clang,
	// so override the definitions when building modules with Clang.
	replaceFirst(armClangCpuVariantCflags["krait"], "-mcpu=cortex-a15", "-mcpu=krait")
	replaceFirst(armClangCpuVariantCflags["kryo"], "-mcpu=cortex-a15", "-mcpu=krait")

	pctx.StaticVariable("armGccVersion", armGccVersion)

	pctx.SourcePathVariable("ArmGccRoot",
		"prebuilts/gcc/${HostPrebuiltTag}/arm/arm-linux-androideabi-${armGccVersion}")

	pctx.StaticVariable("ArmToolchainCflags", strings.Join(armToolchainCflags, " "))
	pctx.StaticVariable("ArmCflags", strings.Join(armCflags, " "))
	pctx.StaticVariable("ArmLdflags", strings.Join(armLdflags, " "))
	pctx.StaticVariable("ArmCppflags", strings.Join(armCppflags, " "))
	pctx.StaticVariable("ArmIncludeFlags", bionicHeaders("arm", "arm"))

	// Extended cflags

	// ARM vs. Thumb instruction set flags
	pctx.StaticVariable("ArmArmCflags", strings.Join(armArmCflags, " "))
	pctx.StaticVariable("ArmThumbCflags", strings.Join(armThumbCflags, " "))

	// Architecture variant cflags
	pctx.StaticVariable("ArmArmv5TECflags", strings.Join(armArchVariantCflags["armv5te"], " "))
	pctx.StaticVariable("ArmArmv7ACflags", strings.Join(armArchVariantCflags["armv7-a"], " "))
	pctx.StaticVariable("ArmArmv7ANeonCflags", strings.Join(armArchVariantCflags["armv7-a-neon"], " "))

	// Cpu variant cflags
	pctx.StaticVariable("ArmGenericCflags", strings.Join(armCpuVariantCflags[""], " "))
	pctx.StaticVariable("ArmCortexA7Cflags", strings.Join(armCpuVariantCflags["cortex-a7"], " "))
	pctx.StaticVariable("ArmCortexA8Cflags", strings.Join(armCpuVariantCflags["cortex-a8"], " "))
	pctx.StaticVariable("ArmCortexA15Cflags", strings.Join(armCpuVariantCflags["cortex-a15"], " "))
	pctx.StaticVariable("ArmCortexA53Cflags", strings.Join(armCpuVariantCflags["cortex-a53"], " "))
	pctx.StaticVariable("ArmKraitCflags", strings.Join(armCpuVariantCflags["krait"], " "))
	pctx.StaticVariable("ArmKryoCflags", strings.Join(armCpuVariantCflags["kryo"], " "))

	// Clang cflags
	pctx.StaticVariable("ArmToolchainClangCflags", strings.Join(ClangFilterUnknownCflags(armToolchainCflags), " "))
	pctx.StaticVariable("ArmClangCflags", strings.Join(ClangFilterUnknownCflags(armCflags), " "))
	pctx.StaticVariable("ArmClangLdflags", strings.Join(ClangFilterUnknownCflags(armLdflags), " "))
	pctx.StaticVariable("ArmClangCppflags", strings.Join(ClangFilterUnknownCflags(armCppflags), " "))

	// Clang ARM vs. Thumb instruction set cflags
	pctx.StaticVariable("ArmClangArmCflags", strings.Join(ClangFilterUnknownCflags(armArmCflags), " "))
	pctx.StaticVariable("ArmClangThumbCflags", strings.Join(ClangFilterUnknownCflags(armThumbCflags), " "))

	// Clang arch variant cflags
	pctx.StaticVariable("ArmClangArmv5TECflags",
		strings.Join(armClangArchVariantCflags["armv5te"], " "))
	pctx.StaticVariable("ArmClangArmv7ACflags",
		strings.Join(armClangArchVariantCflags["armv7-a"], " "))
	pctx.StaticVariable("ArmClangArmv7ANeonCflags",
		strings.Join(armClangArchVariantCflags["armv7-a-neon"], " "))

	// Clang cpu variant cflags
	pctx.StaticVariable("ArmClangGenericCflags",
		strings.Join(armClangCpuVariantCflags[""], " "))
	pctx.StaticVariable("ArmClangCortexA7Cflags",
		strings.Join(armClangCpuVariantCflags["cortex-a7"], " "))
	pctx.StaticVariable("ArmClangCortexA8Cflags",
		strings.Join(armClangCpuVariantCflags["cortex-a8"], " "))
	pctx.StaticVariable("ArmClangCortexA15Cflags",
		strings.Join(armClangCpuVariantCflags["cortex-a15"], " "))
	pctx.StaticVariable("ArmClangCortexA53Cflags",
		strings.Join(armClangCpuVariantCflags["cortex-a53"], " "))
	pctx.StaticVariable("ArmClangKraitCflags",
		strings.Join(armClangCpuVariantCflags["krait"], " "))
	pctx.StaticVariable("ArmClangKryoCflags",
		strings.Join(armClangCpuVariantCflags["kryo"], " "))
}

var (
	armArchVariantCflagsVar = map[string]string{
		"armv5te":      "${config.ArmArmv5TECflags}",
		"armv7-a":      "${config.ArmArmv7ACflags}",
		"armv7-a-neon": "${config.ArmArmv7ANeonCflags}",
	}

	armCpuVariantCflagsVar = map[string]string{
		"":               "${config.ArmGenericCflags}",
		"cortex-a7":      "${config.ArmCortexA7Cflags}",
		"cortex-a8":      "${config.ArmCortexA8Cflags}",
		"cortex-a15":     "${config.ArmCortexA15Cflags}",
		"cortex-a53":     "${config.ArmCortexA53Cflags}",
		"cortex-a53.a57": "${config.ArmCortexA53Cflags}",
		"cortex-a73":     "${config.ArmCortexA53Cflags}",
		"krait":          "${config.ArmKraitCflags}",
		"kryo":           "${config.ArmKryoCflags}",
		"denver":         "${config.ArmCortexA15Cflags}",
	}

	armClangArchVariantCflagsVar = map[string]string{
		"armv5te":      "${config.ArmClangArmv5TECflags}",
		"armv7-a":      "${config.ArmClangArmv7ACflags}",
		"armv7-a-neon": "${config.ArmClangArmv7ANeonCflags}",
	}

	armClangCpuVariantCflagsVar = map[string]string{
		"":               "${config.ArmClangGenericCflags}",
		"cortex-a7":      "${config.ArmClangCortexA7Cflags}",
		"cortex-a8":      "${config.ArmClangCortexA8Cflags}",
		"cortex-a15":     "${config.ArmClangCortexA15Cflags}",
		"cortex-a53":     "${config.ArmClangCortexA53Cflags}",
		"cortex-a53.a57": "${config.ArmClangCortexA53Cflags}",
		"cortex-a73":     "${config.ArmClangCortexA53Cflags}",
		"krait":          "${config.ArmClangKraitCflags}",
		"kryo":           "${config.ArmClangKryoCflags}",
		"denver":         "${config.ArmClangCortexA15Cflags}",
	}
)

type toolchainArm struct {
	toolchain32Bit
	ldflags                               string
	toolchainCflags, toolchainClangCflags string
}

func (t *toolchainArm) Name() string {
	return "arm"
}

func (t *toolchainArm) GccRoot() string {
	return "${config.ArmGccRoot}"
}

func (t *toolchainArm) GccTriple() string {
	return "arm-linux-androideabi"
}

func (t *toolchainArm) GccVersion() string {
	return armGccVersion
}

func (t *toolchainArm) ToolchainCflags() string {
	return t.toolchainCflags
}

func (t *toolchainArm) Cflags() string {
	return "${config.ArmCflags}"
}

func (t *toolchainArm) Cppflags() string {
	return "${config.ArmCppflags}"
}

func (t *toolchainArm) Ldflags() string {
	return t.ldflags
}

func (t *toolchainArm) IncludeFlags() string {
	return "${config.ArmIncludeFlags}"
}

func (t *toolchainArm) InstructionSetFlags(isa string) (string, error) {
	switch isa {
	case "arm":
		return "${config.ArmArmCflags}", nil
	case "thumb", "":
		return "${config.ArmThumbCflags}", nil
	default:
		return t.toolchainBase.InstructionSetFlags(isa)
	}
}

func (t *toolchainArm) ClangTriple() string {
	return t.GccTriple()
}

func (t *toolchainArm) ToolchainClangCflags() string {
	return t.toolchainClangCflags
}

func (t *toolchainArm) ClangCflags() string {
	return "${config.ArmClangCflags}"
}

func (t *toolchainArm) ClangCppflags() string {
	return "${config.ArmClangCppflags}"
}

func (t *toolchainArm) ClangLdflags() string {
	return t.ldflags
}

func (t *toolchainArm) ClangInstructionSetFlags(isa string) (string, error) {
	switch isa {
	case "arm":
		return "${config.ArmClangArmCflags}", nil
	case "thumb", "":
		return "${config.ArmClangThumbCflags}", nil
	default:
		return t.toolchainBase.ClangInstructionSetFlags(isa)
	}
}

func (toolchainArm) SanitizerRuntimeLibraryArch() string {
	return "arm"
}

func armToolchainFactory(arch android.Arch) Toolchain {
	var fixCortexA8 string
	toolchainCflags := make([]string, 2, 3)
	toolchainClangCflags := make([]string, 2, 3)

	toolchainCflags[0] = "${config.ArmToolchainCflags}"
	toolchainCflags[1] = armArchVariantCflagsVar[arch.ArchVariant]
	toolchainClangCflags[0] = "${config.ArmToolchainClangCflags}"
	toolchainClangCflags[1] = armClangArchVariantCflagsVar[arch.ArchVariant]

	switch arch.ArchVariant {
	case "armv7-a-neon":
		switch arch.CpuVariant {
		case "cortex-a8", "":
			// Generic ARM might be a Cortex A8 -- better safe than sorry
			fixCortexA8 = "-Wl,--fix-cortex-a8"
		default:
			fixCortexA8 = "-Wl,--no-fix-cortex-a8"
		}

		toolchainCflags = append(toolchainCflags,
			variantOrDefault(armCpuVariantCflagsVar, arch.CpuVariant))
		toolchainClangCflags = append(toolchainClangCflags,
			variantOrDefault(armClangCpuVariantCflagsVar, arch.CpuVariant))
	case "armv7-a":
		fixCortexA8 = "-Wl,--fix-cortex-a8"
	case "armv5te":
		// Nothing extra for armv5te
	default:
		panic(fmt.Sprintf("Unknown ARM architecture version: %q", arch.ArchVariant))
	}

	return &toolchainArm{
		toolchainCflags: strings.Join(toolchainCflags, " "),
		ldflags: strings.Join([]string{
			"${config.ArmLdflags}",
			fixCortexA8,
		}, " "),
		toolchainClangCflags: strings.Join(toolchainClangCflags, " "),
	}
}

func init() {
	registerToolchainFactory(android.Android, android.Arm, armToolchainFactory)
}
