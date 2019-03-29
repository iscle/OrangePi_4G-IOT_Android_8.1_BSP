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
	"io"
	"strings"

	"github.com/google/blueprint"

	"android/soong/android"
	"android/soong/cc/config"
)

var (
	// Any C flags added by sanitizer which libTooling tools may not
	// understand also need to be added to ClangLibToolingUnknownCflags in
	// cc/config/clang.go

	asanCflags  = []string{"-fno-omit-frame-pointer"}
	asanLdflags = []string{"-Wl,-u,__asan_preinit"}
	asanLibs    = []string{"libasan"}

	cfiCflags = []string{"-flto", "-fsanitize-cfi-cross-dso", "-fvisibility=default",
		"-fsanitize-blacklist=external/compiler-rt/lib/cfi/cfi_blacklist.txt"}
	// FIXME: revert the __cfi_check flag when clang is updated to r280031.
	cfiLdflags = []string{"-flto", "-fsanitize-cfi-cross-dso", "-fsanitize=cfi",
		"-Wl,-plugin-opt,O1 -Wl,-export-dynamic-symbol=__cfi_check"}
	cfiArflags = []string{"--plugin ${config.ClangBin}/../lib64/LLVMgold.so"}

	intOverflowCflags = []string{"-fsanitize-blacklist=build/soong/cc/config/integer_overflow_blacklist.txt"}
)

type sanitizerType int

func boolPtr(v bool) *bool {
	if v {
		return &v
	} else {
		return nil
	}
}

const (
	asan sanitizerType = iota + 1
	tsan
	intOverflow
)

func (t sanitizerType) String() string {
	switch t {
	case asan:
		return "asan"
	case tsan:
		return "tsan"
	case intOverflow:
		return "intOverflow"
	default:
		panic(fmt.Errorf("unknown sanitizerType %d", t))
	}
}

type SanitizeProperties struct {
	// enable AddressSanitizer, ThreadSanitizer, or UndefinedBehaviorSanitizer
	Sanitize struct {
		Never bool `android:"arch_variant"`

		// main sanitizers
		Address *bool `android:"arch_variant"`
		Thread  *bool `android:"arch_variant"`

		// local sanitizers
		Undefined        *bool    `android:"arch_variant"`
		All_undefined    *bool    `android:"arch_variant"`
		Misc_undefined   []string `android:"arch_variant"`
		Coverage         *bool    `android:"arch_variant"`
		Safestack        *bool    `android:"arch_variant"`
		Cfi              *bool    `android:"arch_variant"`
		Integer_overflow *bool    `android:"arch_variant"`

		// Sanitizers to run in the diagnostic mode (as opposed to the release mode).
		// Replaces abort() on error with a human-readable error message.
		// Address and Thread sanitizers always run in diagnostic mode.
		Diag struct {
			Undefined        *bool    `android:"arch_variant"`
			Cfi              *bool    `android:"arch_variant"`
			Integer_overflow *bool    `android:"arch_variant"`
			Misc_undefined   []string `android:"arch_variant"`
		}

		// value to pass to -fsanitize-recover=
		Recover []string

		// value to pass to -fsanitize-blacklist
		Blacklist *string
	} `android:"arch_variant"`

	SanitizerEnabled bool `blueprint:"mutated"`
	SanitizeDep      bool `blueprint:"mutated"`
	InSanitizerDir   bool `blueprint:"mutated"`
}

type sanitize struct {
	Properties SanitizeProperties

	runtimeLibrary          string
	androidMkRuntimeLibrary string
}

func (sanitize *sanitize) props() []interface{} {
	return []interface{}{&sanitize.Properties}
}

