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

package python

// This file contains Ninja build actions for building Python program.

import (
	"strings"

	"android/soong/android"

	"github.com/google/blueprint"
	_ "github.com/google/blueprint/bootstrap"
)

var (
	pctx = android.NewPackageContext("android/soong/python")

	par = pctx.AndroidStaticRule("par",
		blueprint.RuleParams{
			Command: `touch $initFile && ` +
				`sed -e 's/%interpreter%/$interp/g' -e 's/%main%/$main/g' $template > $stub && ` +
				`$parCmd -o $parFile $parArgs && echo '#!/usr/bin/env python' | cat - $parFile > $out && ` +
				`chmod +x $out && (rm -f $initFile; rm -f $stub; rm -f $parFile)`,
			CommandDeps: []string{"$parCmd", "$template"},
		},
		"initFile", "interp", "main", "template", "stub", "parCmd", "parFile", "parArgs")
)

func init() {
	pctx.Import("github.com/google/blueprint/bootstrap")
	pctx.Import("android/soong/common")

	pctx.HostBinToolVariable("parCmd", "soong_zip")
}

type fileListSpec struct {
	fileList     android.Path
	relativeRoot string
}

type parSpec struct {
	rootPrefix string

	fileListSpecs []fileListSpec
}

func (p parSpec) soongParArgs() string {
	ret := "-P " + p.rootPrefix

	for _, spec := range p.fileListSpecs {
		ret += " -C " + spec.relativeRoot + " -l " + spec.fileList.String()
	}

	return ret
}

func registerBuildActionForModuleFileList(ctx android.ModuleContext,
	name string, files android.Paths) android.Path {
	fileList := android.PathForModuleOut(ctx, name+".list")

	content := []string{}
	for _, file := range files {
		content = append(content, file.String())
	}

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        android.WriteFile,
		Description: "generate " + fileList.Rel(),
		Output:      fileList,
		Implicits:   files,
		Args: map[string]string{
			"content": strings.Join(content, `\n`),
		},
	})

	return fileList
}

func registerBuildActionForParFile(ctx android.ModuleContext,
	interpreter, main, binName string, newPyPkgs []string, parSpecs []parSpec) android.Path {

	// intermediate output path for __init__.py
	initFile := android.PathForModuleOut(ctx, initFileName).String()

	// the path of stub_template_host.txt from source tree.
	template := android.PathForSource(ctx, stubTemplateHost)

	// intermediate output path for __main__.py
	stub := android.PathForModuleOut(ctx, mainFileName).String()

	// intermediate output path for par file.
	parFile := android.PathForModuleOut(ctx, binName+parFileExt)

	// intermediate output path for bin executable.
	binFile := android.PathForModuleOut(ctx, binName)

	// implicit dependency for parFile build action.
	implicits := android.Paths{}
	for _, p := range parSpecs {
		for _, f := range p.fileListSpecs {
			implicits = append(implicits, f.fileList)
		}
	}

	parArgs := []string{}
	parArgs = append(parArgs, "-C "+strings.TrimSuffix(stub, mainFileName)+" -f "+stub)
	parArgs = append(parArgs, "-C "+strings.TrimSuffix(initFile, initFileName)+" -f "+initFile)
	for _, pkg := range newPyPkgs {
		parArgs = append(parArgs, "-P "+pkg+" -f "+initFile)
	}
	for _, p := range parSpecs {
		parArgs = append(parArgs, p.soongParArgs())
	}

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        par,
		Description: "python archive",
		Output:      binFile,
		Implicits:   implicits,
		Args: map[string]string{
			"initFile": initFile,
			// the "\" isn't being interpreted by regex parser, it's being
			// interpreted in the string literal.
			"interp":   strings.Replace(interpreter, "/", `\/`, -1),
			"main":     strings.Replace(main, "/", `\/`, -1),
			"template": template.String(),
			"stub":     stub,
			"parFile":  parFile.String(),
			"parArgs":  strings.Join(parArgs, " "),
		},
	})

	return binFile
}
