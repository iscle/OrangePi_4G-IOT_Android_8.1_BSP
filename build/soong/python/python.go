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

// This file contains the "Base" module type for building Python program.

import (
	"fmt"
	"path/filepath"
	"regexp"
	"sort"
	"strings"

	"github.com/google/blueprint"

	"android/soong/android"
)

func init() {
	android.PreDepsMutators(func(ctx android.RegisterMutatorsContext) {
		ctx.BottomUp("version_split", versionSplitMutator()).Parallel()
	})
}

// the version properties that apply to python libraries and binaries.
type PythonVersionProperties struct {
	// true, if the module is required to be built with this version.
	Enabled *bool

	// if specified, common src files are converted to specific version with converter tool.
	// Converter bool

	// non-empty list of .py files under this strict Python version.
	// srcs may reference the outputs of other modules that produce source files like genrule
	// or filegroup using the syntax ":module".
	Srcs []string

	// list of the Python libraries under this Python version.
	Libs []string
}

// properties that apply to python libraries and binaries.
type PythonBaseModuleProperties struct {
	// the package path prefix within the output artifact at which to place the source/data
	// files of the current module.
	// eg. Pkg_path = "a/b/c"; Other packages can reference this module by using
	// (from a.b.c import ...) statement.
	// if left unspecified, all the source/data files of current module are copied to
	// "runfiles/" tree directory directly.
	Pkg_path string

	// list of source (.py) files compatible both with Python2 and Python3 used to compile the
	// Python module.
	// srcs may reference the outputs of other modules that produce source files like genrule
	// or filegroup using the syntax ":module".
	// Srcs has to be non-empty.
	Srcs []string

	// list of files or filegroup modules that provide data that should be installed alongside
	// the test. the file extension can be arbitrary except for (.py).
	Data []string

	// list of the Python libraries compatible both with Python2 and Python3.
	Libs []string

	Version struct {
		// all the "srcs" or Python dependencies that are to be used only for Python2.
		Py2 PythonVersionProperties

		// all the "srcs" or Python dependencies that are to be used only for Python3.
		Py3 PythonVersionProperties
	}

	// the actual version each module uses after variations created.
	// this property name is hidden from users' perspectives, and soong will populate it during
	// runtime.
	ActualVersion string `blueprint:"mutated"`
}

type pathMapping struct {
	dest string
	src  android.Path
}

type pythonBaseModule struct {
	android.ModuleBase
	subModule PythonSubModule

	properties PythonBaseModuleProperties

	// the Python files of current module after expanding source dependencies.
	// pathMapping: <dest: runfile_path, src: source_path>
	srcsPathMappings []pathMapping

	// the data files of current module after expanding source dependencies.
	// pathMapping: <dest: runfile_path, src: source_path>
	dataPathMappings []pathMapping

	// the soong_zip arguments for zipping current module source/data files.
	parSpec parSpec

	// the installer might be nil.
	installer installer

	subAndroidMkOnce map[subAndroidMkProvider]bool
}

type PythonSubModule interface {
	GeneratePythonBuildActions(ctx android.ModuleContext) android.OptionalPath
}

type PythonDependency interface {
	GetSrcsPathMappings() []pathMapping
	GetDataPathMappings() []pathMapping
	GetParSpec() parSpec
}

type pythonDecorator struct {
	baseInstaller *pythonInstaller
}

type installer interface {
	install(ctx android.ModuleContext, path android.Path)
}

func (p *pythonBaseModule) GetSrcsPathMappings() []pathMapping {
	return p.srcsPathMappings
}

func (p *pythonBaseModule) GetDataPathMappings() []pathMapping {
	return p.dataPathMappings
}

func (p *pythonBaseModule) GetParSpec() parSpec {
	return p.parSpec
}

var _ PythonDependency = (*pythonBaseModule)(nil)

var _ android.AndroidMkDataProvider = (*pythonBaseModule)(nil)

