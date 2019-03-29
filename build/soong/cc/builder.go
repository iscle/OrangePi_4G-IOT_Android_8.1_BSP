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

package cc

// This file generates the final rules for compiling all C/C++.  All properties related to
// compiling should have been translated into builderFlags or another argument to the Transform*
// functions.

import (
	"fmt"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"

	"github.com/google/blueprint"

	"android/soong/android"
	"android/soong/cc/config"
)

const (
	objectExtension        = ".o"
	staticLibraryExtension = ".a"
)

var (
	pctx = android.NewPackageContext("android/soong/cc")

	cc = pctx.AndroidGomaStaticRule("cc",
		blueprint.RuleParams{
			Depfile:     "${out}.d",
			Deps:        blueprint.DepsGCC,
			Command:     "$relPwd ${config.CcWrapper}$ccCmd -c $cFlags -MD -MF ${out}.d -o $out $in",
			CommandDeps: []string{"$ccCmd"},
		},
		"ccCmd", "cFlags")

	ld = pctx.AndroidStaticRule("ld",
		blueprint.RuleParams{
			Command: "$ldCmd ${crtBegin} @${out}.rsp " +
				"${libFlags} ${crtEnd} -o ${out} ${ldFlags}",
			CommandDeps:    []string{"$ldCmd"},
			Rspfile:        "${out}.rsp",
			RspfileContent: "${in}",
		},
		"ldCmd", "crtBegin", "libFlags", "crtEnd", "ldFlags")

	partialLd = pctx.AndroidStaticRule("partialLd",
		blueprint.RuleParams{
			Command:     "$ldCmd -nostdlib -Wl,-r ${in} -o ${out} ${ldFlags}",
			CommandDeps: []string{"$ldCmd"},
		},
		"ldCmd", "ldFlags")

	ar = pctx.AndroidStaticRule("ar",
		blueprint.RuleParams{
			Command:        "rm -f ${out} && $arCmd $arFlags $out @${out}.rsp",
			CommandDeps:    []string{"$arCmd"},
			Rspfile:        "${out}.rsp",
			RspfileContent: "${in}",
		},
		"arCmd", "arFlags")

	darwinAr = pctx.AndroidStaticRule("darwinAr",
		blueprint.RuleParams{
			Command:     "rm -f ${out} && ${config.MacArPath} $arFlags $out $in",
			CommandDeps: []string{"${config.MacArPath}"},
		},
		"arFlags")

	darwinAppendAr = pctx.AndroidStaticRule("darwinAppendAr",
		blueprint.RuleParams{
			Command:     "cp -f ${inAr} ${out}.tmp && ${config.MacArPath} $arFlags ${out}.tmp $in && mv ${out}.tmp ${out}",
			CommandDeps: []string{"${config.MacArPath}", "${inAr}"},
		},
		"arFlags", "inAr")

	darwinStrip = pctx.AndroidStaticRule("darwinStrip",
		blueprint.RuleParams{
			Command:     "${config.MacStripPath} -u -r -o $out $in",
			CommandDeps: []string{"${config.MacStripPath}"},
		})

	prefixSymbols = pctx.AndroidStaticRule("prefixSymbols",
		blueprint.RuleParams{
			Command:     "$objcopyCmd --prefix-symbols=${prefix} ${in} ${out}",
			CommandDeps: []string{"$objcopyCmd"},
		},
		"objcopyCmd", "prefix")

	_ = pctx.SourcePathVariable("stripPath", "build/soong/scripts/strip.sh")

	strip = pctx.AndroidStaticRule("strip",
		blueprint.RuleParams{
			Depfile:     "${out}.d",
			Deps:        blueprint.DepsGCC,
			Command:     "CROSS_COMPILE=$crossCompile $stripPath ${args} -i ${in} -o ${out} -d ${out}.d",
			CommandDeps: []string{"$stripPath"},
		},
		"args", "crossCompile")

	emptyFile = pctx.AndroidStaticRule("emptyFile",
		blueprint.RuleParams{
			Command: "rm -f $out && touch $out",
		})

	_ = pctx.SourcePathVariable("copyGccLibPath", "build/soong/scripts/copygcclib.sh")

	copyGccLib = pctx.AndroidStaticRule("copyGccLib",
		blueprint.RuleParams{
			Depfile:     "${out}.d",
			Deps:        blueprint.DepsGCC,
			Command:     "$copyGccLibPath $out $ccCmd $cFlags -print-file-name=${libName}",
			CommandDeps: []string{"$copyGccLibPath", "$ccCmd"},
		},
		"ccCmd", "cFlags", "libName")

	_ = pctx.SourcePathVariable("tocPath", "build/soong/scripts/toc.sh")

	toc = pctx.AndroidStaticRule("toc",
		blueprint.RuleParams{
			Depfile:     "${out}.d",
			Deps:        blueprint.DepsGCC,
			Command:     "CROSS_COMPILE=$crossCompile $tocPath -i ${in} -o ${out} -d ${out}.d",
			CommandDeps: []string{"$tocPath"},
			Restat:      true,
		},
		"crossCompile")

	clangTidy = pctx.AndroidStaticRule("clangTidy",
		blueprint.RuleParams{
			Command:     "rm -f $out && ${config.ClangBin}/clang-tidy $tidyFlags $in -- $cFlags && touch $out",
			CommandDeps: []string{"${config.ClangBin}/clang-tidy"},
		},
		"cFlags", "tidyFlags")

	_ = pctx.SourcePathVariable("yasmCmd", "prebuilts/misc/${config.HostPrebuiltTag}/yasm/yasm")

	yasm = pctx.AndroidStaticRule("yasm",
		blueprint.RuleParams{
			Command:     "$yasmCmd $asFlags -o $out $in",
			CommandDeps: []string{"$yasmCmd"},
		},
		"asFlags")

	_ = pctx.SourcePathVariable("sAbiDumper", "prebuilts/build-tools/${config.HostPrebuiltTag}/bin/header-abi-dumper")

	// -w has been added since header-abi-dumper does not need to produce any sort of diagnostic information.
	sAbiDump = pctx.AndroidStaticRule("sAbiDump",
		blueprint.RuleParams{
			Command:     "rm -f $out && $sAbiDumper -o ${out} $in $exportDirs -- $cFlags -w -isystem ${config.RSIncludePath}",
			CommandDeps: []string{"$sAbiDumper"},
		},
		"cFlags", "exportDirs")

	_ = pctx.SourcePathVariable("sAbiLinker", "prebuilts/build-tools/${config.HostPrebuiltTag}/bin/header-abi-linker")

	sAbiLink = pctx.AndroidStaticRule("sAbiLink",
		blueprint.RuleParams{
			Command:        "$sAbiLinker -o ${out} $symbolFilter -arch $arch -api $api $exportedHeaderFlags @${out}.rsp ",
			CommandDeps:    []string{"$sAbiLinker"},
			Rspfile:        "${out}.rsp",
			RspfileContent: "${in}",
		},
		"symbolFilter", "arch", "api", "exportedHeaderFlags")

	_ = pctx.SourcePathVariable("sAbiDiffer", "prebuilts/build-tools/${config.HostPrebuiltTag}/bin/header-abi-diff")

	// Abidiff check turned on in advice-only mode. Builds will not fail on abi incompatibilties / extensions.
	sAbiDiff = pctx.AndroidStaticRule("sAbiDiff",
		blueprint.RuleParams{
			Command:     "$sAbiDiffer -lib $libName -arch $arch -advice-only -o ${out} -new $in -old $referenceDump",
			CommandDeps: []string{"$sAbiDiffer"},
		},
		"referenceDump", "libName", "arch")

	unzipRefSAbiDump = pctx.AndroidStaticRule("unzipRefSAbiDump",
		blueprint.RuleParams{
			Command: "gunzip -c $in > $out",
		})
)

