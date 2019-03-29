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

// The platform needs to provide the following artifacts for the NDK:
// 1. Bionic headers.
// 2. Platform API headers.
// 3. NDK stub shared libraries.
// 4. Bionic static libraries.
//
// TODO(danalbert): All of the above need to include NOTICE files.
//
// Components 1 and 2: Headers
// The bionic and platform API headers are generalized into a single
// `ndk_headers` rule. This rule has a `from` property that indicates a base
// directory from which headers are to be taken, and a `to` property that
// indicates where in the sysroot they should reside relative to usr/include.
// There is also a `srcs` property that is glob compatible for specifying which
// headers to include.
//
// Component 3: Stub Libraries
// The shared libraries in the NDK are not the actual shared libraries they
// refer to (to prevent people from accidentally loading them), but stub
// libraries with dummy implementations of everything for use at build time
// only.
//
// Since we don't actually need to know anything about the stub libraries aside
// from a list of functions and globals to be exposed, we can create these for
// every platform level in the current tree. This is handled by the
// ndk_library rule.
//
// Component 4: Static Libraries
// The NDK only provides static libraries for bionic, not the platform APIs.
// Since these need to be the actual implementation, we can't build old versions
// in the current platform tree. As such, legacy versions are checked in
// prebuilt to development/ndk, and a current version is built and archived as
// part of the platform build. The platfrom already builds these libraries, our
// NDK build rules only need to archive them for retrieval so they can be added
// to the prebuilts.
//
// TODO(danalbert): Write `ndk_static_library` rule.

import (
	"github.com/google/blueprint"

	"android/soong/android"
)

func init() {
	android.RegisterModuleType("ndk_headers", ndkHeadersFactory)
	android.RegisterModuleType("ndk_library", ndkLibraryFactory)
	android.RegisterModuleType("preprocessed_ndk_headers", preprocessedNdkHeadersFactory)
	android.RegisterSingletonType("ndk", NdkSingleton)

	pctx.Import("android/soong/common")
}

func getNdkInstallBase(ctx android.PathContext) android.OutputPath {
	return android.PathForOutput(ctx, "ndk")
}

// Returns the main install directory for the NDK sysroot. Usable with --sysroot.
func getNdkSysrootBase(ctx android.PathContext) android.OutputPath {
	return getNdkInstallBase(ctx).Join(ctx, "sysroot")
}

func getNdkSysrootTimestampFile(ctx android.PathContext) android.Path {
	return android.PathForOutput(ctx, "ndk.timestamp")
}

func NdkSingleton() blueprint.Singleton {
	return &ndkSingleton{}
}

type ndkSingleton struct{}

func (n *ndkSingleton) GenerateBuildActions(ctx blueprint.SingletonContext) {
	installPaths := []string{}
	licensePaths := []string{}
	ctx.VisitAllModules(func(module blueprint.Module) {
		if m, ok := module.(android.Module); ok && !m.Enabled() {
			return
		}

		if m, ok := module.(*headerModule); ok {
			installPaths = append(installPaths, m.installPaths...)
			licensePaths = append(licensePaths, m.licensePath.String())
		}

		if m, ok := module.(*preprocessedHeaderModule); ok {
			installPaths = append(installPaths, m.installPaths...)
			licensePaths = append(licensePaths, m.licensePath.String())
		}

		if m, ok := module.(*Module); ok {
			if installer, ok := m.installer.(*stubDecorator); ok {
				installPaths = append(installPaths, installer.installPath)
			}
		}
	})

	combinedLicense := getNdkInstallBase(ctx).Join(ctx, "NOTICE")
	ctx.Build(pctx, blueprint.BuildParams{
		Rule:        android.Cat,
		Description: "combine licenses",
		Outputs:     []string{combinedLicense.String()},
		Inputs:      licensePaths,
		Optional:    true,
	})

	depPaths := append(installPaths, combinedLicense.String())

	// There's a dummy "ndk" rule defined in ndk/Android.mk that depends on
	// this. `m ndk` will build the sysroots.
	ctx.Build(pctx, blueprint.BuildParams{
		Rule:      android.Touch,
		Outputs:   []string{getNdkSysrootTimestampFile(ctx).String()},
		Implicits: depPaths,
		Optional:  true,
	})
}
