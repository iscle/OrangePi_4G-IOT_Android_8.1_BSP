// Copyright 2015 Google Inc. All rights reserved.
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

package android

import (
	"fmt"
	"path/filepath"
	"reflect"
	"strings"

	"github.com/google/blueprint"
	"github.com/google/blueprint/pathtools"
)

// PathContext is the subset of a (Module|Singleton)Context required by the
// Path methods.
type PathContext interface {
	Fs() pathtools.FileSystem
	Config() interface{}
	AddNinjaFileDeps(deps ...string)
}

type PathGlobContext interface {
	GlobWithDeps(globPattern string, excludes []string) ([]string, error)
}

var _ PathContext = blueprint.SingletonContext(nil)
var _ PathContext = blueprint.ModuleContext(nil)

type ModuleInstallPathContext interface {
	PathContext

	androidBaseContext

	InstallInData() bool
	InstallInSanitizerDir() bool
}

var _ ModuleInstallPathContext = ModuleContext(nil)

// errorfContext is the interface containing the Errorf method matching the
// Errorf method in blueprint.SingletonContext.
type errorfContext interface {
	Errorf(format string, args ...interface{})
}

var _ errorfContext = blueprint.SingletonContext(nil)

// moduleErrorf is the interface containing the ModuleErrorf method matching
// the ModuleErrorf method in blueprint.ModuleContext.
type moduleErrorf interface {
	ModuleErrorf(format string, args ...interface{})
}

var _ moduleErrorf = blueprint.ModuleContext(nil)

// pathConfig returns the android Config interface associated to the context.
// Panics if the context isn't affiliated with an android build.
func pathConfig(ctx PathContext) Config {
	if ret, ok := ctx.Config().(Config); ok {
		return ret
	}
	panic("Paths may only be used on Soong builds")
}

// reportPathError will register an error with the attached context. It
// attempts ctx.ModuleErrorf for a better error message first, then falls
// back to ctx.Errorf.
func reportPathError(ctx PathContext, format string, args ...interface{}) {
	if mctx, ok := ctx.(moduleErrorf); ok {
		mctx.ModuleErrorf(format, args...)
	} else if ectx, ok := ctx.(errorfContext); ok {
		ectx.Errorf(format, args...)
	} else {
		panic(fmt.Sprintf(format, args...))
	}
}

type Path interface {
	// Returns the path in string form
	String() string

	// Ext returns the extension of the last element of the path
	Ext() string

	// Base returns the last element of the path
	Base() string

	// Rel returns the portion of the path relative to the directory it was created from.  For
	// example, Rel on a PathsForModuleSrc would return the path relative to the module source
	// directory.
	Rel() string
}

// WritablePath is a type of path that can be used as an output for build rules.
type WritablePath interface {
	Path

	// the writablePath method doesn't directly do anything,
	// but it allows a struct to distinguish between whether or not it implements the WritablePath interface
	writablePath()
}

type genPathProvider interface {
	genPathWithExt(ctx ModuleContext, subdir, ext string) ModuleGenPath
}
type objPathProvider interface {
	objPathWithExt(ctx ModuleContext, subdir, ext string) ModuleObjPath
}
type resPathProvider interface {
	resPathWithName(ctx ModuleContext, name string) ModuleResPath
}

// GenPathWithExt derives a new file path in ctx's generated sources directory
// from the current path, but with the new extension.
func GenPathWithExt(ctx ModuleContext, subdir string, p Path, ext string) ModuleGenPath {
	if path, ok := p.(genPathProvider); ok {
		return path.genPathWithExt(ctx, subdir, ext)
	}
	reportPathError(ctx, "Tried to create generated file from unsupported path: %s(%s)", reflect.TypeOf(p).Name(), p)
	return PathForModuleGen(ctx)
}

// ObjPathWithExt derives a new file path in ctx's object directory from the
// current path, but with the new extension.
func ObjPathWithExt(ctx ModuleContext, subdir string, p Path, ext string) ModuleObjPath {
	if path, ok := p.(objPathProvider); ok {
		return path.objPathWithExt(ctx, subdir, ext)
	}
	reportPathError(ctx, "Tried to create object file from unsupported path: %s (%s)", reflect.TypeOf(p).Name(), p)
	return PathForModuleObj(ctx)
}