func init() {
	// We run gcc/clang with PWD=/proc/self/cwd to remove $TOP from the
	// debug output. That way two builds in two different directories will
	// create the same output.
	if runtime.GOOS != "darwin" {
		pctx.StaticVariable("relPwd", "PWD=/proc/self/cwd")
	} else {
		// Darwin doesn't have /proc
		pctx.StaticVariable("relPwd", "")
	}
}

type builderFlags struct {
	globalFlags   string
	arFlags       string
	asFlags       string
	cFlags        string
	toolingCFlags string // Seperate set of Cflags for clang LibTooling tools
	conlyFlags    string
	cppFlags      string
	ldFlags       string
	libFlags      string
	yaccFlags     string
	protoFlags    string
	tidyFlags     string
	sAbiFlags     string
	yasmFlags     string
	aidlFlags     string
	rsFlags       string
	toolchain     config.Toolchain
	clang         bool
	tidy          bool
	coverage      bool
	sAbiDump      bool

	systemIncludeFlags string

	groupStaticLibs bool

	stripKeepSymbols       bool
	stripKeepMiniDebugInfo bool
	stripAddGnuDebuglink   bool
}

type Objects struct {
	objFiles      android.Paths
	tidyFiles     android.Paths
	coverageFiles android.Paths
	sAbiDumpFiles android.Paths
}

