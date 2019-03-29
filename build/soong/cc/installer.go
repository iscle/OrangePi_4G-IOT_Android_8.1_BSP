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
	"path/filepath"

	"android/soong/android"
)

// This file handles installing files into their final location

type InstallerProperties struct {
	// install to a subdirectory of the default install path for the module
	Relative_install_path string `android:"arch_variant"`
}

type installLocation int

const (
	InstallInSystem       installLocation = 0
	InstallInData                         = iota
	InstallInSanitizerDir                 = iota
)

func NewBaseInstaller(dir, dir64 string, location installLocation) *baseInstaller {
	return &baseInstaller{
		dir:      dir,
		dir64:    dir64,
		location: location,
	}
}

type baseInstaller struct {
	Properties InstallerProperties

	dir      string
	dir64    string
	subDir   string
	relative string
	location installLocation

	path android.OutputPath
}

var _ installer = (*baseInstaller)(nil)

func (installer *baseInstaller) installerProps() []interface{} {
	return []interface{}{&installer.Properties}
}

func (installer *baseInstaller) installDir(ctx ModuleContext) android.OutputPath {
	dir := installer.dir
	if ctx.toolchain().Is64Bit() && installer.dir64 != "" {
		dir = installer.dir64
	}
	if !ctx.Host() && !ctx.Arch().Native {
		dir = filepath.Join(dir, ctx.Arch().ArchType.String())
	}
	if installer.location == InstallInData && ctx.vndk() {
		dir = filepath.Join(dir, "vendor")
	}
	return android.PathForModuleInstall(ctx, dir, installer.subDir, installer.Properties.Relative_install_path, installer.relative)
}

func (installer *baseInstaller) install(ctx ModuleContext, file android.Path) {
	installer.path = ctx.InstallFile(installer.installDir(ctx), file)
}

func (installer *baseInstaller) inData() bool {
	return installer.location == InstallInData
}

func (installer *baseInstaller) inSanitizerDir() bool {
	return installer.location == InstallInSanitizerDir
}

func (installer *baseInstaller) hostToolPath() android.OptionalPath {
	return android.OptionalPath{}
}