// ResPathWithName derives a new path in ctx's output resource directory, using
// the current path to create the directory name, and the `name` argument for
// the filename.
func ResPathWithName(ctx ModuleContext, p Path, name string) ModuleResPath {
	if path, ok := p.(resPathProvider); ok {
		return path.resPathWithName(ctx, name)
	}
	reportPathError(ctx, "Tried to create res file from unsupported path: %s (%s)", reflect.TypeOf(p).Name(), p)
	return PathForModuleRes(ctx)
}

// OptionalPath is a container that may or may not contain a valid Path.
type OptionalPath struct {
	valid bool
	path  Path
}

// OptionalPathForPath returns an OptionalPath containing the path.
func OptionalPathForPath(path Path) OptionalPath {
	if path == nil {
		return OptionalPath{}
	}
	return OptionalPath{valid: true, path: path}
}

// Valid returns whether there is a valid path
func (p OptionalPath) Valid() bool {
	return p.valid
}

// Path returns the Path embedded in this OptionalPath. You must be sure that
// there is a valid path, since this method will panic if there is not.
func (p OptionalPath) Path() Path {
	if !p.valid {
		panic("Requesting an invalid path")
	}
	return p.path
}

// String returns the string version of the Path, or "" if it isn't valid.
func (p OptionalPath) String() string {
	if p.valid {
		return p.path.String()
	} else {
		return ""
	}
}

// Paths is a slice of Path objects, with helpers to operate on the collection.
type Paths []Path

// PathsForSource returns Paths rooted from SrcDir
func PathsForSource(ctx PathContext, paths []string) Paths {
	if pathConfig(ctx).AllowMissingDependencies() {
		if modCtx, ok := ctx.(ModuleContext); ok {
			ret := make(Paths, 0, len(paths))
			intermediates := filepath.Join(modCtx.ModuleDir(), modCtx.ModuleName(), modCtx.ModuleSubDir(), "missing")
			for _, path := range paths {
				p := ExistentPathForSource(ctx, intermediates, path)
				if p.Valid() {
					ret = append(ret, p.Path())
				} else {
					modCtx.AddMissingDependencies([]string{path})
				}
			}
			return ret
		}
	}
	ret := make(Paths, len(paths))
	for i, path := range paths {
		ret[i] = PathForSource(ctx, path)
	}
	return ret
}

// ExistentPathsForSources returns a list of Paths rooted from SrcDir that are
// found in the tree. If any are not found, they are omitted from the list,
// and dependencies are added so that we're re-run when they are added.
func ExistentPathsForSources(ctx PathContext, intermediates string, paths []string) Paths {
	ret := make(Paths, 0, len(paths))
	for _, path := range paths {
		p := ExistentPathForSource(ctx, intermediates, path)
		if p.Valid() {
			ret = append(ret, p.Path())
		}
	}
	return ret
}

// PathsForModuleSrc returns Paths rooted from the module's local source
// directory
func PathsForModuleSrc(ctx ModuleContext, paths []string) Paths {
	ret := make(Paths, len(paths))
	for i, path := range paths {
		ret[i] = PathForModuleSrc(ctx, path)
	}
	return ret
}

// pathsForModuleSrcFromFullPath returns Paths rooted from the module's local
// source directory, but strip the local source directory from the beginning of
// each string.
func pathsForModuleSrcFromFullPath(ctx ModuleContext, paths []string) Paths {
	prefix := filepath.Join(ctx.AConfig().srcDir, ctx.ModuleDir()) + "/"
	ret := make(Paths, 0, len(paths))
	for _, p := range paths {
		path := filepath.Clean(p)
		if !strings.HasPrefix(path, prefix) {
			reportPathError(ctx, "Path '%s' is not in module source directory '%s'", p, prefix)
			continue
		}
		ret = append(ret, PathForModuleSrc(ctx, path[len(prefix):]))
	}
	return ret
}

