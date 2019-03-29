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
	"fmt"
	"io"
	"path/filepath"
	"strings"
)

type subAndroidMkProvider interface {
	AndroidMk(*pythonBaseModule, *android.AndroidMkData)
}

func (p *pythonBaseModule) subAndroidMk(data *android.AndroidMkData, obj interface{}) {
	if p.subAndroidMkOnce == nil {
		p.subAndroidMkOnce = make(map[subAndroidMkProvider]bool)
	}
	if androidmk, ok := obj.(subAndroidMkProvider); ok {
		if !p.subAndroidMkOnce[androidmk] {
			p.subAndroidMkOnce[androidmk] = true
			androidmk.AndroidMk(p, data)
		}
	}
}

func (p *pythonBaseModule) AndroidMk() (ret android.AndroidMkData, err error) {
	p.subAndroidMk(&ret, p.installer)

	return ret, nil
}

func (p *pythonBinaryHostDecorator) AndroidMk(base *pythonBaseModule, ret *android.AndroidMkData) {
	ret.Class = "EXECUTABLES"
	base.subAndroidMk(ret, p.pythonDecorator.baseInstaller)
}

func (p *pythonTestHostDecorator) AndroidMk(base *pythonBaseModule, ret *android.AndroidMkData) {
	ret.Class = "NATIVE_TESTS"
	base.subAndroidMk(ret, p.pythonDecorator.baseInstaller)
}

func (installer *pythonInstaller) AndroidMk(base *pythonBaseModule, ret *android.AndroidMkData) {
	// Soong installation is only supported for host modules. Have Make
	// installation trigger Soong installation.
	if base.Target().Os.Class == android.Host {
		ret.OutputFile = android.OptionalPathForPath(installer.path)
	}

	ret.Extra = append(ret.Extra, func(w io.Writer, outputFile android.Path) error {
		path := installer.path.RelPathString()
		dir, file := filepath.Split(path)
		stem := strings.TrimSuffix(file, filepath.Ext(file))

		fmt.Fprintln(w, "LOCAL_MODULE_SUFFIX := "+filepath.Ext(file))
		fmt.Fprintln(w, "LOCAL_MODULE_PATH := $(OUT_DIR)/"+filepath.Clean(dir))
		fmt.Fprintln(w, "LOCAL_MODULE_STEM := "+stem)
		return nil
	})
}
