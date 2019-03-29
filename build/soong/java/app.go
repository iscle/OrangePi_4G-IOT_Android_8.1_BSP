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

// This file contains the module types for compiling Android apps.

import (
	"path/filepath"
	"strings"

	"github.com/google/blueprint"

	"android/soong/android"
)

// AAR prebuilts
// AndroidManifest.xml merging
// package splits

type androidAppProperties struct {
	// path to a certificate, or the name of a certificate in the default
	// certificate directory, or blank to use the default product certificate
	Certificate string

	// paths to extra certificates to sign the apk with
	Additional_certificates []string

	// If set, create package-export.apk, which other packages can
	// use to get PRODUCT-agnostic resource data like IDs and type definitions.
	Export_package_resources bool

	// flags passed to aapt when creating the apk
	Aaptflags []string

	// list of resource labels to generate individual resource packages
	Package_splits []string

	// list of directories relative to the Blueprints file containing assets.
	// Defaults to "assets"
	Asset_dirs []string

	// list of directories relative to the Blueprints file containing
	// Java resources
	Android_resource_dirs []string
}

type AndroidApp struct {
	Module

	appProperties androidAppProperties

	aaptJavaFileList android.Path
	exportPackage    android.Path
}

func (a *AndroidApp) DepsMutator(ctx android.BottomUpMutatorContext) {
	a.Module.deps(ctx)

	if !a.properties.No_standard_libraries {
		switch a.deviceProperties.Sdk_version { // TODO: Res_sdk_version?
		case "current", "system_current", "":
			ctx.AddDependency(ctx.Module(), frameworkResTag, "framework-res")
		default:
			// We'll already have a dependency on an sdk prebuilt android.jar
		}
	}
}

func (a *AndroidApp) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	aaptFlags, aaptDeps, hasResources := a.aaptFlags(ctx)

	if hasResources {
		// First generate R.java so we can build the .class files
		aaptRJavaFlags := append([]string(nil), aaptFlags...)

		publicResourcesFile, proguardOptionsFile, aaptJavaFileList :=
			CreateResourceJavaFiles(ctx, aaptRJavaFlags, aaptDeps)
		a.aaptJavaFileList = aaptJavaFileList
		a.ExtraSrcLists = append(a.ExtraSrcLists, aaptJavaFileList)

		if a.appProperties.Export_package_resources {
			aaptPackageFlags := append([]string(nil), aaptFlags...)
			var hasProduct bool
			for _, f := range aaptPackageFlags {
				if strings.HasPrefix(f, "--product") {
					hasProduct = true
					break
				}
			}

			if !hasProduct {
				aaptPackageFlags = append(aaptPackageFlags,
					"--product "+ctx.AConfig().ProductAaptCharacteristics())
			}
			a.exportPackage = CreateExportPackage(ctx, aaptPackageFlags, aaptDeps)
			ctx.CheckbuildFile(a.exportPackage)
		}
		ctx.CheckbuildFile(publicResourcesFile)
		ctx.CheckbuildFile(proguardOptionsFile)
		ctx.CheckbuildFile(aaptJavaFileList)
	}

	// apps manifests are handled by aapt, don't let Module see them
	a.properties.Manifest = nil

	//if !ctx.ContainsProperty("proguard.enabled") {
	//	a.properties.Proguard.Enabled = true
	//}

	a.Module.compile(ctx)

	aaptPackageFlags := append([]string(nil), aaptFlags...)
	var hasProduct bool
	for _, f := range aaptPackageFlags {
		if strings.HasPrefix(f, "--product") {
			hasProduct = true
			break
		}
	}

	if !hasProduct {
		aaptPackageFlags = append(aaptPackageFlags,
			"--product "+ctx.AConfig().ProductAaptCharacteristics())
	}

	certificate := a.appProperties.Certificate
	if certificate == "" {
		certificate = ctx.AConfig().DefaultAppCertificate(ctx).String()
	} else if dir, _ := filepath.Split(certificate); dir == "" {
		certificate = filepath.Join(ctx.AConfig().DefaultAppCertificateDir(ctx).String(), certificate)
	} else {
		certificate = filepath.Join(android.PathForSource(ctx).String(), certificate)
	}

	certificates := []string{certificate}
	for _, c := range a.appProperties.Additional_certificates {
		certificates = append(certificates, filepath.Join(android.PathForSource(ctx).String(), c))
	}

	a.outputFile = CreateAppPackage(ctx, aaptPackageFlags, a.outputFile, certificates)
	ctx.InstallFileName(android.PathForModuleInstall(ctx, "app"), ctx.ModuleName()+".apk", a.outputFile)
}

var aaptIgnoreFilenames = []string{
	".svn",
	".git",
	".ds_store",
	"*.scc",
	".*",
	"CVS",
	"thumbs.db",
	"picasa.ini",
	"*~",
}

