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

import (
	"path/filepath"

	"github.com/google/blueprint/bootstrap"

	"android/soong/android"
)

var resourceExcludes = []string{
	"**/*.java",
	"**/package.html",
	"**/overview.html",
	"**/.*.swp",
	"**/.DS_Store",
	"**/*~",
}

func isStringInSlice(str string, slice []string) bool {
	for _, s := range slice {
		if s == str {
			return true
		}
	}
	return false
}

func ResourceDirsToJarSpecs(ctx android.ModuleContext, resourceDirs, excludeDirs []string) []jarSpec {
	var excludes []string

	for _, exclude := range excludeDirs {
		excludes = append(excludes, android.PathForModuleSrc(ctx, exclude, "**/*").String())
	}

	excludes = append(excludes, resourceExcludes...)

	var jarSpecs []jarSpec

	for _, resourceDir := range resourceDirs {
		if isStringInSlice(resourceDir, excludeDirs) {
			continue
		}
		resourceDir := android.PathForModuleSrc(ctx, resourceDir)
		dirs := ctx.Glob(resourceDir.String(), nil)
		for _, dir := range dirs {
			fileListFile := android.ResPathWithName(ctx, dir, "resources.list")
			depFile := fileListFile.String() + ".d"

			pattern := filepath.Join(dir.String(), "**/*")
			bootstrap.GlobFile(ctx, pattern, excludes, fileListFile.String(), depFile)
			jarSpecs = append(jarSpecs, jarSpec{fileListFile, dir})
		}
	}

	return jarSpecs
}
