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

package java

// This file generates the final rules for compiling all C/C++.  All properties related to
// compiling should have been translated into builderFlags or another argument to the Transform*
// functions.

import (
	"github.com/google/blueprint"

	"android/soong/android"
)

func init() {
	pctx.HostBinToolVariable("aidlCmd", "aidl")
	pctx.SourcePathVariable("logtagsCmd", "build/tools/java-event-log-tags.py")
	pctx.SourcePathVariable("mergeLogtagsCmd", "build/tools/merge-event-log-tags.py")

	pctx.IntermediatesPathVariable("allLogtagsFile", "all-event-log-tags.txt")
}

var (
	aidl = pctx.AndroidStaticRule("aidl",
		blueprint.RuleParams{
			Command:     "$aidlCmd -d$depFile $aidlFlags $in $out",
			CommandDeps: []string{"$aidlCmd"},
		},
		"depFile", "aidlFlags")

	logtags = pctx.AndroidStaticRule("logtags",
		blueprint.RuleParams{
			Command:     "$logtagsCmd -o $out $in $allLogtagsFile",
			CommandDeps: []string{"$logtagsCmd"},
		})

	mergeLogtags = pctx.AndroidStaticRule("mergeLogtags",
		blueprint.RuleParams{
			Command:     "$mergeLogtagsCmd -o $out $in",
			CommandDeps: []string{"$mergeLogtagsCmd"},
		})
)

func genAidl(ctx android.ModuleContext, aidlFile android.Path, aidlFlags string) android.Path {
	javaFile := android.GenPathWithExt(ctx, "aidl", aidlFile, "java")
	depFile := javaFile.String() + ".d"

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        aidl,
		Description: "aidl " + aidlFile.Rel(),
		Output:      javaFile,
		Input:       aidlFile,
		Args: map[string]string{
			"depFile":   depFile,
			"aidlFlags": aidlFlags,
		},
	})

	return javaFile
}

func genLogtags(ctx android.ModuleContext, logtagsFile android.Path) android.Path {
	javaFile := android.GenPathWithExt(ctx, "logtags", logtagsFile, "java")

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        logtags,
		Description: "logtags " + logtagsFile.Rel(),
		Output:      javaFile,
		Input:       logtagsFile,
	})

	return javaFile
}

func (j *Module) genSources(ctx android.ModuleContext, srcFiles android.Paths,
	flags javaBuilderFlags) android.Paths {

	for i, srcFile := range srcFiles {
		switch srcFile.Ext() {
		case ".aidl":
			javaFile := genAidl(ctx, srcFile, flags.aidlFlags)
			srcFiles[i] = javaFile
		case ".logtags":
			j.logtagsSrcs = append(j.logtagsSrcs, srcFile)
			javaFile := genLogtags(ctx, srcFile)
			srcFiles[i] = javaFile
		}
	}

	return srcFiles
}

func LogtagsSingleton() blueprint.Singleton {
	return &logtagsSingleton{}
}

type logtagsProducer interface {
	logtags() android.Paths
}

type logtagsSingleton struct{}

func (l *logtagsSingleton) GenerateBuildActions(ctx blueprint.SingletonContext) {
	var allLogtags android.Paths
	ctx.VisitAllModules(func(module blueprint.Module) {
		if logtags, ok := module.(logtagsProducer); ok {
			allLogtags = append(allLogtags, logtags.logtags()...)
		}
	})

	ctx.Build(pctx, blueprint.BuildParams{
		Rule:        mergeLogtags,
		Description: "merge logtags",
		Outputs:     []string{"$allLogtagsFile"},
		Inputs:      allLogtags.Strings(),
	})
}
