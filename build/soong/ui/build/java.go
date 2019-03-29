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
	"regexp"
	"runtime"
	"strings"
	"sync"
)

const incompatibleJavacStr = "google"

var javaVersionInfo = struct {
	once      sync.Once
	startOnce sync.Once

	java_version_output  string
	javac_version_output string
}{}

func getJavaVersions(ctx Context, config Config) {
	javaVersionInfo.startOnce.Do(func() {
		go func() {
			if ctx.Tracer != nil {
				thread := ctx.Tracer.NewThread("java_version")
				ctx.Tracer.Begin("get version", thread)
				defer ctx.Tracer.End(thread)
			}

			getJavaVersionsImpl(ctx, config)
		}()
	})
}

func getJavaVersionsImpl(ctx Context, config Config) {
	javaVersionInfo.once.Do(func() {
		cmd := Command(ctx, config, "java", "java", "-version")
		cmd.Environment.Unset("_JAVA_OPTIONS")
		javaVersionInfo.java_version_output = string(cmd.CombinedOutputOrFatal())

		cmd = Command(ctx, config, "javac", "javac", "-version")
		cmd.Environment.Unset("_JAVA_OPTIONS")
		javaVersionInfo.javac_version_output = string(cmd.CombinedOutputOrFatal())
	})
}

func checkJavaVersion(ctx Context, config Config) {
	ctx.BeginTrace("java_version_check")
	defer ctx.EndTrace()

	getJavaVersionsImpl(ctx, config)

	var required_java_version string
	var java_version_regexp *regexp.Regexp
	var javac_version_regexp *regexp.Regexp

	oj9_env, _ := config.Environment().Get("EXPERIMENTAL_USE_OPENJDK9")
	experimental_use_openjdk9 := oj9_env != ""

	if experimental_use_openjdk9 {
		required_java_version = "9"
		java_version_regexp = regexp.MustCompile(`^java .* "9.*"`)
		javac_version_regexp = regexp.MustCompile(`^javac 9`)
	} else {
		required_java_version = "1.8"
		java_version_regexp = regexp.MustCompile(`[ "]1\.8[\. "$]`)
		javac_version_regexp = java_version_regexp
	}

	java_version := javaVersionInfo.java_version_output
	javac_version := javaVersionInfo.javac_version_output

	found := false
	for _, l := range strings.Split(java_version, "\n") {
		if java_version_regexp.MatchString(l) {
			java_version = l
			found = true
			break
		}
	}
	if !found {
		ctx.Println("***************************************************************")
		ctx.Println("You are attempting to build with the incorrect version of java.")
		ctx.Println()
		ctx.Println("Your version is:", java_version)
		ctx.Println("The required version is:", required_java_version+".x")
		ctx.Println()
		ctx.Println("Please follow the machine setup instructions at:")
		ctx.Println("    https://source.android.com/source/initializing.html")
		ctx.Println("***************************************************************")
		ctx.Fatalln("stop")
	}

	if runtime.GOOS == "linux" {
		// Early access builds of OpenJDK 9 do not contain the string "openjdk" in the
		// version name. TODO(tobiast): Reconsider once the OpenJDK 9 toolchain is stable.
		// http://b/62123342
		if !strings.Contains(java_version, "openjdk") && !experimental_use_openjdk9 {
			ctx.Println("*******************************************************")
			ctx.Println("You are attempting to build with an unsupported JDK.")
			ctx.Println()
			ctx.Println("Only an OpenJDK based JDK is supported.")
			ctx.Println()
			ctx.Println("Please follow the machine setup instructions at:")
			ctx.Println("    https://source.android.com/source/initializing.html")
			ctx.Println("*******************************************************")
			ctx.Fatalln("stop")
		}
	} else { // darwin
		if strings.Contains(java_version, "openjdk") {
			ctx.Println("*******************************************************")
			ctx.Println("You are attempting to build with an unsupported JDK.")
			ctx.Println()
			ctx.Println("You use OpenJDK, but only Sun/Oracle JDK is supported.")
			ctx.Println()
			ctx.Println("Please follow the machine setup instructions at:")
			ctx.Println("    https://source.android.com/source/initializing.html")
			ctx.Println("*******************************************************")
			ctx.Fatalln("stop")
		}
	}

	incompatible_javac := strings.Contains(javac_version, incompatibleJavacStr)

	found = false
	for _, l := range strings.Split(javac_version, "\n") {
		if javac_version_regexp.MatchString(l) {
			javac_version = l
			found = true
			break
		}
	}
	if !found || incompatible_javac {
		ctx.Println("****************************************************************")
		ctx.Println("You are attempting to build with the incorrect version of javac.")
		ctx.Println()
		ctx.Println("Your version is:", javac_version)
		if incompatible_javac {
			ctx.Println("The '" + incompatibleJavacStr + "' version is not supported for Android platform builds.")
			ctx.Println("Use a publically available JDK and make sure you have run envsetup.sh / lunch.")
		} else {
			ctx.Println("The required version is:", required_java_version)
		}
		ctx.Println()
		ctx.Println("Please follow the machine setup instructions at:")
		ctx.Println("    https://source.android.com/source/initializing.html")
		ctx.Println("****************************************************************")
		ctx.Fatalln("stop")
	}
}
