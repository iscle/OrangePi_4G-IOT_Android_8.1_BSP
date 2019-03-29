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
	"log"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"

	"android/soong/shared"
)

type Config struct{ *configImpl }

type configImpl struct {
	// From the environment
	arguments []string
	goma      bool
	environ   *Environment

	// From the arguments
	parallel  int
	keepGoing int
	verbose   bool
	dist      bool

	// From the product config
	katiArgs     []string
	ninjaArgs    []string
	katiSuffix   string
	targetDevice string
}

const srcDirFileCheck = "build/soong/root.bp"

func NewConfig(ctx Context, args ...string) Config {
	ret := &configImpl{
		environ: OsEnvironment(),
	}

	// Sane default matching ninja
	ret.parallel = runtime.NumCPU() + 2
	ret.keepGoing = 1

	ret.parseArgs(ctx, args)

	// Make sure OUT_DIR is set appropriately
	if outDir, ok := ret.environ.Get("OUT_DIR"); ok {
		ret.environ.Set("OUT_DIR", filepath.Clean(outDir))
	} else {
		outDir := "out"
		if baseDir, ok := ret.environ.Get("OUT_DIR_COMMON_BASE"); ok {
			if wd, err := os.Getwd(); err != nil {
				ctx.Fatalln("Failed to get working directory:", err)
			} else {
				outDir = filepath.Join(baseDir, filepath.Base(wd))
			}
		}
		ret.environ.Set("OUT_DIR", outDir)
	}

	ret.environ.Unset(
		// We're already using it
		"USE_SOONG_UI",

		// We should never use GOROOT/GOPATH from the shell environment
		"GOROOT",
		"GOPATH",

		// These should only come from Soong, not the environment.
		"CLANG",
		"CLANG_CXX",
		"CCC_CC",
		"CCC_CXX",

		// Used by the goma compiler wrapper, but should only be set by
		// gomacc
		"GOMACC_PATH",

		// We handle this above
		"OUT_DIR_COMMON_BASE",

		// Variables that have caused problems in the past
		"DISPLAY",
		"GREP_OPTIONS",

		// Drop make flags
		"MAKEFLAGS",
		"MAKELEVEL",
		"MFLAGS",
	)

	// Tell python not to spam the source tree with .pyc files.
	ret.environ.Set("PYTHONDONTWRITEBYTECODE", "1")

	// Precondition: the current directory is the top of the source tree
	if _, err := os.Stat(srcDirFileCheck); err != nil {
		if os.IsNotExist(err) {
			log.Fatalf("Current working directory must be the source tree. %q not found", srcDirFileCheck)
		}
		log.Fatalln("Error verifying tree state:", err)
	}

	if srcDir, err := filepath.Abs("."); err == nil {
		if strings.ContainsRune(srcDir, ' ') {
			log.Println("You are building in a directory whose absolute path contains a space character:")
			log.Println()
			log.Printf("%q\n", srcDir)
			log.Println()
			log.Fatalln("Directory names containing spaces are not supported")
		}
	}

	if outDir := ret.OutDir(); strings.ContainsRune(outDir, ' ') {
		log.Println("The absolute path of your output directory ($OUT_DIR) contains a space character:")
		log.Println()
		log.Printf("%q\n", outDir)
		log.Println()
		log.Fatalln("Directory names containing spaces are not supported")
	}

	if distDir := ret.DistDir(); strings.ContainsRune(distDir, ' ') {
		log.Println("The absolute path of your dist directory ($DIST_DIR) contains a space character:")
		log.Println()
		log.Printf("%q\n", distDir)
		log.Println()
		log.Fatalln("Directory names containing spaces are not supported")
	}

	return Config{ret}
}