func (a Objects) Copy() Objects {
	return Objects{
		objFiles:      append(android.Paths{}, a.objFiles...),
		tidyFiles:     append(android.Paths{}, a.tidyFiles...),
		coverageFiles: append(android.Paths{}, a.coverageFiles...),
		sAbiDumpFiles: append(android.Paths{}, a.sAbiDumpFiles...),
	}
}

func (a Objects) Append(b Objects) Objects {
	return Objects{
		objFiles:      append(a.objFiles, b.objFiles...),
		tidyFiles:     append(a.tidyFiles, b.tidyFiles...),
		coverageFiles: append(a.coverageFiles, b.coverageFiles...),
		sAbiDumpFiles: append(a.sAbiDumpFiles, b.sAbiDumpFiles...),
	}
}

// Generate rules for compiling multiple .c, .cpp, or .S files to individual .o files
func TransformSourceToObj(ctx android.ModuleContext, subdir string, srcFiles android.Paths,
	flags builderFlags, deps android.Paths) Objects {

	objFiles := make(android.Paths, len(srcFiles))
	var tidyFiles android.Paths
	if flags.tidy && flags.clang {
		tidyFiles = make(android.Paths, 0, len(srcFiles))
	}
	var coverageFiles android.Paths
	if flags.coverage {
		coverageFiles = make(android.Paths, 0, len(srcFiles))
	}

	commonFlags := strings.Join([]string{
		flags.globalFlags,
		flags.systemIncludeFlags,
	}, " ")

	toolingCflags := strings.Join([]string{
		commonFlags,
		flags.toolingCFlags,
		flags.conlyFlags,
	}, " ")

	cflags := strings.Join([]string{
		commonFlags,
		flags.cFlags,
		flags.conlyFlags,
	}, " ")

	toolingCppflags := strings.Join([]string{
		commonFlags,
		flags.toolingCFlags,
		flags.cppFlags,
	}, " ")

	cppflags := strings.Join([]string{
		commonFlags,
		flags.cFlags,
		flags.cppFlags,
	}, " ")

	asflags := strings.Join([]string{
		commonFlags,
		flags.asFlags,
	}, " ")

	var sAbiDumpFiles android.Paths
	if flags.sAbiDump && flags.clang {
		sAbiDumpFiles = make(android.Paths, 0, len(srcFiles))
	}

	if flags.clang {
		cflags += " ${config.NoOverrideClangGlobalCflags}"
		toolingCflags += " ${config.NoOverrideClangGlobalCflags}"
		cppflags += " ${config.NoOverrideClangGlobalCflags}"
		toolingCppflags += " ${config.NoOverrideClangGlobalCflags}"
	} else {
		cflags += " ${config.NoOverrideGlobalCflags}"
		cppflags += " ${config.NoOverrideGlobalCflags}"
	}

	for i, srcFile := range srcFiles {
		objFile := android.ObjPathWithExt(ctx, subdir, srcFile, "o")

		objFiles[i] = objFile

		if srcFile.Ext() == ".asm" {
			ctx.ModuleBuild(pctx, android.ModuleBuildParams{
				Rule:        yasm,
				Description: "yasm " + srcFile.Rel(),
				Output:      objFile,
				Input:       srcFile,
				OrderOnly:   deps,
				Args: map[string]string{
					"asFlags": flags.yasmFlags,
				},
			})
			continue
		}

		var moduleCflags string
		var moduleToolingCflags string
		var ccCmd string
		tidy := flags.tidy && flags.clang
		coverage := flags.coverage
		dump := flags.sAbiDump && flags.clang

		switch srcFile.Ext() {
		case ".S", ".s":
			ccCmd = "gcc"
			moduleCflags = asflags
			tidy = false
			coverage = false
			dump = false
		case ".c":
			ccCmd = "gcc"
			moduleCflags = cflags
			moduleToolingCflags = toolingCflags
		case ".cpp", ".cc", ".mm":
			ccCmd = "g++"
			moduleCflags = cppflags
			moduleToolingCflags = toolingCppflags
		default:
			ctx.ModuleErrorf("File %s has unknown extension", srcFile)
			continue
		}

		if flags.clang {
			switch ccCmd {
			case "gcc":
				ccCmd = "clang"
			case "g++":
				ccCmd = "clang++"
			default:
				panic("unrecoginzied ccCmd")
			}
		}

		ccDesc := ccCmd

		if flags.clang {
			ccCmd = "${config.ClangBin}/" + ccCmd
		} else {
			ccCmd = gccCmd(flags.toolchain, ccCmd)
		}

		var implicitOutputs android.WritablePaths
		if coverage {
			gcnoFile := android.ObjPathWithExt(ctx, subdir, srcFile, "gcno")
			implicitOutputs = append(implicitOutputs, gcnoFile)
			coverageFiles = append(coverageFiles, gcnoFile)
		}

		ctx.ModuleBuild(pctx, android.ModuleBuildParams{
			Rule:            cc,
			Description:     ccDesc + " " + srcFile.Rel(),
			Output:          objFile,
			ImplicitOutputs: implicitOutputs,
			Input:           srcFile,
			OrderOnly:       deps,
			Args: map[string]string{
				"cFlags": moduleCflags,
				"ccCmd":  ccCmd,
			},
		})

		if tidy {
			tidyFile := android.ObjPathWithExt(ctx, subdir, srcFile, "tidy")
			tidyFiles = append(tidyFiles, tidyFile)

			ctx.ModuleBuild(pctx, android.ModuleBuildParams{
				Rule:        clangTidy,
				Description: "clang-tidy " + srcFile.Rel(),
				Output:      tidyFile,
				Input:       srcFile,
				// We must depend on objFile, since clang-tidy doesn't
				// support exporting dependencies.
				Implicit: objFile,
				Args: map[string]string{
					"cFlags":    moduleToolingCflags,
					"tidyFlags": flags.tidyFlags,
				},
			})
		}

		if dump {
			sAbiDumpFile := android.ObjPathWithExt(ctx, subdir, srcFile, "sdump")
			sAbiDumpFiles = append(sAbiDumpFiles, sAbiDumpFile)

			ctx.ModuleBuild(pctx, android.ModuleBuildParams{
				Rule:        sAbiDump,
				Description: "header-abi-dumper " + srcFile.Rel(),
				Output:      sAbiDumpFile,
				Input:       srcFile,
				Implicit:    objFile,
				Args: map[string]string{
					"cFlags":     moduleToolingCflags,
					"exportDirs": flags.sAbiFlags,
				},
			})
		}

	}

	return Objects{
		objFiles:      objFiles,
		tidyFiles:     tidyFiles,
		coverageFiles: coverageFiles,
		sAbiDumpFiles: sAbiDumpFiles,
	}
}