func (sanitize *sanitize) begin(ctx BaseModuleContext) {
	s := &sanitize.Properties.Sanitize

	// Don't apply sanitizers to NDK code.
	if ctx.sdk() {
		s.Never = true
	}

	// Never always wins.
	if s.Never {
		return
	}

	var globalSanitizers []string
	var globalSanitizersDiag []string

	if ctx.clang() {
		if ctx.Host() {
			globalSanitizers = ctx.AConfig().SanitizeHost()
		} else {
			arches := ctx.AConfig().SanitizeDeviceArch()
			if len(arches) == 0 || inList(ctx.Arch().ArchType.Name, arches) {
				globalSanitizers = ctx.AConfig().SanitizeDevice()
				globalSanitizersDiag = ctx.AConfig().SanitizeDeviceDiag()
			}
		}
	}

	if len(globalSanitizers) > 0 {
		var found bool
		if found, globalSanitizers = removeFromList("undefined", globalSanitizers); found && s.All_undefined == nil {
			s.All_undefined = boolPtr(true)
		}

		if found, globalSanitizers = removeFromList("default-ub", globalSanitizers); found && s.Undefined == nil {
			s.Undefined = boolPtr(true)
		}

		if found, globalSanitizers = removeFromList("address", globalSanitizers); found {
			if s.Address == nil {
				s.Address = boolPtr(true)
			} else if *s.Address == false {
				// Coverage w/o address is an error. If globalSanitizers includes both, and the module
				// disables address, then disable coverage as well.
				_, globalSanitizers = removeFromList("coverage", globalSanitizers)
			}
		}

		if found, globalSanitizers = removeFromList("thread", globalSanitizers); found && s.Thread == nil {
			s.Thread = boolPtr(true)
		}

		if found, globalSanitizers = removeFromList("coverage", globalSanitizers); found && s.Coverage == nil {
			s.Coverage = boolPtr(true)
		}

		if found, globalSanitizers = removeFromList("safe-stack", globalSanitizers); found && s.Safestack == nil {
			s.Safestack = boolPtr(true)
		}

		if found, globalSanitizers = removeFromList("cfi", globalSanitizers); found && s.Cfi == nil {
			s.Cfi = boolPtr(true)
		}

		if found, globalSanitizers = removeFromList("integer_overflow", globalSanitizers); found && s.Integer_overflow == nil {
			if !ctx.AConfig().IntegerOverflowDisabledForPath(ctx.ModuleDir()) {
				s.Integer_overflow = boolPtr(true)
			}
		}

		if len(globalSanitizers) > 0 {
			ctx.ModuleErrorf("unknown global sanitizer option %s", globalSanitizers[0])
		}

		if found, globalSanitizersDiag = removeFromList("integer_overflow", globalSanitizersDiag); found &&
			s.Diag.Integer_overflow == nil && Bool(s.Integer_overflow) {
			s.Diag.Integer_overflow = boolPtr(true)
		}

		if len(globalSanitizersDiag) > 0 {
			ctx.ModuleErrorf("unknown global sanitizer diagnostics option %s", globalSanitizersDiag[0])
		}
	}

	// CFI needs gold linker, and mips toolchain does not have one.
	if !ctx.AConfig().EnableCFI() || ctx.Arch().ArchType == android.Mips || ctx.Arch().ArchType == android.Mips64 {
		s.Cfi = nil
		s.Diag.Cfi = nil
	}

	// Also disable CFI for arm32 until b/35157333 is fixed.
	if ctx.Arch().ArchType == android.Arm {
		s.Cfi = nil
		s.Diag.Cfi = nil
	}

	// Also disable CFI if ASAN is enabled.
	if Bool(s.Address) {
		s.Cfi = nil
		s.Diag.Cfi = nil
	}

	if ctx.staticBinary() {
		s.Address = nil
		s.Coverage = nil
		s.Thread = nil
	}

	if Bool(s.All_undefined) {
		s.Undefined = nil
	}

	if !ctx.toolchain().Is64Bit() {
		// TSAN and SafeStack are not supported on 32-bit architectures
		s.Thread = nil
		s.Safestack = nil
		// TODO(ccross): error for compile_multilib = "32"?
	}

	if ctx.Os() != android.Windows && (Bool(s.All_undefined) || Bool(s.Undefined) || Bool(s.Address) || Bool(s.Thread) ||
		Bool(s.Coverage) || Bool(s.Safestack) || Bool(s.Cfi) || Bool(s.Integer_overflow) || len(s.Misc_undefined) > 0) {
		sanitize.Properties.SanitizerEnabled = true
	}

	if Bool(s.Coverage) {
		if !Bool(s.Address) {
			ctx.ModuleErrorf(`Use of "coverage" also requires "address"`)
		}
	}
}