func (c *configImpl) parseArgs(ctx Context, args []string) {
	for i := 0; i < len(args); i++ {
		arg := strings.TrimSpace(args[i])
		if arg == "--make-mode" {
			continue
		} else if arg == "showcommands" {
			c.verbose = true
			continue
		} else if arg == "dist" {
			c.dist = true
		}
		if arg[0] == '-' {
			parseArgNum := func(def int) int {
				if len(arg) > 2 {
					p, err := strconv.ParseUint(arg[2:], 10, 31)
					if err != nil {
						ctx.Fatalf("Failed to parse %q: %v", arg, err)
					}
					return int(p)
				} else if i+1 < len(args) {
					p, err := strconv.ParseUint(args[i+1], 10, 31)
					if err == nil {
						i++
						return int(p)
					}
				}
				return def
			}

			if arg[1] == 'j' {
				c.parallel = parseArgNum(c.parallel)
			} else if arg[1] == 'k' {
				c.keepGoing = parseArgNum(0)
			} else {
				ctx.Fatalln("Unknown option:", arg)
			}
		} else if k, v, ok := decodeKeyValue(arg); ok && len(k) > 0 {
			c.environ.Set(k, v)
		} else {
			c.arguments = append(c.arguments, arg)
		}
	}
}

// Lunch configures the environment for a specific product similarly to the
// `lunch` bash function.
func (c *configImpl) Lunch(ctx Context, product, variant string) {
	if variant != "eng" && variant != "userdebug" && variant != "user" {
		ctx.Fatalf("Invalid variant %q. Must be one of 'user', 'userdebug' or 'eng'", variant)
	}

	c.environ.Set("TARGET_PRODUCT", product)
	c.environ.Set("TARGET_BUILD_VARIANT", variant)
	c.environ.Set("TARGET_BUILD_TYPE", "release")
	c.environ.Unset("TARGET_BUILD_APPS")
}

// Tapas configures the environment to build one or more unbundled apps,
// similarly to the `tapas` bash function.
func (c *configImpl) Tapas(ctx Context, apps []string, arch, variant string) {
	if len(apps) == 0 {
		apps = []string{"all"}
	}
	if variant == "" {
		variant = "eng"
	}

	if variant != "eng" && variant != "userdebug" && variant != "user" {
		ctx.Fatalf("Invalid variant %q. Must be one of 'user', 'userdebug' or 'eng'", variant)
	}

	var product string
	switch arch {
	case "armv5":
		product = "generic_armv5"
	case "arm", "":
		product = "aosp_arm"
	case "arm64":
		product = "aosm_arm64"
	case "mips":
		product = "aosp_mips"
	case "mips64":
		product = "aosp_mips64"
	case "x86":
		product = "aosp_x86"
	case "x86_64":
		product = "aosp_x86_64"
	default:
		ctx.Fatalf("Invalid architecture: %q", arch)
	}

	c.environ.Set("TARGET_PRODUCT", product)
	c.environ.Set("TARGET_BUILD_VARIANT", variant)
	c.environ.Set("TARGET_BUILD_TYPE", "release")
	c.environ.Set("TARGET_BUILD_APPS", strings.Join(apps, " "))
}

func (c *configImpl) Environment() *Environment {
	return c.environ
}

func (c *configImpl) Arguments() []string {
	return c.arguments
}

func (c *configImpl) OutDir() string {
	if outDir, ok := c.environ.Get("OUT_DIR"); ok {
		return outDir
	}
	return "out"
}

func (c *configImpl) DistDir() string {
	if distDir, ok := c.environ.Get("DIST_DIR"); ok {
		return distDir
	}
	return filepath.Join(c.OutDir(), "dist")
}

func (c *configImpl) NinjaArgs() []string {
	return c.ninjaArgs
}

func (c *configImpl) SoongOutDir() string {
	return filepath.Join(c.OutDir(), "soong")
}

func (c *configImpl) TempDir() string {
	return shared.TempDirForOutDir(c.SoongOutDir())
}

func (c *configImpl) KatiSuffix() string {
	if c.katiSuffix != "" {
		return c.katiSuffix
	}
	panic("SetKatiSuffix has not been called")
}

func (c *configImpl) Dist() bool {
	return c.dist
}

func (c *configImpl) IsVerbose() bool {
	return c.verbose
}

func (c *configImpl) TargetProduct() string {
	if v, ok := c.environ.Get("TARGET_PRODUCT"); ok {
		return v
	}
	panic("TARGET_PRODUCT is not defined")
}

func (c *configImpl) TargetDevice() string {
	return c.targetDevice
}

func (c *configImpl) SetTargetDevice(device string) {
	c.targetDevice = device
}

func (c *configImpl) TargetBuildVariant() string {
	if v, ok := c.environ.Get("TARGET_BUILD_VARIANT"); ok {
		return v
	}
	panic("TARGET_BUILD_VARIANT is not defined")
}

