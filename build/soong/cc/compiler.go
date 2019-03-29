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
	"fmt"
	"path/filepath"
	"strings"

	"github.com/google/blueprint/proptools"

	"android/soong/android"
	"android/soong/cc/config"
)

// This file contains the basic C/C++/assembly to .o compliation steps

type BaseCompilerProperties struct {
	// list of source files used to compile the C/C++ module.  May be .c, .cpp, or .S files.
	// srcs may reference the outputs of other modules that produce source files like genrule
	// or filegroup using the syntax ":module".
	Srcs []string `android:"arch_variant"`

	// list of source files that should not be used to build the C/C++ module.
	// This is most useful in the arch/multilib variants to remove non-common files
	Exclude_srcs []string `android:"arch_variant"`

	// list of module-specific flags that will be used for C and C++ compiles.
	Cflags []string `android:"arch_variant"`

	// list of module-specific flags that will be used for C++ compiles
	Cppflags []string `android:"arch_variant"`

	// list of module-specific flags that will be used for C compiles
	Conlyflags []string `android:"arch_variant"`

	// list of module-specific flags that will be used for .S compiles
	Asflags []string `android:"arch_variant"`

	// list of module-specific flags that will be used for C and C++ compiles when
	// compiling with clang
	Clang_cflags []string `android:"arch_variant"`

	// list of module-specific flags that will be used for .S compiles when
	// compiling with clang
	Clang_asflags []string `android:"arch_variant"`

	// list of module-specific flags that will be used for .y and .yy compiles
	Yaccflags []string

	// the instruction set architecture to use to compile the C/C++
	// module.
	Instruction_set string `android:"arch_variant"`

	// list of directories relative to the root of the source tree that will
	// be added to the include path using -I.
	// If possible, don't use this.  If adding paths from the current directory use
	// local_include_dirs, if adding paths from other modules use export_include_dirs in
	// that module.
	Include_dirs []string `android:"arch_variant,variant_prepend"`

	// list of directories relative to the Blueprints file that will
	// be added to the include path using -I
	Local_include_dirs []string `android:"arch_variant,variant_prepend",`

	// list of generated sources to compile. These are the names of gensrcs or
	// genrule modules.
	Generated_sources []string `android:"arch_variant"`

	// list of generated headers to add to the include path. These are the names
	// of genrule modules.
	Generated_headers []string `android:"arch_variant"`

	// pass -frtti instead of -fno-rtti
	Rtti *bool

	// C standard version to use. Can be a specific version (such as "gnu11"),
	// "experimental" (which will use draft versions like C1x when available),
	// or the empty string (which will use the default).
	C_std string

	// C++ standard version to use. Can be a specific version (such as
	// "gnu++11"), "experimental" (which will use draft versions like C++1z when
	// available), or the empty string (which will use the default).
	Cpp_std string

	// if set to false, use -std=c++* instead of -std=gnu++*
	Gnu_extensions *bool

	Aidl struct {
		// list of directories that will be added to the aidl include paths.
		Include_dirs []string

		// list of directories relative to the Blueprints file that will
		// be added to the aidl include paths.
		Local_include_dirs []string
	}

	Renderscript struct {
		// list of directories that will be added to the llvm-rs-cc include paths
		Include_dirs []string

		// list of flags that will be passed to llvm-rs-cc
		Flags []string

		// Renderscript API level to target
		Target_api *string
	}

	Debug, Release struct {
		// list of module-specific flags that will be used for C and C++ compiles in debug or
		// release builds
		Cflags []string `android:"arch_variant"`
	} `android:"arch_variant"`

	Target struct {
		Vendor struct {
			// list of source files that should only be used in the
			// vendor variant of the C/C++ module.
			Srcs []string

			// list of source files that should not be used to
			// build the vendor variant of the C/C++ module.
			Exclude_srcs []string

			// List of additional cflags that should be used to build the vendor
			// variant of the C/C++ module.
			Cflags []string
		}
	}

	// Stores the original list of source files before being cleared by library reuse
	OriginalSrcs []string `blueprint:"mutated"`
}