func (sanitize *sanitize) deps(ctx BaseModuleContext, deps Deps) Deps {
	if !sanitize.Properties.SanitizerEnabled { // || c.static() {
		return deps
	}

	if ctx.Device() {
		if Bool(sanitize.Properties.Sanitize.Address) {
			deps.StaticLibs = append(deps.StaticLibs, asanLibs...)
		}
	}

	return deps
}

func (sanitize *sanitize) flags(ctx ModuleContext, flags Flags) Flags {
	if !sanitize.Properties.SanitizerEnabled {
		return flags
	}

	if !ctx.clang() {
		ctx.ModuleErrorf("Use of sanitizers requires clang")
	}

	var sanitizers []string
	var diagSanitizers []string

	if Bool(sanitize.Properties.Sanitize.All_undefined) {
		sanitizers = append(sanitizers, "undefined")
		if ctx.Device() {
			ctx.ModuleErrorf("ubsan is not yet supported on the device")
		}
	} else {
		if Bool(sanitize.Properties.Sanitize.Undefined) {
			sanitizers = append(sanitizers,
				"bool",
				"integer-divide-by-zero",
				"return",
				"returns-nonnull-attribute",
				"shift-exponent",
				"unreachable",
				"vla-bound",
				// TODO(danalbert): The following checks currently have compiler performance issues.
				//"alignment",
				//"bounds",
				//"enum",
				//"float-cast-overflow",
				//"float-divide-by-zero",
				//"nonnull-attribute",
				//"null",
				//"shift-base",
				//"signed-integer-overflow",
				// TODO(danalbert): Fix UB in libc++'s __tree so we can turn this on.
				// https://llvm.org/PR19302
				// http://reviews.llvm.org/D6974
				// "object-size",
			)
		}
		sanitizers = append(sanitizers, sanitize.Properties.Sanitize.Misc_undefined...)
	}

	if Bool(sanitize.Properties.Sanitize.Diag.Undefined) {
		diagSanitizers = append(diagSanitizers, "undefined")
	}

	diagSanitizers = append(diagSanitizers, sanitize.Properties.Sanitize.Diag.Misc_undefined...)

	if Bool(sanitize.Properties.Sanitize.Address) {
		if ctx.Arch().ArchType == android.Arm {
			// Frame pointer based unwinder in ASan requires ARM frame setup.
			// TODO: put in flags?
			flags.RequiredInstructionSet = "arm"
		}
		flags.CFlags = append(flags.CFlags, asanCflags...)
		flags.LdFlags = append(flags.LdFlags, asanLdflags...)

		if ctx.Host() {
			// -nodefaultlibs (provided with libc++) prevents the driver from linking
			// libraries needed with -fsanitize=address. http://b/18650275 (WAI)
			flags.LdFlags = append(flags.LdFlags, "-lm", "-lpthread")
			flags.LdFlags = append(flags.LdFlags, "-Wl,--no-as-needed")
		} else {
			flags.CFlags = append(flags.CFlags, "-mllvm", "-asan-globals=0")
			flags.DynamicLinker = "/system/bin/linker_asan"
			if flags.Toolchain.Is64Bit() {
				flags.DynamicLinker += "64"
			}
		}
		sanitizers = append(sanitizers, "address")
		diagSanitizers = append(diagSanitizers, "address")
	}

	if Bool(sanitize.Properties.Sanitize.Coverage) {
		flags.CFlags = append(flags.CFlags, "-fsanitize-coverage=trace-pc-guard")
	}

	if Bool(sanitize.Properties.Sanitize.Safestack) {
		sanitizers = append(sanitizers, "safe-stack")
	}

	if Bool(sanitize.Properties.Sanitize.Cfi) {
		if ctx.Arch().ArchType == android.Arm {
			// __cfi_check needs to be built as Thumb (see the code in linker_cfi.cpp). LLVM is not set up
			// to do this on a function basis, so force Thumb on the entire module.
			flags.RequiredInstructionSet = "thumb"
			// Workaround for b/33678192. CFI jumptables need Thumb2 codegen.  Revert when
			// Clang is updated past r290384.
			flags.LdFlags = append(flags.LdFlags, "-march=armv7-a")
		}
		sanitizers = append(sanitizers, "cfi")
		flags.CFlags = append(flags.CFlags, cfiCflags...)
		flags.LdFlags = append(flags.LdFlags, cfiLdflags...)
		flags.ArFlags = append(flags.ArFlags, cfiArflags...)
		if Bool(sanitize.Properties.Sanitize.Diag.Cfi) {
			diagSanitizers = append(diagSanitizers, "cfi")
		}
	}

	if Bool(sanitize.Properties.Sanitize.Integer_overflow) {
		if !ctx.static() {
			sanitizers = append(sanitizers, "unsigned-integer-overflow")
			sanitizers = append(sanitizers, "signed-integer-overflow")
			flags.CFlags = append(flags.CFlags, intOverflowCflags...)
			if Bool(sanitize.Properties.Sanitize.Diag.Integer_overflow) {
				diagSanitizers = append(diagSanitizers, "unsigned-integer-overflow")
				diagSanitizers = append(diagSanitizers, "signed-integer-overflow")
			}
		}
	}

	if len(sanitizers) > 0 {
		sanitizeArg := "-fsanitize=" + strings.Join(sanitizers, ",")
		flags.CFlags = append(flags.CFlags, sanitizeArg)
		if ctx.Host() {
			flags.CFlags = append(flags.CFlags, "-fno-sanitize-recover=all")
			flags.LdFlags = append(flags.LdFlags, sanitizeArg)
			if ctx.Os() == android.Linux {
				flags.LdFlags = append(flags.LdFlags, "-lrt")
			}
			flags.LdFlags = append(flags.LdFlags, "-ldl")
			// Host sanitizers only link symbols in the final executable, so
			// there will always be undefined symbols in intermediate libraries.
			_, flags.LdFlags = removeFromList("-Wl,--no-undefined", flags.LdFlags)
		} else {
			flags.CFlags = append(flags.CFlags, "-fsanitize-trap=all", "-ftrap-function=abort")
		}
	}

	if len(diagSanitizers) > 0 {
		flags.CFlags = append(flags.CFlags, "-fno-sanitize-trap="+strings.Join(diagSanitizers, ","))
	}
	// FIXME: enable RTTI if diag + (cfi or vptr)

	if sanitize.Properties.Sanitize.Recover != nil {
		flags.CFlags = append(flags.CFlags, "-fsanitize-recover="+
			strings.Join(sanitize.Properties.Sanitize.Recover, ","))
	}

	// Link a runtime library if needed.
	runtimeLibrary := ""
	if Bool(sanitize.Properties.Sanitize.Address) {
		runtimeLibrary = config.AddressSanitizerRuntimeLibrary(ctx.toolchain())
	} else if len(diagSanitizers) > 0 {
		runtimeLibrary = config.UndefinedBehaviorSanitizerRuntimeLibrary(ctx.toolchain())
	}

	if runtimeLibrary != "" {
		// ASan runtime library must be the first in the link order.
		flags.libFlags = append([]string{
			"${config.ClangAsanLibDir}/" + runtimeLibrary + ctx.toolchain().ShlibSuffix(),
		}, flags.libFlags...)
		sanitize.runtimeLibrary = runtimeLibrary

		// When linking against VNDK, use the vendor variant of the runtime lib
		sanitize.androidMkRuntimeLibrary = sanitize.runtimeLibrary
		if ctx.vndk() {
			sanitize.androidMkRuntimeLibrary = sanitize.runtimeLibrary + vendorSuffix
		}
	}

	blacklist := android.OptionalPathForModuleSrc(ctx, sanitize.Properties.Sanitize.Blacklist)
	if blacklist.Valid() {
		flags.CFlags = append(flags.CFlags, "-fsanitize-blacklist="+blacklist.String())
		flags.CFlagsDeps = append(flags.CFlagsDeps, blacklist.Path())
	}

	return flags
}

