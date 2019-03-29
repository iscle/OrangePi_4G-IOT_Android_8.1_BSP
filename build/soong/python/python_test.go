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
	"errors"
	"fmt"
	"io/ioutil"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"

	"android/soong/android"
)

type pyBinary struct {
	name           string
	actualVersion  string
	pyRunfiles     []string
	depsPyRunfiles []string
	parSpec        string
	depsParSpecs   []string
}

var (
	buildNamePrefix          = "soong_python_test"
	moduleVariantErrTemplate = "%s: module %q variant %q: "
	pkgPathErrTemplate       = moduleVariantErrTemplate +
		"pkg_path: %q is not a valid format."
	badIdentifierErrTemplate = moduleVariantErrTemplate +
		"srcs: the path %q contains invalid token %q."
	dupRunfileErrTemplate = moduleVariantErrTemplate +
		"found two files to be placed at the same runfiles location %q." +
		" First file: in module %s at path %q." +
		" Second file: in module %s at path %q."
	noSrcFileErr      = moduleVariantErrTemplate + "doesn't have any source files!"
	badSrcFileExtErr  = moduleVariantErrTemplate + "srcs: found non (.py) file: %q!"
	badDataFileExtErr = moduleVariantErrTemplate + "data: found (.py) file: %q!"
	bpFile            = "Blueprints"

	data = []struct {
		desc      string
		mockFiles map[string][]byte

		errors           []string
		expectedBinaries []pyBinary
	}{
		{
			desc: "module without any src files",
			mockFiles: map[string][]byte{
				bpFile: []byte(`subdirs = ["dir"]`),
				filepath.Join("dir", bpFile): []byte(
					`python_library_host {
						name: "lib1",
					}`,
				),
			},
			errors: []string{
				fmt.Sprintf(noSrcFileErr,
					"dir/Blueprints:1:1", "lib1", "PY3"),
			},
		},
		{
			desc: "module with bad src file ext",
			mockFiles: map[string][]byte{
				bpFile: []byte(`subdirs = ["dir"]`),
				filepath.Join("dir", bpFile): []byte(
					`python_library_host {
						name: "lib1",
						srcs: [
							"file1.exe",
						],
					}`,
				),
				"dir/file1.exe": nil,
			},
			errors: []string{
				fmt.Sprintf(badSrcFileExtErr,
					"dir/Blueprints:3:11", "lib1", "PY3", "dir/file1.exe"),
			},
		},
		{
			desc: "module with bad data file ext",
			mockFiles: map[string][]byte{
				bpFile: []byte(`subdirs = ["dir"]`),
				filepath.Join("dir", bpFile): []byte(
					`python_library_host {
						name: "lib1",
						srcs: [
							"file1.py",
						],
						data: [
							"file2.py",
						],
					}`,
				),
				"dir/file1.py": nil,
				"dir/file2.py": nil,
			},
			errors: []string{
				fmt.Sprintf(badDataFileExtErr,
					"dir/Blueprints:6:11", "lib1", "PY3", "dir/file2.py"),
			},
		},
		{
			desc: "module with bad pkg_path format",
			mockFiles: map[string][]byte{
				bpFile: []byte(`subdirs = ["dir"]`),
				filepath.Join("dir", bpFile): []byte(
					`python_library_host {
						name: "lib1",
						pkg_path: "a/c/../../",
						srcs: [
							"file1.py",
						],
					}

					python_library_host {
						name: "lib2",
						pkg_path: "a/c/../../../",
						srcs: [
							"file1.py",
						],
					}

					python_library_host {
						name: "lib3",
						pkg_path: "/a/c/../../",
						srcs: [
							"file1.py",
						],
					}`,
				),
				"dir/file1.py": nil,
			},
			errors: []string{
				fmt.Sprintf(pkgPathErrTemplate,
					"dir/Blueprints:11:15", "lib2", "PY3", "a/c/../../../"),
				fmt.Sprintf(pkgPathErrTemplate,
					"dir/Blueprints:19:15", "lib3", "PY3", "/a/c/../../"),
			},
		},
		{
			desc: "module with bad runfile src path format",
			mockFiles: map[string][]byte{
				bpFile: []byte(`subdirs = ["dir"]`),
				filepath.Join("dir", bpFile): []byte(
					`python_library_host {
						name: "lib1",
						pkg_path: "a/b/c/",
						srcs: [
							".file1.py",
							"123/file1.py",
							"-e/f/file1.py",
						],
					}`,
				),
				"dir/.file1.py":     nil,
				"dir/123/file1.py":  nil,
				"dir/-e/f/file1.py": nil,
			},
			errors: []string{
				fmt.Sprintf(badIdentifierErrTemplate, "dir/Blueprints:4:11",
					"lib1", "PY3", "runfiles/a/b/c/-e/f/file1.py", "-e"),
				fmt.Sprintf(badIdentifierErrTemplate, "dir/Blueprints:4:11",
					"lib1", "PY3", "runfiles/a/b/c/.file1.py", ".file1"),
				fmt.Sprintf(badIdentifierErrTemplate, "dir/Blueprints:4:11",
					"lib1", "PY3", "runfiles/a/b/c/123/file1.py", "123"),
			},
		},
		{
			desc: "module with duplicate runfile path",
			mockFiles: map[string][]byte{
				bpFile: []byte(`subdirs = ["dir"]`),
				filepath.Join("dir", bpFile): []byte(
					`python_library_host {
						name: "lib1",
						pkg_path: "a/b/",
						srcs: [
							"c/file1.py",
						],
					}

					python_library_host {
						name: "lib2",
						pkg_path: "a/b/c/",
						srcs: [
							"file1.py",
						],
						libs: [
							"lib1",
						],
					}
					`,
				),
				"dir/c/file1.py": nil,
				"dir/file1.py":   nil,
			},
			errors: []string{
				fmt.Sprintf(dupRunfileErrTemplate, "dir/Blueprints:9:6",
					"lib2", "PY3", "runfiles/a/b/c/file1.py", "lib2", "dir/file1.py",
					"lib1", "dir/c/file1.py"),
			},
		},
		{
			desc: "module for testing dependencies",
			mockFiles: map[string][]byte{
				bpFile: []byte(`subdirs = ["dir"]`),
				filepath.Join("dir", bpFile): []byte(
					`python_library_host {
						name: "lib5",
						pkg_path: "a/b/",
						srcs: [
							"file1.py",
						],
						version: {
							py2: {
								enabled: true,
							},
							py3: {
								enabled: true,
							},
						},
					}

					python_library_host {
						name: "lib6",
						pkg_path: "c/d/",
						srcs: [
							"file2.py",
						],
						libs: [
							"lib5",
						],
					}

					python_binary_host {
						name: "bin",
						pkg_path: "e/",
						srcs: [
							"bin.py",
						],
						libs: [
							"lib5",
						],
						version: {
							py3: {
								enabled: true,
								srcs: [
									"file4.py",
								],
								libs: [
									"lib6",
								],
							},
						},
					}`,
				),
				filepath.Join("dir", "file1.py"): nil,
				filepath.Join("dir", "file2.py"): nil,
				filepath.Join("dir", "bin.py"):   nil,
				filepath.Join("dir", "file4.py"): nil,
				stubTemplateHost: []byte(`PYTHON_BINARY = '%interpreter%'
				MAIN_FILE = '%main%'`),
			},
			expectedBinaries: []pyBinary{
				{
					name:          "bin",
					actualVersion: "PY3",
					pyRunfiles: []string{
						"runfiles/e/bin.py",
						"runfiles/e/file4.py",
					},
					depsPyRunfiles: []string{
						"runfiles/a/b/file1.py",
						"runfiles/c/d/file2.py",
					},
					parSpec: "-P runfiles/e -C dir/ -l @prefix@/.intermediates/dir/bin/PY3/dir_.list",
					depsParSpecs: []string{
						"-P runfiles/a/b -C dir/ -l @prefix@/.intermediates/dir/lib5/PY3/dir_.list",
						"-P runfiles/c/d -C dir/ -l @prefix@/.intermediates/dir/lib6/PY3/dir_.list",
					},
				},
			},
		},
	}
)