func InitPythonBaseModule(baseModule *pythonBaseModule, subModule PythonSubModule,
	hod android.HostOrDeviceSupported) android.Module {

	baseModule.subModule = subModule

	baseModule.AddProperties(&baseModule.properties)

	android.InitAndroidArchModule(baseModule, hod, android.MultilibCommon)

	return baseModule
}

// the tag used to mark dependencies within "py_libs" attribute.
type pythonDependencyTag struct {
	blueprint.BaseDependencyTag
}

var pyDependencyTag pythonDependencyTag

var (
	pyIdentifierRegexp = regexp.MustCompile(`^([a-z]|[A-Z]|_)([a-z]|[A-Z]|[0-9]|_)*$`)
	pyExt              = ".py"
	pyVersion2         = "PY2"
	pyVersion3         = "PY3"
	initFileName       = "__init__.py"
	mainFileName       = "__main__.py"
	parFileExt         = ".zip"
	runFiles           = "runfiles"
)

// create version variants for modules.
func versionSplitMutator() func(android.BottomUpMutatorContext) {
	return func(mctx android.BottomUpMutatorContext) {
		if base, ok := mctx.Module().(*pythonBaseModule); ok {
			versionNames := []string{}
			if base.properties.Version.Py2.Enabled != nil &&
				*(base.properties.Version.Py2.Enabled) == true {
				versionNames = append(versionNames, pyVersion2)
			}
			if !(base.properties.Version.Py3.Enabled != nil &&
				*(base.properties.Version.Py3.Enabled) == false) {
				versionNames = append(versionNames, pyVersion3)
			}
			modules := mctx.CreateVariations(versionNames...)
			for i, v := range versionNames {
				// set the actual version for Python module.
				modules[i].(*pythonBaseModule).properties.ActualVersion = v
			}
		}
	}
}

func (p *pythonBaseModule) DepsMutator(ctx android.BottomUpMutatorContext) {
	// deps from "data".
	android.ExtractSourcesDeps(ctx, p.properties.Data)
	// deps from "srcs".
	android.ExtractSourcesDeps(ctx, p.properties.Srcs)

	switch p.properties.ActualVersion {
	case pyVersion2:
		// deps from "version.py2.srcs" property.
		android.ExtractSourcesDeps(ctx, p.properties.Version.Py2.Srcs)

		ctx.AddVariationDependencies(nil, pyDependencyTag,
			uniqueLibs(ctx, p.properties.Libs, "version.py2.libs",
				p.properties.Version.Py2.Libs)...)
	case pyVersion3:
		// deps from "version.py3.srcs" property.
		android.ExtractSourcesDeps(ctx, p.properties.Version.Py3.Srcs)

		ctx.AddVariationDependencies(nil, pyDependencyTag,
			uniqueLibs(ctx, p.properties.Libs, "version.py3.libs",
				p.properties.Version.Py3.Libs)...)
	default:
		panic(fmt.Errorf("unknown Python actualVersion: %q for module: %q.",
			p.properties.ActualVersion, ctx.ModuleName()))
	}
}

// check "libs" duplicates from current module dependencies.
func uniqueLibs(ctx android.BottomUpMutatorContext,
	commonLibs []string, versionProp string, versionLibs []string) []string {
	set := make(map[string]string)
	ret := []string{}

	// deps from "libs" property.
	for _, l := range commonLibs {
		if _, found := set[l]; found {
			ctx.PropertyErrorf("libs", "%q has duplicates within libs.", l)
		} else {
			set[l] = "libs"
			ret = append(ret, l)
		}
	}
	// deps from "version.pyX.libs" property.
	for _, l := range versionLibs {
		if _, found := set[l]; found {
			ctx.PropertyErrorf(versionProp, "%q has duplicates within %q.", set[l])
		} else {
			set[l] = versionProp
			ret = append(ret, l)
		}
	}

	return ret
}