func (sanitize *sanitize) AndroidMk(ctx AndroidMkContext, ret *android.AndroidMkData) {
	ret.Extra = append(ret.Extra, func(w io.Writer, outputFile android.Path) error {
		if sanitize.androidMkRuntimeLibrary != "" {
			fmt.Fprintln(w, "LOCAL_SHARED_LIBRARIES += "+sanitize.androidMkRuntimeLibrary)
		}

		return nil
	})
}

func (sanitize *sanitize) inSanitizerDir() bool {
	return sanitize.Properties.InSanitizerDir
}

func (sanitize *sanitize) Sanitizer(t sanitizerType) bool {
	if sanitize == nil {
		return false
	}

	switch t {
	case asan:
		return Bool(sanitize.Properties.Sanitize.Address)
	case tsan:
		return Bool(sanitize.Properties.Sanitize.Thread)
	case intOverflow:
		return Bool(sanitize.Properties.Sanitize.Integer_overflow)
	default:
		panic(fmt.Errorf("unknown sanitizerType %d", t))
	}
}

func (sanitize *sanitize) SetSanitizer(t sanitizerType, b bool) {
	switch t {
	case asan:
		sanitize.Properties.Sanitize.Address = boolPtr(b)
		if !b {
			sanitize.Properties.Sanitize.Coverage = nil
		}
	case tsan:
		sanitize.Properties.Sanitize.Thread = boolPtr(b)
	case intOverflow:
		sanitize.Properties.Sanitize.Integer_overflow = boolPtr(b)
	default:
		panic(fmt.Errorf("unknown sanitizerType %d", t))
	}
	if b {
		sanitize.Properties.SanitizerEnabled = true
	}
}