func TestPythonModule(t *testing.T) {
	config, buildDir := setupBuildEnv(t)
	defer tearDownBuildEnv(buildDir)
	for _, d := range data {
		t.Run(d.desc, func(t *testing.T) {
			ctx := android.NewTestContext()
			ctx.PreDepsMutators(func(ctx android.RegisterMutatorsContext) {
				ctx.BottomUp("version_split", versionSplitMutator()).Parallel()
			})
			ctx.RegisterModuleType("python_library_host",
				android.ModuleFactoryAdaptor(PythonLibraryHostFactory))
			ctx.RegisterModuleType("python_binary_host",
				android.ModuleFactoryAdaptor(PythonBinaryHostFactory))
			ctx.Register()
			ctx.MockFileSystem(d.mockFiles)
			_, testErrs := ctx.ParseBlueprintsFiles(bpFile)
			fail(t, testErrs)
			_, actErrs := ctx.PrepareBuildActions(config)
			if len(actErrs) > 0 {
				testErrs = append(testErrs, expectErrors(t, actErrs, d.errors)...)
			} else {
				for _, e := range d.expectedBinaries {
					testErrs = append(testErrs,
						expectModule(t, ctx, buildDir, e.name,
							e.actualVersion,
							e.pyRunfiles, e.depsPyRunfiles,
							e.parSpec, e.depsParSpecs)...)
				}
			}
			fail(t, testErrs)
		})
	}
}