func (c *configImpl) KatiArgs() []string {
	return c.katiArgs
}

func (c *configImpl) Parallel() int {
	return c.parallel
}

func (c *configImpl) UseGoma() bool {
	if v, ok := c.environ.Get("USE_GOMA"); ok {
		v = strings.TrimSpace(v)
		if v != "" && v != "false" {
			return true
		}
	}
	return false
}

// RemoteParallel controls how many remote jobs (i.e., commands which contain
// gomacc) are run in parallel.  Note the parallelism of all other jobs is
// still limited by Parallel()
func (c *configImpl) RemoteParallel() int {
	if v, ok := c.environ.Get("NINJA_REMOTE_NUM_JOBS"); ok {
		if i, err := strconv.Atoi(v); err == nil {
			return i
		}
	}
	return 500
}

func (c *configImpl) SetKatiArgs(args []string) {
	c.katiArgs = args
}

func (c *configImpl) SetNinjaArgs(args []string) {
	c.ninjaArgs = args
}

func (c *configImpl) SetKatiSuffix(suffix string) {
	c.katiSuffix = suffix
}

func (c *configImpl) KatiEnvFile() string {
	return filepath.Join(c.OutDir(), "env"+c.KatiSuffix()+".sh")
}

func (c *configImpl) KatiNinjaFile() string {
	return filepath.Join(c.OutDir(), "build"+c.KatiSuffix()+".ninja")
}

func (c *configImpl) SoongNinjaFile() string {
	return filepath.Join(c.SoongOutDir(), "build.ninja")
}

func (c *configImpl) CombinedNinjaFile() string {
	return filepath.Join(c.OutDir(), "combined"+c.KatiSuffix()+".ninja")
}

func (c *configImpl) SoongAndroidMk() string {
	return filepath.Join(c.SoongOutDir(), "Android-"+c.TargetProduct()+".mk")
}

func (c *configImpl) SoongMakeVarsMk() string {
	return filepath.Join(c.SoongOutDir(), "make_vars-"+c.TargetProduct()+".mk")
}

func (c *configImpl) ProductOut() string {
	// MTK customization
	if buildType, ok := c.environ.Get("TARGET_BUILD_TYPE"); ok && buildType == "debug" {
		return filepath.Join(c.OutDir(), "debug", "target", "product", c.MtkTargetProjectName())
	} else {
		return filepath.Join(c.OutDir(), "target", "product", c.MtkTargetProjectName())
	}
}

func (c *configImpl) DevicePreviousProductConfig() string {
	return filepath.Join(c.ProductOut(), "previous_build_config.mk")
}

func (c *configImpl) hostOutRoot() string {
	if buildType, ok := c.environ.Get("HOST_BUILD_TYPE"); ok && buildType == "debug" {
		return filepath.Join(c.OutDir(), "debug", "host")
	} else {
		return filepath.Join(c.OutDir(), "host")
	}
}

func (c *configImpl) HostOut() string {
	return filepath.Join(c.hostOutRoot(), c.HostPrebuiltTag())
}

// This probably needs to be multi-valued, so not exporting it for now
func (c *configImpl) hostCrossOut() string {
	if runtime.GOOS == "linux" {
		return filepath.Join(c.hostOutRoot(), "windows-x86")
	} else {
		return ""
	}
}

func (c *configImpl) HostPrebuiltTag() string {
	if runtime.GOOS == "linux" {
		return "linux-x86"
	} else if runtime.GOOS == "darwin" {
		return "darwin-x86"
	} else {
		panic("Unsupported OS")
	}
}

func (c *configImpl) HostAsan() bool {
	if v, ok := c.environ.Get("SANITIZE_HOST"); ok {
		if sanitize := strings.Fields(v); inList("address", sanitize) {
			return true
		}
	}
	return false
}

func (c *configImpl) PrebuiltBuildTool(name string) string {
	// (b/36182021) We're seeing rare ckati crashes, so always enable asan kati on the build servers.
	if c.HostAsan() || (c.Dist() && name == "ckati") {
		asan := filepath.Join("prebuilts/build-tools", c.HostPrebuiltTag(), "asan/bin", name)
		if _, err := os.Stat(asan); err == nil {
			return asan
		}
	}
	return filepath.Join("prebuilts/build-tools", c.HostPrebuiltTag(), "bin", name)
}