func (p *pythonBaseModule) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	installSource := p.subModule.GeneratePythonBuildActions(ctx)

	if p.installer != nil && installSource.Valid() {
		p.installer.install(ctx, installSource.Path())
	}
}

func (p *pythonBaseModule) GeneratePythonBuildActions(ctx android.ModuleContext) android.OptionalPath {
	// expand python files from "srcs" property.
	srcs := p.properties.Srcs
	switch p.properties.ActualVersion {
	case pyVersion2:
		srcs = append(srcs, p.properties.Version.Py2.Srcs...)
	case pyVersion3:
		srcs = append(srcs, p.properties.Version.Py3.Srcs...)
	default:
		panic(fmt.Errorf("unknown Python actualVersion: %q for module: %q.",
			p.properties.ActualVersion, ctx.ModuleName()))
	}
	expandedSrcs := ctx.ExpandSources(srcs, nil)
	if len(expandedSrcs) == 0 {
		ctx.ModuleErrorf("doesn't have any source files!")
	}

	// expand data files from "data" property.
	expandedData := ctx.ExpandSources(p.properties.Data, nil)

	// sanitize pkg_path.
	pkg_path := p.properties.Pkg_path
	if pkg_path != "" {
		pkg_path = filepath.Clean(p.properties.Pkg_path)
		if pkg_path == ".." || strings.HasPrefix(pkg_path, "../") ||
			strings.HasPrefix(pkg_path, "/") {
			ctx.PropertyErrorf("pkg_path", "%q is not a valid format.",
				p.properties.Pkg_path)
			return android.OptionalPath{}
		}
		// pkg_path starts from "runfiles/" implicitly.
		pkg_path = filepath.Join(runFiles, pkg_path)
	} else {
		// pkg_path starts from "runfiles/" implicitly.
		pkg_path = runFiles
	}

	p.genModulePathMappings(ctx, pkg_path, expandedSrcs, expandedData)

	p.parSpec = p.dumpFileList(ctx, pkg_path)

	p.uniqWholeRunfilesTree(ctx)

	return android.OptionalPath{}
}

// generate current module unique pathMappings: <dest: runfiles_path, src: source_path>
// for python/data files.
func (p *pythonBaseModule) genModulePathMappings(ctx android.ModuleContext, pkg_path string,
	expandedSrcs, expandedData android.Paths) {
	// fetch <runfiles_path, source_path> pairs from "src" and "data" properties to
	// check duplicates.
	destToPySrcs := make(map[string]string)
	destToPyData := make(map[string]string)

	for _, s := range expandedSrcs {
		if s.Ext() != pyExt {
			ctx.PropertyErrorf("srcs", "found non (.py) file: %q!", s.String())
			continue
		}
		runfilesPath := filepath.Join(pkg_path, s.Rel())
		identifiers := strings.Split(strings.TrimSuffix(runfilesPath, pyExt), "/")
		for _, token := range identifiers {
			if !pyIdentifierRegexp.MatchString(token) {
				ctx.PropertyErrorf("srcs", "the path %q contains invalid token %q.",
					runfilesPath, token)
			}
		}
		if fillInMap(ctx, destToPySrcs, runfilesPath, s.String(), p.Name(), p.Name()) {
			p.srcsPathMappings = append(p.srcsPathMappings,
				pathMapping{dest: runfilesPath, src: s})
		}
	}

	for _, d := range expandedData {
		if d.Ext() == pyExt {
			ctx.PropertyErrorf("data", "found (.py) file: %q!", d.String())
			continue
		}
		runfilesPath := filepath.Join(pkg_path, d.Rel())
		if fillInMap(ctx, destToPyData, runfilesPath, d.String(), p.Name(), p.Name()) {
			p.dataPathMappings = append(p.dataPathMappings,
				pathMapping{dest: runfilesPath, src: d})
		}
	}

}

