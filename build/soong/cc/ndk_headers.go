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
	"os"
	"path/filepath"

	"github.com/google/blueprint"

	"android/soong/android"
)

var (
	preprocessBionicHeaders = pctx.AndroidStaticRule("preprocessBionicHeaders",
		blueprint.RuleParams{
			// The `&& touch $out` isn't really necessary, but Blueprint won't
			// let us have only implicit outputs.
			Command:     "$versionerCmd -o $outDir $srcDir $depsPath && touch $out",
			CommandDeps: []string{"$versionerCmd"},
		},
		"depsPath", "srcDir", "outDir")
)

func init() {
	pctx.HostBinToolVariable("versionerCmd", "versioner")
}

// Returns the NDK base include path for use with sdk_version current. Usable with -I.
func getCurrentIncludePath(ctx android.ModuleContext) android.OutputPath {
	return getNdkSysrootBase(ctx).Join(ctx, "usr/include")
}

type headerProperies struct {
	// Base directory of the headers being installed. As an example:
	//
	// ndk_headers {
	//     name: "foo",
	//     from: "include",
	//     to: "",
	//     srcs: ["include/foo/bar/baz.h"],
	// }
	//
	// Will install $SYSROOT/usr/include/foo/bar/baz.h. If `from` were instead
	// "include/foo", it would have installed $SYSROOT/usr/include/bar/baz.h.
	From string

	// Install path within the sysroot. This is relative to usr/include.
	To string

	// List of headers to install. Glob compatible. Common case is "include/**/*.h".
	Srcs []string

	// Path to the NOTICE file associated with the headers.
	License string
}

type headerModule struct {
	android.ModuleBase

	properties headerProperies

	installPaths []string
	licensePath  android.ModuleSrcPath
}

func (m *headerModule) DepsMutator(ctx android.BottomUpMutatorContext) {
}

func getHeaderInstallDir(ctx android.ModuleContext, header android.Path, from string,
	to string) android.OutputPath {
	// Output path is the sysroot base + "usr/include" + to directory + directory component
	// of the file without the leading from directory stripped.
	//
	// Given:
	// sysroot base = "ndk/sysroot"
	// from = "include/foo"
	// to = "bar"
	// header = "include/foo/woodly/doodly.h"
	// output path = "ndk/sysroot/usr/include/bar/woodly/doodly.h"

	// full/platform/path/to/include/foo
	fullFromPath := android.PathForModuleSrc(ctx, from)

	// full/platform/path/to/include/foo/woodly
	headerDir := filepath.Dir(header.String())

	// woodly
	strippedHeaderDir, err := filepath.Rel(fullFromPath.String(), headerDir)
	if err != nil {
		ctx.ModuleErrorf("filepath.Rel(%q, %q) failed: %s", headerDir,
			fullFromPath.String(), err)
	}

	// full/platform/path/to/sysroot/usr/include/bar/woodly
	installDir := getCurrentIncludePath(ctx).Join(ctx, to, strippedHeaderDir)

	// full/platform/path/to/sysroot/usr/include/bar/woodly/doodly.h
	return installDir
}

func (m *headerModule) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	if m.properties.License == "" {
		ctx.PropertyErrorf("license", "field is required")
	}

	m.licensePath = android.PathForModuleSrc(ctx, m.properties.License)

	srcFiles := ctx.ExpandSources(m.properties.Srcs, nil)
	for _, header := range srcFiles {
		installDir := getHeaderInstallDir(ctx, header, m.properties.From, m.properties.To)
		installedPath := ctx.InstallFile(installDir, header)
		installPath := installDir.Join(ctx, header.Base())
		if installPath != installedPath {
			panic(fmt.Sprintf(
				"expected header install path (%q) not equal to actual install path %q",
				installPath, installedPath))
		}
		m.installPaths = append(m.installPaths, installPath.String())
	}

	if len(m.installPaths) == 0 {
		ctx.ModuleErrorf("srcs %q matched zero files", m.properties.Srcs)
	}
}

func ndkHeadersFactory() android.Module {
	module := &headerModule{}
	module.AddProperties(&module.properties)
	android.InitAndroidModule(module)
	return module
}