func expectErrors(t *testing.T, actErrs []error, expErrs []string) (testErrs []error) {
	actErrStrs := []string{}
	for _, v := range actErrs {
		actErrStrs = append(actErrStrs, v.Error())
	}
	sort.Strings(actErrStrs)
	if len(actErrStrs) != len(expErrs) {
		t.Errorf("got (%d) errors, expected (%d) errors!", len(actErrStrs), len(expErrs))
		for _, v := range actErrStrs {
			testErrs = append(testErrs, errors.New(v))
		}
	} else {
		sort.Strings(expErrs)
		for i, v := range actErrStrs {
			if v != expErrs[i] {
				testErrs = append(testErrs, errors.New(v))
			}
		}
	}

	return
}

func expectModule(t *testing.T, ctx *android.TestContext, buildDir, name, variant string,
	expPyRunfiles, expDepsPyRunfiles []string,
	expParSpec string, expDepsParSpecs []string) (testErrs []error) {
	module := ctx.ModuleForTests(name, variant)

	base, baseOk := module.Module().(*pythonBaseModule)
	if !baseOk {
		t.Fatalf("%s is not Python module!", name)
	}
	sub, subOk := base.subModule.(*pythonBinaryBase)
	if !subOk {
		t.Fatalf("%s is not Python binary!", name)
	}

	actPyRunfiles := []string{}
	for _, path := range base.srcsPathMappings {
		actPyRunfiles = append(actPyRunfiles, path.dest)
	}

	if !reflect.DeepEqual(actPyRunfiles, expPyRunfiles) {
		testErrs = append(testErrs, errors.New(fmt.Sprintf(
			`binary "%s" variant "%s" has unexpected pyRunfiles: %q!`,
			base.Name(),
			base.properties.ActualVersion,
			actPyRunfiles)))
	}

	if !reflect.DeepEqual(sub.depsPyRunfiles, expDepsPyRunfiles) {
		testErrs = append(testErrs, errors.New(fmt.Sprintf(
			`binary "%s" variant "%s" has unexpected depsPyRunfiles: %q!`,
			base.Name(),
			base.properties.ActualVersion,
			sub.depsPyRunfiles)))
	}

	if base.parSpec.soongParArgs() != strings.Replace(expParSpec, "@prefix@", buildDir, 1) {
		testErrs = append(testErrs, errors.New(fmt.Sprintf(
			`binary "%s" variant "%s" has unexpected parSpec: %q!`,
			base.Name(),
			base.properties.ActualVersion,
			base.parSpec.soongParArgs())))
	}

	actDepsParSpecs := []string{}
	for i, p := range sub.depsParSpecs {
		actDepsParSpecs = append(actDepsParSpecs, p.soongParArgs())
		expDepsParSpecs[i] = strings.Replace(expDepsParSpecs[i], "@prefix@", buildDir, 1)
	}

	if !reflect.DeepEqual(actDepsParSpecs, expDepsParSpecs) {
		testErrs = append(testErrs, errors.New(fmt.Sprintf(
			`binary "%s" variant "%s" has unexpected depsParSpecs: %q!`,
			base.Name(),
			base.properties.ActualVersion,
			actDepsParSpecs)))
	}

	return
}

func setupBuildEnv(t *testing.T) (config android.Config, buildDir string) {
	buildDir, err := ioutil.TempDir("", buildNamePrefix)
	if err != nil {
		t.Fatal(err)
	}

	config = android.TestConfig(buildDir)

	return
}

func tearDownBuildEnv(buildDir string) {
	os.RemoveAll(buildDir)
}

func fail(t *testing.T, errs []error) {
	if len(errs) > 0 {
		for _, err := range errs {
			t.Error(err)
		}
		t.FailNow()
	}
}