// PathsWithOptionalDefaultForModuleSrc returns Paths rooted from the module's
// local source directory. If none are provided, use the default if it exists.
func PathsWithOptionalDefaultForModuleSrc(ctx ModuleContext, input []string, def string) Paths {
	if len(input) > 0 {
		return PathsForModuleSrc(ctx, input)
	}
	// Use Glob so that if the default doesn't exist, a dependency is added so that when it
	// is created, we're run again.
	path := filepath.Join(ctx.AConfig().srcDir, ctx.ModuleDir(), def)
	return ctx.Glob(path, []string{})
}

// Strings returns the Paths in string form
func (p Paths) Strings() []string {
	if p == nil {
		return nil
	}
	ret := make([]string, len(p))
	for i, path := range p {
		ret[i] = path.String()
	}
	return ret
}

// WritablePaths is a slice of WritablePaths, used for multiple outputs.
type WritablePaths []WritablePath

// Strings returns the string forms of the writable paths.
func (p WritablePaths) Strings() []string {
	if p == nil {
		return nil
	}
	ret := make([]string, len(p))
	for i, path := range p {
		ret[i] = path.String()
	}
	return ret
}

type basePath struct {
	path   string
	config Config
	rel    string
}

func (p basePath) Ext() string {
	return filepath.Ext(p.path)
}

func (p basePath) Base() string {
	return filepath.Base(p.path)
}

func (p basePath) Rel() string {
	if p.rel != "" {
		return p.rel
	}
	return p.path
}

// SourcePath is a Path representing a file path rooted from SrcDir
type SourcePath struct {
	basePath
}

var _ Path = SourcePath{}

// safePathForSource is for paths that we expect are safe -- only for use by go
// code that is embedding ninja variables in paths
func safePathForSource(ctx PathContext, path string) SourcePath {
	p := validateSafePath(ctx, path)
	ret := SourcePath{basePath{p, pathConfig(ctx), ""}}

	abs, err := filepath.Abs(ret.String())
	if err != nil {
		reportPathError(ctx, "%s", err.Error())
		return ret
	}
	buildroot, err := filepath.Abs(pathConfig(ctx).buildDir)
	if err != nil {
		reportPathError(ctx, "%s", err.Error())
		return ret
	}
	if strings.HasPrefix(abs, buildroot) {
		reportPathError(ctx, "source path %s is in output", abs)
		return ret
	}

	return ret
}

// PathForSource joins the provided path components and validates that the result
// neither escapes the source dir nor is in the out dir.
// On error, it will return a usable, but invalid SourcePath, and report a ModuleError.
func PathForSource(ctx PathContext, pathComponents ...string) SourcePath {
	p := validatePath(ctx, pathComponents...)
	ret := SourcePath{basePath{p, pathConfig(ctx), ""}}

	abs, err := filepath.Abs(ret.String())
	if err != nil {
		reportPathError(ctx, "%s", err.Error())
		return ret
	}
	buildroot, err := filepath.Abs(pathConfig(ctx).buildDir)
	if err != nil {
		reportPathError(ctx, "%s", err.Error())
		return ret
	}
	if strings.HasPrefix(abs, buildroot) {
		reportPathError(ctx, "source path %s is in output", abs)
		return ret
	}

	if exists, _, err := ctx.Fs().Exists(ret.String()); err != nil {
		reportPathError(ctx, "%s: %s", ret, err.Error())
	} else if !exists {
		reportPathError(ctx, "source path %s does not exist", ret)
	}
	return ret
}