// Generate a rule for compiling multiple .o files to a static library (.a)
func TransformObjToStaticLib(ctx android.ModuleContext, objFiles android.Paths,
	flags builderFlags, outputFile android.ModuleOutPath, deps android.Paths) {

	if ctx.Darwin() {
		transformDarwinObjToStaticLib(ctx, objFiles, flags, outputFile, deps)
		return
	}

	arCmd := gccCmd(flags.toolchain, "ar")
	arFlags := "crsPD"
	if flags.arFlags != "" {
		arFlags += " " + flags.arFlags
	}

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        ar,
		Description: "static link " + outputFile.Base(),
		Output:      outputFile,
		Inputs:      objFiles,
		Implicits:   deps,
		Args: map[string]string{
			"arFlags": arFlags,
			"arCmd":   arCmd,
		},
	})
}

// Generate a rule for compiling multiple .o files to a static library (.a) on
// darwin.  The darwin ar tool doesn't support @file for list files, and has a
// very small command line length limit, so we have to split the ar into multiple
// steps, each appending to the previous one.
func transformDarwinObjToStaticLib(ctx android.ModuleContext, objFiles android.Paths,
	flags builderFlags, outputFile android.ModuleOutPath, deps android.Paths) {

	arFlags := "cqs"

	if len(objFiles) == 0 {
		dummy := android.PathForModuleOut(ctx, "dummy"+objectExtension)
		dummyAr := android.PathForModuleOut(ctx, "dummy"+staticLibraryExtension)

		ctx.ModuleBuild(pctx, android.ModuleBuildParams{
			Rule:        emptyFile,
			Description: "empty object file",
			Output:      dummy,
			Implicits:   deps,
		})

		ctx.ModuleBuild(pctx, android.ModuleBuildParams{
			Rule:        darwinAr,
			Description: "empty static archive",
			Output:      dummyAr,
			Input:       dummy,
			Args: map[string]string{
				"arFlags": arFlags,
			},
		})

		ctx.ModuleBuild(pctx, android.ModuleBuildParams{
			Rule:        darwinAppendAr,
			Description: "static link " + outputFile.Base(),
			Output:      outputFile,
			Input:       dummy,
			Args: map[string]string{
				"arFlags": "d",
				"inAr":    dummyAr.String(),
			},
		})

		return
	}

	// ARG_MAX on darwin is 262144, use half that to be safe
	objFilesLists, err := splitListForSize(objFiles, 131072)
	if err != nil {
		ctx.ModuleErrorf("%s", err.Error())
	}

	var in, out android.WritablePath
	for i, l := range objFilesLists {
		in = out
		out = outputFile
		if i != len(objFilesLists)-1 {
			out = android.PathForModuleOut(ctx, outputFile.Base()+strconv.Itoa(i))
		}

		build := android.ModuleBuildParams{
			Rule:        darwinAr,
			Description: "static link " + out.Base(),
			Output:      out,
			Inputs:      l,
			Implicits:   deps,
			Args: map[string]string{
				"arFlags": arFlags,
			},
		}
		if i != 0 {
			build.Rule = darwinAppendAr
			build.Args["inAr"] = in.String()
		}
		ctx.ModuleBuild(pctx, build)
	}
}