// register build actions to dump filelist to disk.
func (p *pythonBaseModule) dumpFileList(ctx android.ModuleContext, pkg_path string) parSpec {
	relativeRootMap := make(map[string]android.Paths)
	// the soong_zip params in order to pack current module's Python/data files.
	ret := parSpec{rootPrefix: pkg_path}

	pathMappings := append(p.srcsPathMappings, p.dataPathMappings...)

	// "srcs" or "data" properties may have filegroup so it might happen that
	// the relative root for each source path is different.
	for _, path := range pathMappings {
		relativeRoot := strings.TrimSuffix(path.src.String(), path.src.Rel())
		if v, found := relativeRootMap[relativeRoot]; found {
			relativeRootMap[relativeRoot] = append(v, path.src)
		} else {
			relativeRootMap[relativeRoot] = android.Paths{path.src}
		}
	}

	var keys []string

	// in order to keep stable order of soong_zip params, we sort the keys here.
	for k := range relativeRootMap {
		keys = append(keys, k)
	}
	sort.Strings(keys)

	for _, k := range keys {
		// use relative root as filelist name.
		fileListPath := registerBuildActionForModuleFileList(
			ctx, strings.Replace(k, "/", "_", -1), relativeRootMap[k])
		ret.fileListSpecs = append(ret.fileListSpecs,
			fileListSpec{fileList: fileListPath, relativeRoot: k})
	}

	return ret
}

// check Python/data files duplicates from current module and its whole dependencies.
func (p *pythonBaseModule) uniqWholeRunfilesTree(ctx android.ModuleContext) {
	// fetch <runfiles_path, source_path> pairs from "src" and "data" properties to
	// check duplicates.
	destToPySrcs := make(map[string]string)
	destToPyData := make(map[string]string)

	for _, path := range p.srcsPathMappings {
		destToPySrcs[path.dest] = path.src.String()
	}
	for _, path := range p.dataPathMappings {
		destToPyData[path.dest] = path.src.String()
	}

	// visit all its dependencies in depth first.
	ctx.VisitDepsDepthFirst(func(module blueprint.Module) {
		// module can only depend on Python library.
		if base, ok := module.(*pythonBaseModule); ok {
			if _, ok := base.subModule.(*PythonLibrary); !ok {
				panic(fmt.Errorf(
					"the dependency %q of module %q is not Python library!",
					ctx.ModuleName(), ctx.OtherModuleName(module)))
			}
		} else {
			return
		}
		if dep, ok := module.(PythonDependency); ok {
			srcs := dep.GetSrcsPathMappings()
			for _, path := range srcs {
				if !fillInMap(ctx, destToPySrcs,
					path.dest, path.src.String(), ctx.ModuleName(),
					ctx.OtherModuleName(module)) {
					continue
				}
				// binary needs the Python runfiles paths from all its
				// dependencies to fill __init__.py in each runfiles dir.
				if sub, ok := p.subModule.(*pythonBinaryBase); ok {
					sub.depsPyRunfiles = append(sub.depsPyRunfiles, path.dest)
				}
			}
			data := dep.GetDataPathMappings()
			for _, path := range data {
				fillInMap(ctx, destToPyData,
					path.dest, path.src.String(), ctx.ModuleName(),
					ctx.OtherModuleName(module))
			}
			// binary needs the soong_zip arguments from all its
			// dependencies to generate executable par file.
			if sub, ok := p.subModule.(*pythonBinaryBase); ok {
				sub.depsParSpecs = append(sub.depsParSpecs, dep.GetParSpec())
			}
		}
	})
}

func fillInMap(ctx android.ModuleContext, m map[string]string,
	key, value, curModule, otherModule string) bool {
	if oldValue, found := m[key]; found {
		ctx.ModuleErrorf("found two files to be placed at the same runfiles location %q."+
			" First file: in module %s at path %q."+
			" Second file: in module %s at path %q.",
			key, curModule, oldValue, otherModule, value)
		return false
	} else {
		m[key] = value
	}

	return true
}