// ExistentPathForSource returns an OptionalPath with the SourcePath if the
// path exists, or an empty OptionalPath if it doesn't exist. Dependencies are added
// so that the ninja file will be regenerated if the state of the path changes.
func ExistentPathForSource(ctx PathContext, intermediates string, pathComponents ...string) OptionalPath {
	if len(pathComponents) == 0 {
		// For when someone forgets the 'intermediates' argument
		panic("Missing path(s)")
	}

	p := validatePath(ctx, pathComponents...)
	path := SourcePath{basePath{p, pathConfig(ctx), ""}}

	abs, err := filepath.Abs(path.String())
	if err != nil {
		reportPathError(ctx, "%s", err.Error())
		return OptionalPath{}
	}
	buildroot, err := filepath.Abs(pathConfig(ctx).buildDir)
	if err != nil {
		reportPathError(ctx, "%s", err.Error())
		return OptionalPath{}
	}
	if strings.HasPrefix(abs, buildroot) {
		reportPathError(ctx, "source path %s is in output", abs)
		return OptionalPath{}
	}

	if pathtools.IsGlob(path.String()) {
		reportPathError(ctx, "path may not contain a glob: %s", path.String())
		return OptionalPath{}
	}

	if gctx, ok := ctx.(PathGlobContext); ok {
		// Use glob to produce proper dependencies, even though we only want
		// a single file.
		files, err := gctx.GlobWithDeps(path.String(), nil)
		if err != nil {
			reportPathError(ctx, "glob: %s", err.Error())
			return OptionalPath{}
		}

		if len(files) == 0 {
			return OptionalPath{}
		}
	} else {
		// We cannot add build statements in this context, so we fall back to
		// AddNinjaFileDeps
		files, dirs, err := pathtools.Glob(path.String(), nil)
		if err != nil {
			reportPathError(ctx, "glob: %s", err.Error())
			return OptionalPath{}
		}

		ctx.AddNinjaFileDeps(dirs...)

		if len(files) == 0 {
			return OptionalPath{}
		}

		ctx.AddNinjaFileDeps(path.String())
	}
	return OptionalPathForPath(path)
}

func (p SourcePath) String() string {
	return filepath.Join(p.config.srcDir, p.path)
}

// Join creates a new SourcePath with paths... joined with the current path. The
// provided paths... may not use '..' to escape from the current path.
func (p SourcePath) Join(ctx PathContext, paths ...string) SourcePath {
	path := validatePath(ctx, paths...)
	return PathForSource(ctx, p.path, path)
}

// OverlayPath returns the overlay for `path' if it exists. This assumes that the
// SourcePath is the path to a resource overlay directory.
func (p SourcePath) OverlayPath(ctx ModuleContext, path Path) OptionalPath {
	var relDir string
	if moduleSrcPath, ok := path.(ModuleSrcPath); ok {
		relDir = moduleSrcPath.path
	} else if srcPath, ok := path.(SourcePath); ok {
		relDir = srcPath.path
	} else {
		reportPathError(ctx, "Cannot find relative path for %s(%s)", reflect.TypeOf(path).Name(), path)
		return OptionalPath{}
	}
	dir := filepath.Join(p.config.srcDir, p.path, relDir)
	// Use Glob so that we are run again if the directory is added.
	if pathtools.IsGlob(dir) {
		reportPathError(ctx, "Path may not contain a glob: %s", dir)
	}
	paths, err := ctx.GlobWithDeps(dir, []string{})
	if err != nil {
		reportPathError(ctx, "glob: %s", err.Error())
		return OptionalPath{}
	}
	if len(paths) == 0 {
		return OptionalPath{}
	}
	relPath, err := filepath.Rel(p.config.srcDir, paths[0])
	if err != nil {
		reportPathError(ctx, "%s", err.Error())
		return OptionalPath{}
	}
	return OptionalPathForPath(PathForSource(ctx, relPath))
}

// OutputPath is a Path representing a file path rooted from the build directory
type OutputPath struct {
	basePath
}

var _ Path = OutputPath{}

// PathForOutput joins the provided paths and returns an OutputPath that is
// validated to not escape the build dir.
// On error, it will return a usable, but invalid OutputPath, and report a ModuleError.
func PathForOutput(ctx PathContext, pathComponents ...string) OutputPath {
	path := validatePath(ctx, pathComponents...)
	return OutputPath{basePath{path, pathConfig(ctx), ""}}
}

func (p OutputPath) writablePath() {}

func (p OutputPath) String() string {
	return filepath.Join(p.config.buildDir, p.path)
}

func (p OutputPath) RelPathString() string {
	return p.path
}

// Join creates a new OutputPath with paths... joined with the current path. The
// provided paths... may not use '..' to escape from the current path.
func (p OutputPath) Join(ctx PathContext, paths ...string) OutputPath {
	path := validatePath(ctx, paths...)
	return PathForOutput(ctx, p.path, path)
}

// PathForIntermediates returns an OutputPath representing the top-level
// intermediates directory.
func PathForIntermediates(ctx PathContext, paths ...string) OutputPath {
	path := validatePath(ctx, paths...)
	return PathForOutput(ctx, ".intermediates", path)
}