// Generate a rule for compiling multiple .o files, plus static libraries, whole static libraries,
// and shared libraires, to a shared library (.so) or dynamic executable
func TransformObjToDynamicBinary(ctx android.ModuleContext,
	objFiles, sharedLibs, staticLibs, lateStaticLibs, wholeStaticLibs, deps android.Paths,
	crtBegin, crtEnd android.OptionalPath, groupLate bool, flags builderFlags, outputFile android.WritablePath) {

	var ldCmd string
	if flags.clang {
		ldCmd = "${config.ClangBin}/clang++"
	} else {
		ldCmd = gccCmd(flags.toolchain, "g++")
	}

	var libFlagsList []string

	if len(flags.libFlags) > 0 {
		libFlagsList = append(libFlagsList, flags.libFlags)
	}

	if len(wholeStaticLibs) > 0 {
		if ctx.Host() && ctx.Darwin() {
			libFlagsList = append(libFlagsList, android.JoinWithPrefix(wholeStaticLibs.Strings(), "-force_load "))
		} else {
			libFlagsList = append(libFlagsList, "-Wl,--whole-archive ")
			libFlagsList = append(libFlagsList, wholeStaticLibs.Strings()...)
			libFlagsList = append(libFlagsList, "-Wl,--no-whole-archive ")
		}
	}

	if flags.groupStaticLibs && !ctx.Darwin() && len(staticLibs) > 0 {
		libFlagsList = append(libFlagsList, "-Wl,--start-group")
	}
	libFlagsList = append(libFlagsList, staticLibs.Strings()...)
	if flags.groupStaticLibs && !ctx.Darwin() && len(staticLibs) > 0 {
		libFlagsList = append(libFlagsList, "-Wl,--end-group")
	}

	if groupLate && !ctx.Darwin() && len(lateStaticLibs) > 0 {
		libFlagsList = append(libFlagsList, "-Wl,--start-group")
	}
	libFlagsList = append(libFlagsList, lateStaticLibs.Strings()...)
	if groupLate && !ctx.Darwin() && len(lateStaticLibs) > 0 {
		libFlagsList = append(libFlagsList, "-Wl,--end-group")
	}

	for _, lib := range sharedLibs {
		libFlagsList = append(libFlagsList, lib.String())
	}

	deps = append(deps, staticLibs...)
	deps = append(deps, lateStaticLibs...)
	deps = append(deps, wholeStaticLibs...)
	if crtBegin.Valid() {
		deps = append(deps, crtBegin.Path(), crtEnd.Path())
	}

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        ld,
		Description: "link " + outputFile.Base(),
		Output:      outputFile,
		Inputs:      objFiles,
		Implicits:   deps,
		Args: map[string]string{
			"ldCmd":    ldCmd,
			"crtBegin": crtBegin.String(),
			"libFlags": strings.Join(libFlagsList, " "),
			"ldFlags":  flags.ldFlags,
			"crtEnd":   crtEnd.String(),
		},
	})
}

