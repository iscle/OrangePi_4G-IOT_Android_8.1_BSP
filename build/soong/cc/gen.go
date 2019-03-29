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
	"github.com/google/blueprint"

	"android/soong/android"
)

func init() {
	pctx.SourcePathVariable("lexCmd", "prebuilts/misc/${config.HostPrebuiltTag}/flex/flex-2.5.39")
	pctx.SourcePathVariable("yaccCmd", "prebuilts/misc/${config.HostPrebuiltTag}/bison/bison")
	pctx.SourcePathVariable("yaccDataDir", "external/bison/data")

	pctx.HostBinToolVariable("aidlCmd", "aidl-cpp")
}

var (
	yacc = pctx.AndroidStaticRule("yacc",
		blueprint.RuleParams{
			Command:     "BISON_PKGDATADIR=$yaccDataDir $yaccCmd -d $yaccFlags --defines=$hFile -o $out $in",
			CommandDeps: []string{"$yaccCmd"},
		},
		"yaccFlags", "hFile")

	lex = pctx.AndroidStaticRule("lex",
		blueprint.RuleParams{
			Command:     "$lexCmd -o$out $in",
			CommandDeps: []string{"$lexCmd"},
		})

	aidl = pctx.AndroidStaticRule("aidl",
		blueprint.RuleParams{
			Command:     "$aidlCmd -d${out}.d -ninja $aidlFlags $in $outDir $out",
			CommandDeps: []string{"$aidlCmd"},
			Depfile:     "${out}.d",
			Deps:        blueprint.DepsGCC,
		},
		"aidlFlags", "outDir")
)

func genYacc(ctx android.ModuleContext, yaccFile android.Path, outFile android.ModuleGenPath, yaccFlags string) (headerFile android.ModuleGenPath) {
	headerFile = android.GenPathWithExt(ctx, "yacc", yaccFile, "h")

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:           yacc,
		Description:    "yacc " + yaccFile.Rel(),
		Output:         outFile,
		ImplicitOutput: headerFile,
		Input:          yaccFile,
		Args: map[string]string{
			"yaccFlags": yaccFlags,
			"hFile":     headerFile.String(),
		},
	})

	return headerFile
}

func genAidl(ctx android.ModuleContext, aidlFile android.Path, outFile android.ModuleGenPath, aidlFlags string) android.Paths {

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        aidl,
		Description: "aidl " + aidlFile.Rel(),
		Output:      outFile,
		Input:       aidlFile,
		Args: map[string]string{
			"aidlFlags": aidlFlags,
			"outDir":    android.PathForModuleGen(ctx, "aidl").String(),
		},
	})

	// TODO: This should return the generated headers, not the source file.
	return android.Paths{outFile}
}

func genLex(ctx android.ModuleContext, lexFile android.Path, outFile android.ModuleGenPath) {
	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        lex,
		Description: "lex " + lexFile.Rel(),
		Output:      outFile,
		Input:       lexFile,
	})
}

func genSources(ctx android.ModuleContext, srcFiles android.Paths,
	buildFlags builderFlags) (android.Paths, android.Paths) {

	var deps android.Paths

	var rsFiles android.Paths

	for i, srcFile := range srcFiles {
		switch srcFile.Ext() {
		case ".y":
			cFile := android.GenPathWithExt(ctx, "yacc", srcFile, "c")
			srcFiles[i] = cFile
			deps = append(deps, genYacc(ctx, srcFile, cFile, buildFlags.yaccFlags))
		case ".yy":
			cppFile := android.GenPathWithExt(ctx, "yacc", srcFile, "cpp")
			srcFiles[i] = cppFile
			deps = append(deps, genYacc(ctx, srcFile, cppFile, buildFlags.yaccFlags))
		case ".l":
			cFile := android.GenPathWithExt(ctx, "lex", srcFile, "c")
			srcFiles[i] = cFile
			genLex(ctx, srcFile, cFile)
		case ".ll":
			cppFile := android.GenPathWithExt(ctx, "lex", srcFile, "cpp")
			srcFiles[i] = cppFile
			genLex(ctx, srcFile, cppFile)
		case ".proto":
			cppFile, headerFile := genProto(ctx, srcFile, buildFlags.protoFlags)
			srcFiles[i] = cppFile
			deps = append(deps, headerFile)
		case ".aidl":
			cppFile := android.GenPathWithExt(ctx, "aidl", srcFile, "cpp")
			srcFiles[i] = cppFile
			deps = append(deps, genAidl(ctx, srcFile, cppFile, buildFlags.aidlFlags)...)
		case ".rs", ".fs":
			cppFile := rsGeneratedCppFile(ctx, srcFile)
			rsFiles = append(rsFiles, srcFiles[i])
			srcFiles[i] = cppFile
		}
	}

	if len(rsFiles) > 0 {
		deps = append(deps, rsGenerateCpp(ctx, rsFiles, buildFlags.rsFlags)...)
	}

	return srcFiles, deps
}