// ModuleSrcPath is a Path representing a file rooted from a module's local source dir
type ModuleSrcPath struct {
	SourcePath
}

var _ Path = ModuleSrcPath{}
var _ genPathProvider = ModuleSrcPath{}
var _ objPathProvider = ModuleSrcPath{}
var _ resPathProvider = ModuleSrcPath{}

// PathForModuleSrc returns a ModuleSrcPath representing the paths... under the
// module's local source directory.
func PathForModuleSrc(ctx ModuleContext, paths ...string) ModuleSrcPath {
	p := validatePath(ctx, paths...)
	path := ModuleSrcPath{PathForSource(ctx, ctx.ModuleDir(), p)}
	path.basePath.rel = p
	return path
}

// OptionalPathForModuleSrc returns an OptionalPath. The OptionalPath contains a
// valid path if p is non-nil.
func OptionalPathForModuleSrc(ctx ModuleContext, p *string) OptionalPath {
	if p == nil {
		return OptionalPath{}
	}
	return OptionalPathForPath(PathForModuleSrc(ctx, *p))
}

func (p ModuleSrcPath) genPathWithExt(ctx ModuleContext, subdir, ext string) ModuleGenPath {
	return PathForModuleGen(ctx, subdir, pathtools.ReplaceExtension(p.path, ext))
}

func (p ModuleSrcPath) objPathWithExt(ctx ModuleContext, subdir, ext string) ModuleObjPath {
	return PathForModuleObj(ctx, subdir, pathtools.ReplaceExtension(p.path, ext))
}

func (p ModuleSrcPath) resPathWithName(ctx ModuleContext, name string) ModuleResPath {
	// TODO: Use full directory if the new ctx is not the current ctx?
	return PathForModuleRes(ctx, p.path, name)
}

func (p ModuleSrcPath) WithSubDir(ctx ModuleContext, subdir string) ModuleSrcPath {
	subdir = PathForModuleSrc(ctx, subdir).String()
	var err error
	rel, err := filepath.Rel(subdir, p.path)
	if err != nil {
		ctx.ModuleErrorf("source file %q is not under path %q", p.path, subdir)
		return p
	}
	p.rel = rel
	return p
}

// ModuleOutPath is a Path representing a module's output directory.
type ModuleOutPath struct {
	OutputPath
}

var _ Path = ModuleOutPath{}

// PathForVndkRefDump returns an OptionalPath representing the path of the reference
// abi dump for the given module. This is not guaranteed to be valid.
func PathForVndkRefAbiDump(ctx ModuleContext, version, fileName string, vndkOrNdk, isSourceDump bool) OptionalPath {
	archName := ctx.Arch().ArchType.Name
	var sourceOrBinaryDir string
	var vndkOrNdkDir string
	var ext string
	if isSourceDump {
		ext = ".lsdump.gz"
		sourceOrBinaryDir = "source-based"
	} else {
		ext = ".bdump.gz"
		sourceOrBinaryDir = "binary-based"
	}
	if vndkOrNdk {
		vndkOrNdkDir = "vndk"
	} else {
		vndkOrNdkDir = "ndk"
	}
	refDumpFileStr := "prebuilts/abi-dumps/" + vndkOrNdkDir + "/" + version + "/" +
		archName + "/" + sourceOrBinaryDir + "/" + fileName + ext
	return ExistentPathForSource(ctx, "", refDumpFileStr)
}

// PathForModuleOut returns a Path representing the paths... under the module's
// output directory.
func PathForModuleOut(ctx ModuleContext, paths ...string) ModuleOutPath {
	p := validatePath(ctx, paths...)
	return ModuleOutPath{PathForOutput(ctx, ".intermediates", ctx.ModuleDir(), ctx.ModuleName(), ctx.ModuleSubDir(), p)}
}

// ModuleGenPath is a Path representing the 'gen' directory in a module's output
// directory. Mainly used for generated sources.
type ModuleGenPath struct {
	ModuleOutPath
	path string
}

var _ Path = ModuleGenPath{}
var _ genPathProvider = ModuleGenPath{}
var _ objPathProvider = ModuleGenPath{}