// Generate a rule to combine .dump sAbi dump files from multiple source files
// into a single .ldump sAbi dump file
func TransformDumpToLinkedDump(ctx android.ModuleContext, sAbiDumps android.Paths, soFile android.Path,
	symbolFile android.OptionalPath, apiLevel, baseName, exportedHeaderFlags string) android.OptionalPath {
	outputFile := android.PathForModuleOut(ctx, baseName+".lsdump")
	var symbolFilterStr string
	var linkedDumpDep android.Path
	if symbolFile.Valid() {
		symbolFilterStr = "-v " + symbolFile.Path().String()
		linkedDumpDep = symbolFile.Path()
	} else {
		linkedDumpDep = soFile
		symbolFilterStr = "-so " + soFile.String()
	}
	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        sAbiLink,
		Description: "header-abi-linker " + outputFile.Base(),
		Output:      outputFile,
		Inputs:      sAbiDumps,
		Implicit:    linkedDumpDep,
		Args: map[string]string{
			"symbolFilter": symbolFilterStr,
			"arch":         ctx.Arch().ArchType.Name,
			"api":          apiLevel,
			"exportedHeaderFlags": exportedHeaderFlags,
		},
	})
	return android.OptionalPathForPath(outputFile)
}

func UnzipRefDump(ctx android.ModuleContext, zippedRefDump android.Path, baseName string) android.Path {
	outputFile := android.PathForModuleOut(ctx, baseName+"_ref.lsdump")
	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        unzipRefSAbiDump,
		Description: "gunzip" + outputFile.Base(),
		Output:      outputFile,
		Input:       zippedRefDump,
	})
	return outputFile
}

func SourceAbiDiff(ctx android.ModuleContext, inputDump android.Path, referenceDump android.Path,
	baseName string) android.OptionalPath {
	outputFile := android.PathForModuleOut(ctx, baseName+".abidiff")
	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        sAbiDiff,
		Description: "header-abi-diff " + outputFile.Base(),
		Output:      outputFile,
		Input:       inputDump,
		Implicit:    referenceDump,
		Args: map[string]string{
			"referenceDump": referenceDump.String(),
			"libName":       baseName,
			"arch":          ctx.Arch().ArchType.Name,
		},
	})
	return android.OptionalPathForPath(outputFile)
}

// Generate a rule for extract a table of contents from a shared library (.so)
func TransformSharedObjectToToc(ctx android.ModuleContext, inputFile android.WritablePath,
	outputFile android.WritablePath, flags builderFlags) {

	crossCompile := gccCmd(flags.toolchain, "")

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        toc,
		Description: "generate toc " + inputFile.Base(),
		Output:      outputFile,
		Input:       inputFile,
		Args: map[string]string{
			"crossCompile": crossCompile,
		},
	})
}

