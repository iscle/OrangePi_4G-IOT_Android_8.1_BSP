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

package java

import (
	"android/soong/android"
	"fmt"
	"io/ioutil"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

var buildDir string

func setUp() {
	var err error
	buildDir, err = ioutil.TempDir("", "soong_java_test")
	if err != nil {
		panic(err)
	}
}

func tearDown() {
	os.RemoveAll(buildDir)
}

func TestMain(m *testing.M) {
	run := func() int {
		setUp()
		defer tearDown()

		return m.Run()
	}

	os.Exit(run())
}

func testJava(t *testing.T, bp string) *android.TestContext {
	config := android.TestConfig(buildDir)

	ctx := android.NewTestContext()
	ctx.RegisterModuleType("android_app", android.ModuleFactoryAdaptor(AndroidAppFactory))
	ctx.RegisterModuleType("java_library", android.ModuleFactoryAdaptor(JavaLibraryFactory))
	ctx.RegisterModuleType("java_prebuilt_library", android.ModuleFactoryAdaptor(JavaPrebuiltFactory))
	ctx.Register()

	extraModules := []string{"core-libart", "frameworks", "sdk_v14"}

	for _, extra := range extraModules {
		bp += fmt.Sprintf(`
			java_library {
				name: "%s",
				no_standard_libraries: true,
			}
		`, extra)
	}

	ctx.MockFileSystem(map[string][]byte{
		"Android.bp": []byte(bp),
		"a.java":     nil,
		"b.java":     nil,
		"c.java":     nil,
		"a.jar":      nil,
		"b.jar":      nil,
	})

	_, errs := ctx.ParseBlueprintsFiles("Android.bp")
	fail(t, errs)
	_, errs = ctx.PrepareBuildActions(config)
	fail(t, errs)

	return ctx
}

func TestSimple(t *testing.T) {
	ctx := testJava(t, `
		java_library {
			name: "foo",
			srcs: ["a.java"],
			libs: ["bar"],
			static_libs: ["baz"],
		}

		java_library {
			name: "bar",
			srcs: ["b.java"],
		}

		java_library {
			name: "baz",
			srcs: ["c.java"],
		}
		`)

	javac := ctx.ModuleForTests("foo", "").Rule("javac")
	jar := ctx.ModuleForTests("foo", "").Rule("jar")

	if len(javac.Inputs) != 1 || javac.Inputs[0].String() != "a.java" {
		t.Errorf(`foo inputs %v != ["a.java"]`, javac.Inputs)
	}

	bar := filepath.Join(buildDir, ".intermediates", "bar", "classes-full-debug.jar")
	if !strings.Contains(javac.Args["classpath"], bar) {
		t.Errorf("foo classpath %v does not contain %q", javac.Args["classpath"], bar)
	}

	baz := filepath.Join(buildDir, ".intermediates", "baz", "classes.list")
	if !strings.Contains(jar.Args["jarArgs"], baz) {
		t.Errorf("foo jarArgs %v does not contain %q", jar.Args["jarArgs"], baz)
	}
}

func TestSdk(t *testing.T) {
	ctx := testJava(t, `
		java_library {
			name: "foo1",
			srcs: ["a.java"],
		}

		java_library {
			name: "foo2",
			srcs: ["a.java"],
			sdk_version: "",
		}

		java_library {
			name: "foo3",
			srcs: ["a.java"],
			sdk_version: "14",
		}

		java_library {
			name: "foo4",
			srcs: ["a.java"],
			sdk_version: "current",
		}

		java_library {
			name: "foo5",
			srcs: ["a.java"],
			sdk_version: "system_current",
		}

		java_library {
			name: "foo6",
			srcs: ["a.java"],
			sdk_version: "test_current",
		}
		`)

	type depType int
	const (
		staticLib = iota
		classpathLib
		bootclasspathLib
	)

	check := func(module, dep string, depType depType) {
		if dep != "" {
			dep = filepath.Join(buildDir, ".intermediates", dep, "classes-full-debug.jar")
		}

		javac := ctx.ModuleForTests(module, "").Rule("javac")

		if depType == bootclasspathLib {
			got := strings.TrimPrefix(javac.Args["bootClasspath"], "-bootclasspath ")
			if got != dep {
				t.Errorf("module %q bootclasspath %q != %q", module, got, dep)
			}
		} else if depType == classpathLib {
			got := strings.TrimPrefix(javac.Args["classpath"], "-classpath ")
			if got != dep {
				t.Errorf("module %q classpath %q != %q", module, got, dep)
			}
		}

		if len(javac.Implicits) != 1 || javac.Implicits[0].String() != dep {
			t.Errorf("module %q implicits != [%q]", dep)
		}
	}

	check("foo1", "core-libart", bootclasspathLib)
}

func TestPrebuilts(t *testing.T) {
	ctx := testJava(t, `
		java_library {
			name: "foo",
			srcs: ["a.java"],
			libs: ["bar"],
			static_libs: ["baz"],
		}

		java_prebuilt_library {
			name: "bar",
			srcs: ["a.jar"],
		}

		java_prebuilt_library {
			name: "baz",
			srcs: ["b.jar"],
		}
		`)

	javac := ctx.ModuleForTests("foo", "").Rule("javac")
	jar := ctx.ModuleForTests("foo", "").Rule("jar")

	bar := "a.jar"
	if !strings.Contains(javac.Args["classpath"], bar) {
		t.Errorf("foo classpath %v does not contain %q", javac.Args["classpath"], bar)
	}

	baz := filepath.Join(buildDir, ".intermediates", "baz", "extracted", "classes.list")
	if !strings.Contains(jar.Args["jarArgs"], baz) {
		t.Errorf("foo jarArgs %v does not contain %q", jar.Args["jarArgs"], baz)
	}
}

func fail(t *testing.T, errs []error) {
	if len(errs) > 0 {
		for _, err := range errs {
			t.Error(err)
		}
		t.FailNow()
	}
}