func NewBaseCompiler() *baseCompiler {
	return &baseCompiler{}
}

type baseCompiler struct {
	Properties BaseCompilerProperties
	Proto      ProtoProperties
	deps       android.Paths
	srcs       android.Paths
	flags      builderFlags
}

var _ compiler = (*baseCompiler)(nil)

type CompiledInterface interface {
	Srcs() android.Paths
}

func (compiler *baseCompiler) Srcs() android.Paths {
	return compiler.srcs
}

func (compiler *baseCompiler) appendCflags(flags []string) {
	compiler.Properties.Cflags = append(compiler.Properties.Cflags, flags...)
}

func (compiler *baseCompiler) appendAsflags(flags []string) {
	compiler.Properties.Asflags = append(compiler.Properties.Asflags, flags...)
}

func (compiler *baseCompiler) compilerProps() []interface{} {
	return []interface{}{&compiler.Properties, &compiler.Proto}
}

func (compiler *baseCompiler) compilerInit(ctx BaseModuleContext) {}

func (compiler *baseCompiler) compilerDeps(ctx DepsContext, deps Deps) Deps {
	deps.GeneratedSources = append(deps.GeneratedSources, compiler.Properties.Generated_sources...)
	deps.GeneratedHeaders = append(deps.GeneratedHeaders, compiler.Properties.Generated_headers...)

	android.ExtractSourcesDeps(ctx, compiler.Properties.Srcs)

	if compiler.hasSrcExt(".proto") {
		deps = protoDeps(ctx, deps, &compiler.Proto)
	}

	return deps
}