// Generate a rule for compiling multiple .o files to a .o using ld partial linking
func TransformObjsToObj(ctx android.ModuleContext, objFiles android.Paths,
	flags builderFlags, outputFile android.WritablePath) {

	var ldCmd string
	if flags.clang {
		ldCmd = "${config.ClangBin}/clang++"
	} else {
		ldCmd = gccCmd(flags.toolchain, "g++")
	}

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        partialLd,
		Description: "link " + outputFile.Base(),
		Output:      outputFile,
		Inputs:      objFiles,
		Args: map[string]string{
			"ldCmd":   ldCmd,
			"ldFlags": flags.ldFlags,
		},
	})
}

// Generate a rule for runing objcopy --prefix-symbols on a binary
func TransformBinaryPrefixSymbols(ctx android.ModuleContext, prefix string, inputFile android.Path,
	flags builderFlags, outputFile android.WritablePath) {

	objcopyCmd := gccCmd(flags.toolchain, "objcopy")

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        prefixSymbols,
		Description: "prefix symbols " + outputFile.Base(),
		Output:      outputFile,
		Input:       inputFile,
		Args: map[string]string{
			"objcopyCmd": objcopyCmd,
			"prefix":     prefix,
		},
	})
}

func TransformStrip(ctx android.ModuleContext, inputFile android.Path,
	outputFile android.WritablePath, flags builderFlags) {

	crossCompile := gccCmd(flags.toolchain, "")
	args := ""
	if flags.stripAddGnuDebuglink {
		args += " --add-gnu-debuglink"
	}
	if flags.stripKeepMiniDebugInfo {
		args += " --keep-mini-debug-info"
	}
	if flags.stripKeepSymbols {
		args += " --keep-symbols"
	}

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        strip,
		Description: "strip " + outputFile.Base(),
		Output:      outputFile,
		Input:       inputFile,
		Args: map[string]string{
			"crossCompile": crossCompile,
			"args":         args,
		},
	})
}

func TransformDarwinStrip(ctx android.ModuleContext, inputFile android.Path,
	outputFile android.WritablePath) {

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        darwinStrip,
		Description: "strip " + outputFile.Base(),
		Output:      outputFile,
		Input:       inputFile,
	})
}

func TransformCoverageFilesToLib(ctx android.ModuleContext,
	inputs Objects, flags builderFlags, baseName string) android.OptionalPath {

	if len(inputs.coverageFiles) > 0 {
		outputFile := android.PathForModuleOut(ctx, baseName+".gcnodir")

		TransformObjToStaticLib(ctx, inputs.coverageFiles, flags, outputFile, nil)

		return android.OptionalPathForPath(outputFile)
	}

	return android.OptionalPath{}
}

func CopyGccLib(ctx android.ModuleContext, libName string,
	flags builderFlags, outputFile android.WritablePath) {

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        copyGccLib,
		Description: "copy gcc library " + libName,
		Output:      outputFile,
		Args: map[string]string{
			"ccCmd":   gccCmd(flags.toolchain, "gcc"),
			"cFlags":  flags.globalFlags,
			"libName": libName,
		},
	})
}

func gccCmd(toolchain config.Toolchain, cmd string) string {
	return filepath.Join(toolchain.GccRoot(), "bin", toolchain.GccTriple()+"-"+cmd)
}

func splitListForSize(list android.Paths, limit int) (lists []android.Paths, err error) {
	var i int

	start := 0
	bytes := 0
	for i = range list {
		l := len(list[i].String())
		if l > limit {
			return nil, fmt.Errorf("list element greater than size limit (%d)", limit)
		}
		if bytes+l > limit {
			lists = append(lists, list[start:i])
			start = i
			bytes = 0
		}
		bytes += l + 1 // count a space between each list element
	}

	lists = append(lists, list[start:])

	totalLen := 0
	for _, l := range lists {
		totalLen += len(l)
	}
	if totalLen != len(list) {
		panic(fmt.Errorf("Failed breaking up list, %d != %d", len(list), totalLen))
	}
	return lists, nil
}