func (a *AndroidApp) aaptFlags(ctx android.ModuleContext) ([]string, android.Paths, bool) {
	aaptFlags := a.appProperties.Aaptflags
	hasVersionCode := false
	hasVersionName := false
	for _, f := range aaptFlags {
		if strings.HasPrefix(f, "--version-code") {
			hasVersionCode = true
		} else if strings.HasPrefix(f, "--version-name") {
			hasVersionName = true
		}
	}

	if true /* is not a test */ {
		aaptFlags = append(aaptFlags, "-z")
	}

	assetDirs := android.PathsWithOptionalDefaultForModuleSrc(ctx, a.appProperties.Asset_dirs, "assets")
	resourceDirs := android.PathsWithOptionalDefaultForModuleSrc(ctx, a.appProperties.Android_resource_dirs, "res")

	var overlayResourceDirs android.Paths
	// For every resource directory, check if there is an overlay directory with the same path.
	// If found, it will be prepended to the list of resource directories.
	for _, overlayDir := range ctx.AConfig().ResourceOverlays() {
		for _, resourceDir := range resourceDirs {
			overlay := overlayDir.OverlayPath(ctx, resourceDir)
			if overlay.Valid() {
				overlayResourceDirs = append(overlayResourceDirs, overlay.Path())
			}
		}
	}

	if len(overlayResourceDirs) > 0 {
		resourceDirs = append(overlayResourceDirs, resourceDirs...)
	}

	// aapt needs to rerun if any files are added or modified in the assets or resource directories,
	// use glob to create a filelist.
	var aaptDeps android.Paths
	var hasResources bool
	for _, d := range resourceDirs {
		newDeps := ctx.Glob(filepath.Join(d.String(), "**/*"), aaptIgnoreFilenames)
		aaptDeps = append(aaptDeps, newDeps...)
		if len(newDeps) > 0 {
			hasResources = true
		}
	}
	for _, d := range assetDirs {
		newDeps := ctx.Glob(filepath.Join(d.String(), "**/*"), aaptIgnoreFilenames)
		aaptDeps = append(aaptDeps, newDeps...)
	}

	var manifestFile string
	if a.properties.Manifest == nil {
		manifestFile = "AndroidManifest.xml"
	} else {
		manifestFile = *a.properties.Manifest
	}

	manifestPath := android.PathForModuleSrc(ctx, manifestFile)
	aaptDeps = append(aaptDeps, manifestPath)

	aaptFlags = append(aaptFlags, "-M "+manifestPath.String())
	aaptFlags = append(aaptFlags, android.JoinWithPrefix(assetDirs.Strings(), "-A "))
	aaptFlags = append(aaptFlags, android.JoinWithPrefix(resourceDirs.Strings(), "-S "))

	ctx.VisitDirectDeps(func(module blueprint.Module) {
		var depFile android.OptionalPath
		if sdkDep, ok := module.(sdkDependency); ok {
			depFile = android.OptionalPathForPath(sdkDep.ClasspathFile())
		} else if javaDep, ok := module.(JavaDependency); ok {
			if ctx.OtherModuleName(module) == "framework-res" {
				depFile = android.OptionalPathForPath(javaDep.(*AndroidApp).exportPackage)
			}
		}
		if depFile.Valid() {
			aaptFlags = append(aaptFlags, "-I "+depFile.String())
			aaptDeps = append(aaptDeps, depFile.Path())
		}
	})

	sdkVersion := a.deviceProperties.Sdk_version
	if sdkVersion == "" {
		sdkVersion = ctx.AConfig().PlatformSdkVersion()
	}

	aaptFlags = append(aaptFlags, "--min-sdk-version "+sdkVersion)
	aaptFlags = append(aaptFlags, "--target-sdk-version "+sdkVersion)

	if !hasVersionCode {
		aaptFlags = append(aaptFlags, "--version-code "+ctx.AConfig().PlatformSdkVersion())
	}

	if !hasVersionName {
		aaptFlags = append(aaptFlags,
			"--version-name "+ctx.AConfig().PlatformVersion()+"-"+ctx.AConfig().BuildNumber())
	}

	// TODO: LOCAL_PACKAGE_OVERRIDES
	//    $(addprefix --rename-manifest-package , $(PRIVATE_MANIFEST_PACKAGE_NAME)) \

	// TODO: LOCAL_INSTRUMENTATION_FOR
	//    $(addprefix --rename-instrumentation-target-package , $(PRIVATE_MANIFEST_INSTRUMENTATION_FOR))

	return aaptFlags, aaptDeps, hasResources
}

func AndroidAppFactory() android.Module {
	module := &AndroidApp{}

	module.deviceProperties.Dex = true

	module.AddProperties(
		&module.Module.properties,
		&module.Module.deviceProperties,
		&module.appProperties)

	android.InitAndroidArchModule(module, android.DeviceSupported, android.MultilibCommon)
	return module
}