// Propagate asan requirements down from binaries
func sanitizerDepsMutator(t sanitizerType) func(android.TopDownMutatorContext) {
	return func(mctx android.TopDownMutatorContext) {
		if c, ok := mctx.Module().(*Module); ok && c.sanitize.Sanitizer(t) {
			mctx.VisitDepsDepthFirst(func(module blueprint.Module) {
				if d, ok := mctx.Module().(*Module); ok && c.sanitize != nil &&
					!c.sanitize.Properties.Sanitize.Never {
					d.sanitize.Properties.SanitizeDep = true
				}
			})
		}
	}
}

// Create asan variants for modules that need them
func sanitizerMutator(t sanitizerType) func(android.BottomUpMutatorContext) {
	return func(mctx android.BottomUpMutatorContext) {
		if c, ok := mctx.Module().(*Module); ok && c.sanitize != nil {
			if c.isDependencyRoot() && c.sanitize.Sanitizer(t) {
				modules := mctx.CreateVariations(t.String())
				modules[0].(*Module).sanitize.SetSanitizer(t, true)
			} else if c.sanitize.Properties.SanitizeDep {
				modules := mctx.CreateVariations("", t.String())
				modules[0].(*Module).sanitize.SetSanitizer(t, false)
				modules[1].(*Module).sanitize.SetSanitizer(t, true)
				modules[0].(*Module).sanitize.Properties.SanitizeDep = false
				modules[1].(*Module).sanitize.Properties.SanitizeDep = false
				if mctx.Device() {
					modules[1].(*Module).sanitize.Properties.InSanitizerDir = true
				} else {
					modules[0].(*Module).Properties.PreventInstall = true
				}
				if mctx.AConfig().EmbeddedInMake() {
					modules[0].(*Module).Properties.HideFromMake = true
				}
			}
			c.sanitize.Properties.SanitizeDep = false
		}
	}
}