type preprocessedHeaderProperies struct {
	// Base directory of the headers being installed. As an example:
	//
	// preprocessed_ndk_headers {
	//     name: "foo",
	//     from: "include",
	//     to: "",
	// }
	//
	// Will install $SYSROOT/usr/include/foo/bar/baz.h. If `from` were instead
	// "include/foo", it would have installed $SYSROOT/usr/include/bar/baz.h.
	From string

	// Install path within the sysroot. This is relative to usr/include.
	To string

	// Path to the NOTICE file associated with the headers.
	License string
}

// Like ndk_headers, but preprocesses the headers with the bionic versioner:
// https://android.googlesource.com/platform/bionic/+/master/tools/versioner/README.md.
//
// Unlike ndk_headers, we don't operate on a list of sources but rather a whole directory, the
// module does not have the srcs property, and operates on a full directory (the `from` property).
//
// Note that this is really only built to handle bionic/libc/include.
type preprocessedHeaderModule struct {
	android.ModuleBase

	properties preprocessedHeaderProperies

	installPaths []string
	licensePath  android.ModuleSrcPath
}

func (m *preprocessedHeaderModule) DepsMutator(ctx android.BottomUpMutatorContext) {
}

func (m *preprocessedHeaderModule) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	if m.properties.License == "" {
		ctx.PropertyErrorf("license", "field is required")
	}

	m.licensePath = android.PathForModuleSrc(ctx, m.properties.License)

	fromSrcPath := android.PathForModuleSrc(ctx, m.properties.From)
	toOutputPath := getCurrentIncludePath(ctx).Join(ctx, m.properties.To)
	srcFiles := ctx.Glob(filepath.Join(fromSrcPath.String(), "**/*.h"), nil)
	var installPaths []android.WritablePath
	for _, header := range srcFiles {
		installDir := getHeaderInstallDir(ctx, header, m.properties.From, m.properties.To)
		installPath := installDir.Join(ctx, header.Base())
		installPaths = append(installPaths, installPath)
		m.installPaths = append(m.installPaths, installPath.String())
	}

	if len(m.installPaths) == 0 {
		ctx.ModuleErrorf("glob %q matched zero files", m.properties.From)
	}

	processHeadersWithVersioner(ctx, fromSrcPath, toOutputPath, srcFiles, installPaths)
}

func processHeadersWithVersioner(ctx android.ModuleContext, srcDir, outDir android.Path, srcFiles android.Paths, installPaths []android.WritablePath) android.Path {
	// The versioner depends on a dependencies directory to simplify determining include paths
	// when parsing headers. This directory contains architecture specific directories as well
	// as a common directory, each of which contains symlinks to the actually directories to
	// be included.
	//
	// ctx.Glob doesn't follow symlinks, so we need to do this ourselves so we correctly
	// depend on these headers.
	// TODO(http://b/35673191): Update the versioner to use a --sysroot.
	depsPath := android.PathForSource(ctx, "bionic/libc/versioner-dependencies")
	depsGlob := ctx.Glob(filepath.Join(depsPath.String(), "**/*"), nil)
	for i, path := range depsGlob {
		fileInfo, err := os.Lstat(path.String())
		if err != nil {
			ctx.ModuleErrorf("os.Lstat(%q) failed: %s", path.String, err)
		}
		if fileInfo.Mode()&os.ModeSymlink == os.ModeSymlink {
			dest, err := os.Readlink(path.String())
			if err != nil {
				ctx.ModuleErrorf("os.Readlink(%q) failed: %s",
					path.String, err)
			}
			// Additional .. to account for the symlink itself.
			depsGlob[i] = android.PathForSource(
				ctx, filepath.Clean(filepath.Join(path.String(), "..", dest)))
		}
	}

	timestampFile := android.PathForModuleOut(ctx, "versioner.timestamp")
	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:            preprocessBionicHeaders,
		Description:     "versioner preprocess " + srcDir.Rel(),
		Output:          timestampFile,
		Implicits:       append(srcFiles, depsGlob...),
		ImplicitOutputs: installPaths,
		Args: map[string]string{
			"depsPath": depsPath.String(),
			"srcDir":   srcDir.String(),
			"outDir":   outDir.String(),
		},
	})

	return timestampFile
}

func preprocessedNdkHeadersFactory() android.Module {
	module := &preprocessedHeaderModule{}

	module.AddProperties(&module.properties)

	// Host module rather than device module because device module install steps
	// do not get run when embedded in make. We're not any of the existing
	// module types that can be exposed via the Android.mk exporter, so just use
	// a host module.
	android.InitAndroidArchModule(module, android.HostSupportedNoCross, android.MultilibFirst)

	return module
}
