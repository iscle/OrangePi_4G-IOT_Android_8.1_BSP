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
	"path/filepath"
	"strings"
)

// DumpMakeVars can be used to extract the values of Make variables after the
// product configurations are loaded. This is roughly equivalent to the
// `get_build_var` bash function.
//
// goals can be used to set MAKECMDGOALS, which emulates passing arguments to
// Make without actually building them. So all the variables based on
// MAKECMDGOALS can be read.
//
// extra_targets adds real arguments to the make command, in case other targets
// actually need to be run (like the Soong config generator).
//
// vars is the list of variables to read. The values will be put in the
// returned map.
func DumpMakeVars(ctx Context, config Config, goals, extra_targets, vars []string) (map[string]string, error) {
	ctx.BeginTrace("dumpvars")
	defer ctx.EndTrace()

	cmd := Command(ctx, config, "make",
		"make",
		"--no-print-directory",
		"-f", "build/core/config.mk",
		"dump-many-vars",
		"CALLED_FROM_SETUP=true",
		"BUILD_SYSTEM=build/core",
		"MAKECMDGOALS="+strings.Join(goals, " "),
		"DUMP_MANY_VARS="+strings.Join(vars, " "),
		"OUT_DIR="+config.OutDir())
	cmd.Args = append(cmd.Args, extra_targets...)
	cmd.Sandbox = makeSandbox
	// TODO: error out when Stderr contains any content
	cmd.Stderr = ctx.Stderr()
	output, err := cmd.Output()
	if err != nil {
		return nil, err
	}

	ret := make(map[string]string, len(vars))
	for _, line := range strings.Split(string(output), "\n") {
		if len(line) == 0 {
			continue
		}

		if key, value, ok := decodeKeyValue(line); ok {
			if value, ok = singleUnquote(value); ok {
				ret[key] = value
				ctx.Verboseln(key, value)
			} else {
				return nil, fmt.Errorf("Failed to parse make line: %q", line)
			}
		} else {
			return nil, fmt.Errorf("Failed to parse make line: %q", line)
		}
	}

	return ret, nil
}

func runMakeProductConfig(ctx Context, config Config) {
	// Variables to export into the environment of Kati/Ninja
	exportEnvVars := []string{
		// So that we can use the correct TARGET_PRODUCT if it's been
		// modified by PRODUCT-*/APP-* arguments
		"TARGET_PRODUCT",
		"TARGET_BUILD_VARIANT",
		"TARGET_BUILD_APPS",

		// compiler wrappers set up by make
		"CC_WRAPPER",
		"CXX_WRAPPER",
		"JAVAC_WRAPPER",

		// ccache settings
		"CCACHE_COMPILERCHECK",
		"CCACHE_SLOPPINESS",
		"CCACHE_BASEDIR",
		"CCACHE_CPP2",
	}

	// Variables to print out in the top banner
	bannerVars := []string{
		"PLATFORM_VERSION_CODENAME",
		"PLATFORM_VERSION",
		"TARGET_PRODUCT",
		"TARGET_BUILD_VARIANT",
		"TARGET_BUILD_TYPE",
		"TARGET_BUILD_APPS",
		"TARGET_ARCH",
		"TARGET_ARCH_VARIANT",
		"TARGET_CPU_VARIANT",
		"TARGET_2ND_ARCH",
		"TARGET_2ND_ARCH_VARIANT",
		"TARGET_2ND_CPU_VARIANT",
		"HOST_ARCH",
		"HOST_2ND_ARCH",
		"HOST_OS",
		"HOST_OS_EXTRA",
		"HOST_CROSS_OS",
		"HOST_CROSS_ARCH",
		"HOST_CROSS_2ND_ARCH",
		"HOST_BUILD_TYPE",
		"BUILD_ID",
		"OUT_DIR",
		"AUX_OS_VARIANT_LIST",
		"TARGET_BUILD_PDK",
		"PDK_FUSION_PLATFORM_ZIP",
	}

	allVars := append(append([]string{
		// Used to execute Kati and Ninja
		"NINJA_GOALS",
		"KATI_GOALS",

		// To find target/product/<DEVICE>
		"TARGET_DEVICE",
	}, exportEnvVars...), bannerVars...)

	make_vars, err := DumpMakeVars(ctx, config, config.Arguments(), []string{
		filepath.Join(config.SoongOutDir(), "soong.variables"),
	}, allVars)
	if err != nil {
		ctx.Fatalln("Error dumping make vars:", err)
	}

	// Print the banner like make does
	fmt.Fprintln(ctx.Stdout(), "============================================")
	for _, name := range bannerVars {
		if make_vars[name] != "" {
			fmt.Fprintf(ctx.Stdout(), "%s=%s\n", name, make_vars[name])
		}
	}
	fmt.Fprintln(ctx.Stdout(), "============================================")

	// Populate the environment
	env := config.Environment()
	for _, name := range exportEnvVars {
		if make_vars[name] == "" {
			env.Unset(name)
		} else {
			env.Set(name, make_vars[name])
		}
	}

	config.SetKatiArgs(strings.Fields(make_vars["KATI_GOALS"]))
	config.SetNinjaArgs(strings.Fields(make_vars["NINJA_GOALS"]))
	config.SetTargetDevice(make_vars["TARGET_DEVICE"])

	// MTK customization
	MtkDumpMakeVars(ctx, config)
}
