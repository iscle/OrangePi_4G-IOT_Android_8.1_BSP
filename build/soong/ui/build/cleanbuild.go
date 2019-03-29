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
	"fmt"
	"io/ioutil"
	"os"
	"path/filepath"
	"strings"
)

func removeGlobs(ctx Context, globs ...string) {
	for _, glob := range globs {
		files, err := filepath.Glob(glob)
		if err != nil {
			// Only possible error is ErrBadPattern
			panic(fmt.Errorf("%q: %s", glob, err))
		}

		for _, file := range files {
			err = os.RemoveAll(file)
			if err != nil {
				ctx.Fatalf("Failed to remove file %q: %v", file, err)
			}
		}
	}
}

// Remove everything under the out directory. Don't remove the out directory
// itself in case it's a symlink.
func clean(ctx Context, config Config, what int) {
	removeGlobs(ctx, filepath.Join(config.OutDir(), "*"))
	ctx.Println("Entire build directory removed.")
}

func dataClean(ctx Context, config Config, what int) {
	removeGlobs(ctx, filepath.Join(config.ProductOut(), "data", "*"))
}

// installClean deletes all of the installed files -- the intent is to remove
// files that may no longer be installed, either because the user previously
// installed them, or they were previously installed by default but no longer
// are.
//
// This is faster than a full clean, since we're not deleting the
// intermediates.  Instead of recompiling, we can just copy the results.
func installClean(ctx Context, config Config, what int) {
	dataClean(ctx, config, what)

	if hostCrossOutPath := config.hostCrossOut(); hostCrossOutPath != "" {
		hostCrossOut := func(path string) string {
			return filepath.Join(hostCrossOutPath, path)
		}
		removeGlobs(ctx,
			hostCrossOut("bin"),
			hostCrossOut("coverage"),
			hostCrossOut("lib*"),
			hostCrossOut("nativetest*"))
	}

	hostOutPath := config.HostOut()
	hostOut := func(path string) string {
		return filepath.Join(hostOutPath, path)
	}

	productOutPath := config.ProductOut()
	productOut := func(path string) string {
		return filepath.Join(productOutPath, path)
	}

	// Host bin, frameworks, and lib* are intentionally omitted, since
	// otherwise we'd have to rebuild any generated files created with
	// those tools.
	removeGlobs(ctx,
		hostOut("obj/NOTICE_FILES"),
		hostOut("obj/PACKAGING"),
		hostOut("coverage"),
		hostOut("cts"),
		hostOut("nativetest*"),
		hostOut("sdk"),
		hostOut("sdk_addon"),
		hostOut("testcases"),
		hostOut("vts"),
		productOut("*.img"),
		productOut("*.zip"),
		productOut("android-info.txt"),
		productOut("kernel"),
		productOut("data"),
		productOut("skin"),
		productOut("obj/NOTICE_FILES"),
		productOut("obj/PACKAGING"),
		productOut("recovery"),
		productOut("root"),
		productOut("system"),
		productOut("system_other"),
		productOut("vendor"),
		productOut("oem"),
		productOut("obj/FAKE"),
		productOut("breakpad"),
		productOut("cache"),
		productOut("coverage"),
		productOut("installer"),
		productOut("odm"),
		productOut("sysloader"),
		productOut("testcases"))
}

// Since products and build variants (unfortunately) shared the same
// PRODUCT_OUT staging directory, things can get out of sync if different
// build configurations are built in the same tree. This function will
// notice when the configuration has changed and call installclean to
// remove the files necessary to keep things consistent.
func installCleanIfNecessary(ctx Context, config Config) {
	configFile := config.DevicePreviousProductConfig()
	prefix := "PREVIOUS_BUILD_CONFIG := "
	suffix := "\n"
	currentProduct := prefix + config.TargetProduct() + "-" + config.TargetBuildVariant() + suffix

	writeConfig := func() {
		err := ioutil.WriteFile(configFile, []byte(currentProduct), 0666)
		if err != nil {
			ctx.Fatalln("Failed to write product config:", err)
		}
	}

	prev, err := ioutil.ReadFile(configFile)
	if err != nil {
		if os.IsNotExist(err) {
			writeConfig()
			return
		} else {
			ctx.Fatalln("Failed to read previous product config:", err)
		}
	} else if string(prev) == currentProduct {
		return
	}

	if disable, _ := config.Environment().Get("DISABLE_AUTO_INSTALLCLEAN"); disable == "true" {
		ctx.Println("DISABLE_AUTO_INSTALLCLEAN is set; skipping auto-clean. Your tree may be in an inconsistent state.")
		return
	}

	ctx.BeginTrace("installclean")
	defer ctx.EndTrace()

	prevConfig := strings.TrimPrefix(strings.TrimSuffix(string(prev), suffix), prefix)
	currentConfig := strings.TrimPrefix(strings.TrimSuffix(currentProduct, suffix), prefix)

	ctx.Printf("Build configuration changed: %q -> %q, forcing installclean\n", prevConfig, currentConfig)

	installClean(ctx, config, 0)

	writeConfig()
}
