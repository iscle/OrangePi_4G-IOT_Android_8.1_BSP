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

package build

import (
	"io/ioutil"
	"os"
	"path/filepath"
	"text/template"
)

// Ensures the out directory exists, and has the proper files to prevent kati
// from recursing into it.
func SetupOutDir(ctx Context, config Config) {
	ensureEmptyFileExists(ctx, filepath.Join(config.OutDir(), "Android.mk"))
	ensureEmptyFileExists(ctx, filepath.Join(config.OutDir(), "CleanSpec.mk"))
	ensureEmptyFileExists(ctx, filepath.Join(config.SoongOutDir(), ".soong.in_make"))
	// The ninja_build file is used by our buildbots to understand that the output
	// can be parsed as ninja output.
	ensureEmptyFileExists(ctx, filepath.Join(config.OutDir(), "ninja_build"))
}

var combinedBuildNinjaTemplate = template.Must(template.New("combined").Parse(`
builddir = {{.OutDir}}
include {{.KatiNinjaFile}}
include {{.SoongNinjaFile}}
build {{.CombinedNinjaFile}}: phony {{.SoongNinjaFile}}
`))

func createCombinedBuildNinjaFile(ctx Context, config Config) {
	file, err := os.Create(config.CombinedNinjaFile())
	if err != nil {
		ctx.Fatalln("Failed to create combined ninja file:", err)
	}
	defer file.Close()

	if err := combinedBuildNinjaTemplate.Execute(file, config); err != nil {
		ctx.Fatalln("Failed to write combined ninja file:", err)
	}
}

const (
	BuildNone          = iota
	BuildProductConfig = 1 << iota
	BuildSoong         = 1 << iota
	BuildKati          = 1 << iota
	BuildNinja         = 1 << iota
	BuildAll           = BuildProductConfig | BuildSoong | BuildKati | BuildNinja
)

func checkCaseSensitivity(ctx Context, config Config) {
	outDir := config.OutDir()
	lowerCase := filepath.Join(outDir, "casecheck.txt")
	upperCase := filepath.Join(outDir, "CaseCheck.txt")
	lowerData := "a"
	upperData := "B"

	err := ioutil.WriteFile(lowerCase, []byte(lowerData), 0777)
	if err != nil {
		ctx.Fatalln("Failed to check case sensitivity:", err)
	}

	err = ioutil.WriteFile(upperCase, []byte(upperData), 0777)
	if err != nil {
		ctx.Fatalln("Failed to check case sensitivity:", err)
	}

	res, err := ioutil.ReadFile(lowerCase)
	if err != nil {
		ctx.Fatalln("Failed to check case sensitivity:", err)
	}

	if string(res) != lowerData {
		ctx.Println("************************************************************")
		ctx.Println("You are building on a case-insensitive filesystem.")
		ctx.Println("Please move your source tree to a case-sensitive filesystem.")
		ctx.Println("************************************************************")
		ctx.Fatalln("Case-insensitive filesystems not supported")
	}
}

func help(ctx Context, config Config, what int) {
	cmd := Command(ctx, config, "make",
		"make", "-f", "build/core/help.mk")
	cmd.Sandbox = makeSandbox
	cmd.Stdout = ctx.Stdout()
	cmd.Stderr = ctx.Stderr()
	cmd.RunOrFatal()
}

// Build the tree. The 'what' argument can be used to chose which components of
// the build to run.
func Build(ctx Context, config Config, what int) {
	ctx.Verboseln("Starting build with args:", config.Arguments())
	ctx.Verboseln("Environment:", config.Environment().Environ())

	if inList("help", config.Arguments()) {
		help(ctx, config, what)
		return
	} else if inList("clean", config.Arguments()) || inList("clobber", config.Arguments()) {
		clean(ctx, config, what)
		return
	}

	// Start getting java version as early as possible
	getJavaVersions(ctx, config)

	// Make sure that no other Soong process is running with the same output directory
	buildLock := BecomeSingletonOrFail(ctx, config)
	defer buildLock.Unlock()

	SetupOutDir(ctx, config)

	checkCaseSensitivity(ctx, config)

	ensureEmptyDirectoriesExist(ctx, config.TempDir())

	if what&BuildProductConfig != 0 {
		// Run make for product config
		runMakeProductConfig(ctx, config)
	}

	if inList("installclean", config.Arguments()) {
		installClean(ctx, config, what)
		ctx.Println("Deleted images and staging directories.")
		return
	} else if inList("dataclean", config.Arguments()) {
		dataClean(ctx, config, what)
		ctx.Println("Deleted data files.")
		return
	}

	if what&BuildSoong != 0 {
		// Run Soong
		runSoongBootstrap(ctx, config)
		runSoong(ctx, config)
	}

	// Check the java versions we read earlier
	checkJavaVersion(ctx, config)

	if what&BuildKati != 0 {
		// Run ckati
		runKati(ctx, config)
	}

	if what&BuildNinja != 0 {
		installCleanIfNecessary(ctx, config)

		// Write combined ninja file
		createCombinedBuildNinjaFile(ctx, config)

		// Run ninja
		runNinja(ctx, config)
	}
}
