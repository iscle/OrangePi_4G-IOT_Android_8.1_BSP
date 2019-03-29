// Copyright 2017 Google Inc. All rights reserved.
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
	"android/soong/android"
	"strings"

	"github.com/google/blueprint"
	"github.com/google/blueprint/proptools"
)

func init() {
	pctx.HostBinToolVariable("rsCmd", "llvm-rs-cc")
}

var rsCppCmdLine = strings.Replace(`
${rsCmd} -o ${outDir} -d ${outDir} -a ${out} -MD -reflect-c++ ${rsFlags} $in &&
(echo '${out}: \' && cat ${depFiles} | awk 'start { sub(/( \\)?$$/, " \\"); print } /:/ { start=1 }') > ${out}.d &&
touch $out
`, "\n", "", -1)

var (
	rsCpp = pctx.AndroidStaticRule("rsCpp",
		blueprint.RuleParams{
			Command:     rsCppCmdLine,
			CommandDeps: []string{"$rsCmd"},
			Depfile:     "${out}.d",
			Deps:        blueprint.DepsGCC,
		},
		"depFiles", "outDir", "rsFlags", "stampFile")
)

// Takes a path to a .rs or .fs file, and returns a path to a generated ScriptC_*.cpp file
// This has to match the logic in llvm-rs-cc in DetermineOutputFile.
func rsGeneratedCppFile(ctx android.ModuleContext, rsFile android.Path) android.WritablePath {
	fileName := strings.TrimSuffix(rsFile.Base(), rsFile.Ext())
	return android.PathForModuleGen(ctx, "rs", "ScriptC_"+fileName+".cpp")
}

func rsGeneratedDepFile(ctx android.ModuleContext, rsFile android.Path) android.WritablePath {
	fileName := strings.TrimSuffix(rsFile.Base(), rsFile.Ext())
	return android.PathForModuleGen(ctx, "rs", fileName+".d")
}

func rsGenerateCpp(ctx android.ModuleContext, rsFiles android.Paths, rsFlags string) android.Paths {
	stampFile := android.PathForModuleGen(ctx, "rs", "rs.stamp")
	depFiles := make(android.WritablePaths, len(rsFiles))
	cppFiles := make(android.WritablePaths, len(rsFiles))
	for i, rsFile := range rsFiles {
		depFiles[i] = rsGeneratedDepFile(ctx, rsFile)
		cppFiles[i] = rsGeneratedCppFile(ctx, rsFile)
	}

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:            rsCpp,
		Description:     "llvm-rs-cc",
		Output:          stampFile,
		ImplicitOutputs: cppFiles,
		Inputs:          rsFiles,
		Args: map[string]string{
			"rsFlags":  rsFlags,
			"outDir":   android.PathForModuleGen(ctx, "rs").String(),
			"depFiles": strings.Join(depFiles.Strings(), " "),
		},
	})

	return android.Paths{stampFile}
}

func rsFlags(ctx ModuleContext, flags Flags, properties *BaseCompilerProperties) Flags {
	targetApi := proptools.String(properties.Renderscript.Target_api)
	if targetApi == "" && ctx.sdk() {
		switch ctx.sdkVersion() {
		case "current", "system_current", "test_current":
			// Nothing
		default:
			targetApi = ctx.sdkVersion()
		}
	}

	if targetApi != "" {
		flags.rsFlags = append(flags.rsFlags, "-target-api "+targetApi)
	}

	flags.rsFlags = append(flags.rsFlags, "-Wall", "-Werror")
	flags.rsFlags = append(flags.rsFlags, properties.Renderscript.Flags...)
	if ctx.Arch().ArchType.Multilib == "lib64" {
		flags.rsFlags = append(flags.rsFlags, "-m64")
	} else {
		flags.rsFlags = append(flags.rsFlags, "-m32")
	}
	flags.rsFlags = append(flags.rsFlags, "${config.RsGlobalIncludes}")

	rootRsIncludeDirs := android.PathsForSource(ctx, properties.Renderscript.Include_dirs)
	flags.rsFlags = append(flags.rsFlags, includeDirsToFlags(rootRsIncludeDirs))

	flags.GlobalFlags = append(flags.GlobalFlags,
		"-I"+android.PathForModuleGen(ctx, "rs").String(),
		"-Iframeworks/rs",
		"-Iframeworks/rs/cpp",
	)

	return flags
}