// Create a Flags struct that collects the compile flags from global values,
// per-target values, module type values, and per-module Blueprints properties
func (compiler *baseCompiler) compilerFlags(ctx ModuleContext, flags Flags) Flags {
	tc := ctx.toolchain()

	CheckBadCompilerFlags(ctx, "cflags", compiler.Properties.Cflags)
	CheckBadCompilerFlags(ctx, "cppflags", compiler.Properties.Cppflags)
	CheckBadCompilerFlags(ctx, "conlyflags", compiler.Properties.Conlyflags)
	CheckBadCompilerFlags(ctx, "asflags", compiler.Properties.Asflags)

	esc := proptools.NinjaAndShellEscape

	flags.CFlags = append(flags.CFlags, esc(compiler.Properties.Cflags)...)
	flags.CppFlags = append(flags.CppFlags, esc(compiler.Properties.Cppflags)...)
	flags.ConlyFlags = append(flags.ConlyFlags, esc(compiler.Properties.Conlyflags)...)
	flags.AsFlags = append(flags.AsFlags, esc(compiler.Properties.Asflags)...)
	flags.YasmFlags = append(flags.YasmFlags, esc(compiler.Properties.Asflags)...)
	flags.YaccFlags = append(flags.YaccFlags, esc(compiler.Properties.Yaccflags)...)

	// Include dir cflags
	localIncludeDirs := android.PathsForModuleSrc(ctx, compiler.Properties.Local_include_dirs)
	if len(localIncludeDirs) > 0 {
		f := includeDirsToFlags(localIncludeDirs)
		flags.GlobalFlags = append(flags.GlobalFlags, f)
		flags.YasmFlags = append(flags.YasmFlags, f)
	}
	rootIncludeDirs := android.PathsForSource(ctx, compiler.Properties.Include_dirs)
	if len(rootIncludeDirs) > 0 {
		f := includeDirsToFlags(rootIncludeDirs)
		flags.GlobalFlags = append(flags.GlobalFlags, f)
		flags.YasmFlags = append(flags.YasmFlags, f)
	}

	if !ctx.noDefaultCompilerFlags() {
		flags.GlobalFlags = append(flags.GlobalFlags, "-I"+android.PathForModuleSrc(ctx).String())
		flags.YasmFlags = append(flags.YasmFlags, "-I"+android.PathForModuleSrc(ctx).String())

		if !(ctx.sdk() || ctx.vndk()) || ctx.Host() {
			flags.SystemIncludeFlags = append(flags.SystemIncludeFlags,
				"${config.CommonGlobalIncludes}",
				tc.IncludeFlags(),
				"${config.CommonNativehelperInclude}")
		}
	}

	if ctx.sdk() {
		// The NDK headers are installed to a common sysroot. While a more
		// typical Soong approach would be to only make the headers for the
		// library you're using available, we're trying to emulate the NDK
		// behavior here, and the NDK always has all the NDK headers available.
		flags.SystemIncludeFlags = append(flags.SystemIncludeFlags,
			"-isystem "+getCurrentIncludePath(ctx).String(),
			"-isystem "+getCurrentIncludePath(ctx).Join(ctx, tc.ClangTriple()).String())

		// Traditionally this has come from android/api-level.h, but with the
		// libc headers unified it must be set by the build system since we
		// don't have per-API level copies of that header now.
		version := ctx.sdkVersion()
		if version == "current" {
			version = "__ANDROID_API_FUTURE__"
		}
		flags.GlobalFlags = append(flags.GlobalFlags,
			"-D__ANDROID_API__="+version)

		// Until the full NDK has been migrated to using ndk_headers, we still
		// need to add the legacy sysroot includes to get the full set of
		// headers.
		legacyIncludes := fmt.Sprintf(
			"prebuilts/ndk/current/platforms/android-%s/arch-%s/usr/include",
			ctx.sdkVersion(), ctx.Arch().ArchType.String())
		flags.SystemIncludeFlags = append(flags.SystemIncludeFlags, "-isystem "+legacyIncludes)
	}

	if ctx.vndk() {
		flags.GlobalFlags = append(flags.GlobalFlags,
			"-D__ANDROID_API__=__ANDROID_API_FUTURE__", "-D__ANDROID_VNDK__")
	}

	instructionSet := compiler.Properties.Instruction_set
	if flags.RequiredInstructionSet != "" {
		instructionSet = flags.RequiredInstructionSet
	}
	instructionSetFlags, err := tc.InstructionSetFlags(instructionSet)
	if flags.Clang {
		instructionSetFlags, err = tc.ClangInstructionSetFlags(instructionSet)
	}
	if err != nil {
		ctx.ModuleErrorf("%s", err)
	}

	CheckBadCompilerFlags(ctx, "release.cflags", compiler.Properties.Release.Cflags)

	// TODO: debug
	flags.CFlags = append(flags.CFlags, esc(compiler.Properties.Release.Cflags)...)

	if flags.Clang {
		CheckBadCompilerFlags(ctx, "clang_cflags", compiler.Properties.Clang_cflags)
		CheckBadCompilerFlags(ctx, "clang_asflags", compiler.Properties.Clang_asflags)

		flags.CFlags = config.ClangFilterUnknownCflags(flags.CFlags)
		flags.CFlags = append(flags.CFlags, esc(compiler.Properties.Clang_cflags)...)
		flags.AsFlags = append(flags.AsFlags, esc(compiler.Properties.Clang_asflags)...)
		flags.CppFlags = config.ClangFilterUnknownCflags(flags.CppFlags)
		flags.ConlyFlags = config.ClangFilterUnknownCflags(flags.ConlyFlags)
		flags.LdFlags = config.ClangFilterUnknownCflags(flags.LdFlags)

		target := "-target " + tc.ClangTriple()
		gccPrefix := "-B" + config.ToolPath(tc)

		flags.CFlags = append(flags.CFlags, target, gccPrefix)
		flags.AsFlags = append(flags.AsFlags, target, gccPrefix)
		flags.LdFlags = append(flags.LdFlags, target, gccPrefix)
	}

	hod := "Host"
	if ctx.Os().Class == android.Device {
		hod = "Device"
	}

	if !ctx.noDefaultCompilerFlags() {
		flags.GlobalFlags = append(flags.GlobalFlags, instructionSetFlags)
		flags.ConlyFlags = append([]string{"${config.CommonGlobalConlyflags}"}, flags.ConlyFlags...)

		if flags.Clang {
			flags.AsFlags = append(flags.AsFlags, tc.ClangAsflags())
			flags.CppFlags = append([]string{"${config.CommonClangGlobalCppflags}"}, flags.CppFlags...)
			flags.GlobalFlags = append(flags.GlobalFlags,
				tc.ClangCflags(),
				"${config.CommonClangGlobalCflags}",
				fmt.Sprintf("${config.%sClangGlobalCflags}", hod))
		} else {
			flags.CppFlags = append([]string{"${config.CommonGlobalCppflags}"}, flags.CppFlags...)
			flags.GlobalFlags = append(flags.GlobalFlags,
				tc.Cflags(),
				"${config.CommonGlobalCflags}",
				fmt.Sprintf("${config.%sGlobalCflags}", hod))
		}

		if Bool(ctx.AConfig().ProductVariables.Brillo) {
			flags.GlobalFlags = append(flags.GlobalFlags, "-D__BRILLO__")
		}

		if ctx.Device() {
			if Bool(compiler.Properties.Rtti) {
				flags.CppFlags = append(flags.CppFlags, "-frtti")
			} else {
				flags.CppFlags = append(flags.CppFlags, "-fno-rtti")
			}
		}

		flags.AsFlags = append(flags.AsFlags, "-D__ASSEMBLY__")

		if flags.Clang {
			flags.CppFlags = append(flags.CppFlags, tc.ClangCppflags())
		} else {
			flags.CppFlags = append(flags.CppFlags, tc.Cppflags())
		}

		flags.YasmFlags = append(flags.YasmFlags, tc.YasmFlags())
	}

	if flags.Clang {
		flags.GlobalFlags = append(flags.GlobalFlags, tc.ToolchainClangCflags())
	} else {
		flags.GlobalFlags = append(flags.GlobalFlags, tc.ToolchainCflags())
	}

	if !ctx.sdk() {
		cStd := config.CStdVersion
		if compiler.Properties.C_std == "experimental" {
			cStd = config.ExperimentalCStdVersion
		} else if compiler.Properties.C_std != "" {
			cStd = compiler.Properties.C_std
		}

		cppStd := config.CppStdVersion
		if compiler.Properties.Cpp_std == "experimental" {
			cppStd = config.ExperimentalCppStdVersion
		} else if compiler.Properties.Cpp_std != "" {
			cppStd = compiler.Properties.Cpp_std
		}

		if !flags.Clang {
			// GCC uses an invalid C++14 ABI (emits calls to
			// __cxa_throw_bad_array_length, which is not a valid C++ RT ABI).
			// http://b/25022512
			cppStd = config.GccCppStdVersion
		} else if ctx.Host() && !flags.Clang {
			// The host GCC doesn't support C++14 (and is deprecated, so likely
			// never will). Build these modules with C++11.
			cppStd = config.GccCppStdVersion
		}

		if compiler.Properties.Gnu_extensions != nil && *compiler.Properties.Gnu_extensions == false {
			cStd = gnuToCReplacer.Replace(cStd)
			cppStd = gnuToCReplacer.Replace(cppStd)
		}

		flags.ConlyFlags = append([]string{"-std=" + cStd}, flags.ConlyFlags...)
		flags.CppFlags = append([]string{"-std=" + cppStd}, flags.CppFlags...)
	}

	if ctx.vndk() {
		flags.CFlags = append(flags.CFlags, esc(compiler.Properties.Target.Vendor.Cflags)...)
	}

	// We can enforce some rules more strictly in the code we own. strict
	// indicates if this is code that we can be stricter with. If we have
	// rules that we want to apply to *our* code (but maybe can't for
	// vendor/device specific things), we could extend this to be a ternary
	// value.
	strict := true
	if strings.HasPrefix(android.PathForModuleSrc(ctx).String(), "external/") {
		strict = false
	}

	// Can be used to make some annotations stricter for code we can fix
	// (such as when we mark functions as deprecated).
	if strict {
		flags.CFlags = append(flags.CFlags, "-DANDROID_STRICT")
	}

	if compiler.hasSrcExt(".proto") {
		flags = protoFlags(ctx, flags, &compiler.Proto)
	}

	if compiler.hasSrcExt(".y") || compiler.hasSrcExt(".yy") {
		flags.GlobalFlags = append(flags.GlobalFlags,
			"-I"+android.PathForModuleGen(ctx, "yacc", ctx.ModuleDir()).String())
	}

	if compiler.hasSrcExt(".aidl") {
		if len(compiler.Properties.Aidl.Local_include_dirs) > 0 {
			localAidlIncludeDirs := android.PathsForModuleSrc(ctx, compiler.Properties.Aidl.Local_include_dirs)
			flags.aidlFlags = append(flags.aidlFlags, includeDirsToFlags(localAidlIncludeDirs))
		}
		if len(compiler.Properties.Aidl.Include_dirs) > 0 {
			rootAidlIncludeDirs := android.PathsForSource(ctx, compiler.Properties.Aidl.Include_dirs)
			flags.aidlFlags = append(flags.aidlFlags, includeDirsToFlags(rootAidlIncludeDirs))
		}

		flags.GlobalFlags = append(flags.GlobalFlags,
			"-I"+android.PathForModuleGen(ctx, "aidl").String())
	}

	if compiler.hasSrcExt(".rs") || compiler.hasSrcExt(".fs") {
		flags = rsFlags(ctx, flags, &compiler.Properties)
	}

	return flags
}