// PathForModuleGen returns a Path representing the paths... under the module's
// `gen' directory.
func PathForModuleGen(ctx ModuleContext, paths ...string) ModuleGenPath {
	p := validatePath(ctx, paths...)
	return ModuleGenPath{
		PathForModuleOut(ctx, "gen", p),
		p,
	}
}

func (p ModuleGenPath) genPathWithExt(ctx ModuleContext, subdir, ext string) ModuleGenPath {
	// TODO: make a different path for local vs remote generated files?
	return PathForModuleGen(ctx, subdir, pathtools.ReplaceExtension(p.path, ext))
}

func (p ModuleGenPath) objPathWithExt(ctx ModuleContext, subdir, ext string) ModuleObjPath {
	return PathForModuleObj(ctx, subdir, pathtools.ReplaceExtension(p.path, ext))
}

// ModuleObjPath is a Path representing the 'obj' directory in a module's output
// directory. Used for compiled objects.
type ModuleObjPath struct {
	ModuleOutPath
}

var _ Path = ModuleObjPath{}

// PathForModuleObj returns a Path representing the paths... under the module's
// 'obj' directory.
func PathForModuleObj(ctx ModuleContext, pathComponents ...string) ModuleObjPath {
	p := validatePath(ctx, pathComponents...)
	return ModuleObjPath{PathForModuleOut(ctx, "obj", p)}
}

// ModuleResPath is a a Path representing the 'res' directory in a module's
// output directory.
type ModuleResPath struct {
	ModuleOutPath
}

var _ Path = ModuleResPath{}

// PathForModuleRes returns a Path representing the paths... under the module's
// 'res' directory.
func PathForModuleRes(ctx ModuleContext, pathComponents ...string) ModuleResPath {
	p := validatePath(ctx, pathComponents...)
	return ModuleResPath{PathForModuleOut(ctx, "res", p)}
}

// PathForModuleInstall returns a Path representing the install path for the
// module appended with paths...
func PathForModuleInstall(ctx ModuleInstallPathContext, pathComponents ...string) OutputPath {
	var outPaths []string
	if ctx.Device() {
		var partition string
		if ctx.InstallInData() {
			partition = "data"
		} else if ctx.Vendor() {
			partition = ctx.DeviceConfig().VendorPath()
		} else {
			partition = "system"
		}

		if ctx.InstallInSanitizerDir() {
			partition = "data/asan/" + partition
		}
		// MTK customization
		outPaths = []string{"target", "product", ctx.AConfig().MtkTargetProjectName(), partition}
	} else {
		outPaths = []string{"host", ctx.Os().String() + "-x86"}
	}
	if ctx.Debug() {
		outPaths = append([]string{"debug"}, outPaths...)
	}
	outPaths = append(outPaths, pathComponents...)
	return PathForOutput(ctx, outPaths...)
}

// validateSafePath validates a path that we trust (may contain ninja variables).
// Ensures that each path component does not attempt to leave its component.
func validateSafePath(ctx PathContext, pathComponents ...string) string {
	for _, path := range pathComponents {
		path := filepath.Clean(path)
		if path == ".." || strings.HasPrefix(path, "../") || strings.HasPrefix(path, "/") {
			reportPathError(ctx, "Path is outside directory: %s", path)
			return ""
		}
	}
	// TODO: filepath.Join isn't necessarily correct with embedded ninja
	// variables. '..' may remove the entire ninja variable, even if it
	// will be expanded to multiple nested directories.
	return filepath.Join(pathComponents...)
}

// validatePath validates that a path does not include ninja variables, and that
// each path component does not attempt to leave its component. Returns a joined
// version of each path component.
func validatePath(ctx PathContext, pathComponents ...string) string {
	for _, path := range pathComponents {
		if strings.Contains(path, "$") {
			reportPathError(ctx, "Path contains invalid character($): %s", path)
			return ""
		}
	}
	return validateSafePath(ctx, pathComponents...)
}

type testPath struct {
	basePath
}

func (p testPath) String() string {
	return p.path
}

func PathForTesting(paths ...string) Path {
	p := validateSafePath(nil, paths...)
	return testPath{basePath{path: p, rel: p}}
}

func PathsForTesting(strs []string) Paths {
	p := make(Paths, len(strs))
	for i, s := range strs {
		p[i] = PathForTesting(s)
	}

	return p
}
