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

import (
	"android/soong/android"
	"path/filepath"
)

// This file contains the module types for building Python test.

func init() {
	android.RegisterModuleType("python_test_host", PythonTestHostFactory)
}

type PythonTestHost struct {
	pythonBinaryBase
}

var _ PythonSubModule = (*PythonTestHost)(nil)

type pythonTestHostDecorator struct {
	pythonDecorator
}

func (p *pythonTestHostDecorator) install(ctx android.ModuleContext, file android.Path) {
	p.pythonDecorator.baseInstaller.dir = filepath.Join("nativetest", ctx.ModuleName())
	p.pythonDecorator.baseInstaller.install(ctx, file)
}

func PythonTestHostFactory() android.Module {
	decorator := &pythonTestHostDecorator{
		pythonDecorator: pythonDecorator{baseInstaller: NewPythonInstaller("nativetest")}}

	module := &PythonBinaryHost{}
	module.pythonBaseModule.installer = decorator

	module.AddProperties(&module.binaryProperties)

	return InitPythonBaseModule(&module.pythonBinaryBase.pythonBaseModule,
		&module.pythonBinaryBase, android.HostSupportedNoCross)
}