func (compiler *baseCompiler) hasSrcExt(ext string) bool {
	for _, src := range compiler.Properties.Srcs {
		if filepath.Ext(src) == ext {
			return true
		}
	}
	for _, src := range compiler.Properties.OriginalSrcs {
		if filepath.Ext(src) == ext {
			return true
		}
	}

	return false
}

var gnuToCReplacer = strings.NewReplacer("gnu", "c")

func ndkPathDeps(ctx ModuleContext) android.Paths {
	if ctx.sdk() {
		// The NDK sysroot timestamp file depends on all the NDK sysroot files
		// (headers and libraries).
		return android.Paths{getNdkSysrootTimestampFile(ctx)}
	}
	return nil
}

func (compiler *baseCompiler) compile(ctx ModuleContext, flags Flags, deps PathDeps) Objects {
	pathDeps := deps.GeneratedHeaders
	pathDeps = append(pathDeps, ndkPathDeps(ctx)...)

	if ctx.vndk() {
		compiler.Properties.Srcs = append(compiler.Properties.Srcs,
			compiler.Properties.Target.Vendor.Srcs...)

		compiler.Properties.Exclude_srcs = append(compiler.Properties.Exclude_srcs,
			compiler.Properties.Target.Vendor.Exclude_srcs...)
	}

	srcs := ctx.ExpandSources(compiler.Properties.Srcs, compiler.Properties.Exclude_srcs)
	srcs = append(srcs, deps.GeneratedSources...)

	buildFlags := flagsToBuilderFlags(flags)

	srcs, genDeps := genSources(ctx, srcs, buildFlags)

	pathDeps = append(pathDeps, genDeps...)
	pathDeps = append(pathDeps, flags.CFlagsDeps...)

	compiler.deps = pathDeps

	// Save src, buildFlags and context
	compiler.srcs = srcs

	// Compile files listed in c.Properties.Srcs into objects
	objs := compileObjs(ctx, buildFlags, "", srcs, compiler.deps)

	if ctx.Failed() {
		return Objects{}
	}

	return objs
}

// Compile a list of source files into objects a specified subdirectory
func compileObjs(ctx android.ModuleContext, flags builderFlags,
	subdir string, srcFiles, deps android.Paths) Objects {

	return TransformSourceToObj(ctx, subdir, srcFiles, flags, deps)
}
