// Copyright 2016 Google Inc. All rights reserved.
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

package cc

import (
	"path/filepath"
	"runtime"
	"strings"

	"android/soong/android"
)

type TestProperties struct {
	// if set, build against the gtest library. Defaults to true.
	Gtest *bool
}

type TestBinaryProperties struct {
	// Create a separate binary for each source file.  Useful when there is
	// global state that can not be torn down and reset between each test suite.
	Test_per_src *bool

	// Disables the creation of a test-specific directory when used with
	// relative_install_path. Useful if several tests need to be in the same
	// directory, but test_per_src doesn't work.
	No_named_install_directory *bool

	// list of files or filegroup modules that provide data that should be installed alongside
	// the test
	Data []string

	// list of compatibility suites (for example "cts", "vts") that the module should be
	// installed into.
	Test_suites []string
}

func init() {
	android.RegisterModuleType("cc_test", testFactory)
	android.RegisterModuleType("cc_test_library", testLibraryFactory)
	android.RegisterModuleType("cc_benchmark", benchmarkFactory)
	android.RegisterModuleType("cc_test_host", testHostFactory)
	android.RegisterModuleType("cc_benchmark_host", benchmarkHostFactory)
}

// Module factory for tests
func testFactory() android.Module {
	module := NewTest(android.HostAndDeviceSupported)
	return module.Init()
}

// Module factory for test libraries
func testLibraryFactory() android.Module {
	module := NewTestLibrary(android.HostAndDeviceSupported)
	return module.Init()
}

// Module factory for benchmarks
func benchmarkFactory() android.Module {
	module := NewBenchmark(android.HostAndDeviceSupported)
	return module.Init()
}

// Module factory for host tests
func testHostFactory() android.Module {
	module := NewTest(android.HostSupported)
	return module.Init()
}

// Module factory for host benchmarks
func benchmarkHostFactory() android.Module {
	module := NewBenchmark(android.HostSupported)
	return module.Init()
}

type testPerSrc interface {
	testPerSrc() bool
	srcs() []string
	setSrc(string, string)
}

func (test *testBinary) testPerSrc() bool {
	return Bool(test.Properties.Test_per_src)
}

func (test *testBinary) srcs() []string {
	return test.baseCompiler.Properties.Srcs
}

func (test *testBinary) setSrc(name, src string) {
	test.baseCompiler.Properties.Srcs = []string{src}
	test.binaryDecorator.Properties.Stem = name
}

var _ testPerSrc = (*testBinary)(nil)

func testPerSrcMutator(mctx android.BottomUpMutatorContext) {
	if m, ok := mctx.Module().(*Module); ok {
		if test, ok := m.linker.(testPerSrc); ok {
			if test.testPerSrc() && len(test.srcs()) > 0 {
				testNames := make([]string, len(test.srcs()))
				for i, src := range test.srcs() {
					testNames[i] = strings.TrimSuffix(filepath.Base(src), filepath.Ext(src))
				}
				tests := mctx.CreateLocalVariations(testNames...)
				for i, src := range test.srcs() {
					tests[i].(*Module).linker.(testPerSrc).setSrc(testNames[i], src)
				}
			}
		}
	}
}

type testDecorator struct {
	Properties TestProperties
	linker     *baseLinker
}

func (test *testDecorator) gtest() bool {
	return test.Properties.Gtest == nil || *test.Properties.Gtest == true
}

func (test *testDecorator) linkerFlags(ctx ModuleContext, flags Flags) Flags {
	if !test.gtest() {
		return flags
	}

	flags.CFlags = append(flags.CFlags, "-DGTEST_HAS_STD_STRING")
	if ctx.Host() {
		flags.CFlags = append(flags.CFlags, "-O0", "-g")

		switch ctx.Os() {
		case android.Windows:
			flags.CFlags = append(flags.CFlags, "-DGTEST_OS_WINDOWS")
		case android.Linux:
			flags.CFlags = append(flags.CFlags, "-DGTEST_OS_LINUX")
			flags.LdFlags = append(flags.LdFlags, "-lpthread")
		case android.Darwin:
			flags.CFlags = append(flags.CFlags, "-DGTEST_OS_MAC")
			flags.LdFlags = append(flags.LdFlags, "-lpthread")
		}
	} else {
		flags.CFlags = append(flags.CFlags, "-DGTEST_OS_LINUX_ANDROID")
	}

	return flags
}

func (test *testDecorator) linkerDeps(ctx BaseModuleContext, deps Deps) Deps {
	if test.gtest() {
		if ctx.sdk() && ctx.Device() {
			switch ctx.selectedStl() {
			case "ndk_libc++_shared", "ndk_libc++_static":
				deps.StaticLibs = append(deps.StaticLibs, "libgtest_main_ndk_libcxx", "libgtest_ndk_libcxx")
			case "ndk_libgnustl_static":
				deps.StaticLibs = append(deps.StaticLibs, "libgtest_main_ndk_gnustl", "libgtest_ndk_gnustl")
			default:
				deps.StaticLibs = append(deps.StaticLibs, "libgtest_main_ndk", "libgtest_ndk")
			}
		} else {
			deps.StaticLibs = append(deps.StaticLibs, "libgtest_main", "libgtest")
		}
	}

	return deps
}

func (test *testDecorator) linkerInit(ctx BaseModuleContext, linker *baseLinker) {
	runpath := "../../lib"
	if ctx.toolchain().Is64Bit() {
		runpath += "64"
	}
	linker.dynamicProperties.RunPaths = append(linker.dynamicProperties.RunPaths, runpath)
}

func (test *testDecorator) linkerProps() []interface{} {
	return []interface{}{&test.Properties}
}

func NewTestInstaller() *baseInstaller {
	return NewBaseInstaller("nativetest", "nativetest64", InstallInData)
}

type testBinary struct {
	testDecorator
	*binaryDecorator
	*baseCompiler
	Properties TestBinaryProperties
	data       android.Paths
}

func (test *testBinary) linkerProps() []interface{} {
	props := append(test.testDecorator.linkerProps(), test.binaryDecorator.linkerProps()...)
	props = append(props, &test.Properties)
	return props
}

func (test *testBinary) linkerInit(ctx BaseModuleContext) {
	test.testDecorator.linkerInit(ctx, test.binaryDecorator.baseLinker)
	test.binaryDecorator.linkerInit(ctx)
}

func (test *testBinary) linkerDeps(ctx DepsContext, deps Deps) Deps {
	android.ExtractSourcesDeps(ctx, test.Properties.Data)

	deps = test.testDecorator.linkerDeps(ctx, deps)
	deps = test.binaryDecorator.linkerDeps(ctx, deps)
	return deps
}

func (test *testBinary) linkerFlags(ctx ModuleContext, flags Flags) Flags {
	flags = test.binaryDecorator.linkerFlags(ctx, flags)
	flags = test.testDecorator.linkerFlags(ctx, flags)
	return flags
}

func (test *testBinary) install(ctx ModuleContext, file android.Path) {
	test.data = ctx.ExpandSources(test.Properties.Data, nil)

	test.binaryDecorator.baseInstaller.dir = "nativetest"
	test.binaryDecorator.baseInstaller.dir64 = "nativetest64"

	if !Bool(test.Properties.No_named_install_directory) {
		test.binaryDecorator.baseInstaller.relative = ctx.ModuleName()
	} else if test.binaryDecorator.baseInstaller.Properties.Relative_install_path == "" {
		ctx.PropertyErrorf("no_named_install_directory", "Module install directory may only be disabled if relative_install_path is set")
	}

	test.binaryDecorator.baseInstaller.install(ctx, file)
}

func NewTest(hod android.HostOrDeviceSupported) *Module {
	module, binary := NewBinary(hod)
	module.multilib = android.MultilibBoth
	binary.baseInstaller = NewTestInstaller()

	test := &testBinary{
		testDecorator: testDecorator{
			linker: binary.baseLinker,
		},
		binaryDecorator: binary,
		baseCompiler:    NewBaseCompiler(),
	}
	module.compiler = test
	module.linker = test
	module.installer = test
	return module
}

type testLibrary struct {
	testDecorator
	*libraryDecorator
}

func (test *testLibrary) linkerProps() []interface{} {
	return append(test.testDecorator.linkerProps(), test.libraryDecorator.linkerProps()...)
}

func (test *testLibrary) linkerInit(ctx BaseModuleContext) {
	test.testDecorator.linkerInit(ctx, test.libraryDecorator.baseLinker)
	test.libraryDecorator.linkerInit(ctx)
}

func (test *testLibrary) linkerDeps(ctx DepsContext, deps Deps) Deps {
	deps = test.testDecorator.linkerDeps(ctx, deps)
	deps = test.libraryDecorator.linkerDeps(ctx, deps)
	return deps
}

func (test *testLibrary) linkerFlags(ctx ModuleContext, flags Flags) Flags {
	flags = test.libraryDecorator.linkerFlags(ctx, flags)
	flags = test.testDecorator.linkerFlags(ctx, flags)
	return flags
}

func NewTestLibrary(hod android.HostOrDeviceSupported) *Module {
	module, library := NewLibrary(android.HostAndDeviceSupported)
	library.baseInstaller = NewTestInstaller()
	test := &testLibrary{
		testDecorator: testDecorator{
			linker: library.baseLinker,
		},
		libraryDecorator: library,
	}
	module.linker = test
	return module
}

type BenchmarkProperties struct {
	// list of files or filegroup modules that provide data that should be installed alongside
	// the test
	Data []string

	// list of compatibility suites (for example "cts", "vts") that the module should be
	// installed into.
	Test_suites []string
}

type benchmarkDecorator struct {
	*binaryDecorator
	Properties BenchmarkProperties
	data       android.Paths
}

func (benchmark *benchmarkDecorator) linkerInit(ctx BaseModuleContext) {
	runpath := "../../lib"
	if ctx.toolchain().Is64Bit() {
		runpath += "64"
	}
	benchmark.baseLinker.dynamicProperties.RunPaths = append(benchmark.baseLinker.dynamicProperties.RunPaths, runpath)
	benchmark.binaryDecorator.linkerInit(ctx)
}

func (benchmark *benchmarkDecorator) linkerProps() []interface{} {
	props := benchmark.binaryDecorator.linkerProps()
	props = append(props, &benchmark.Properties)
	return props
}

func (benchmark *benchmarkDecorator) linkerDeps(ctx DepsContext, deps Deps) Deps {
	android.ExtractSourcesDeps(ctx, benchmark.Properties.Data)
	deps = benchmark.binaryDecorator.linkerDeps(ctx, deps)
	deps.StaticLibs = append(deps.StaticLibs, "libgoogle-benchmark")
	return deps
}

func (benchmark *benchmarkDecorator) install(ctx ModuleContext, file android.Path) {
	benchmark.data = ctx.ExpandSources(benchmark.Properties.Data, nil)
	benchmark.binaryDecorator.baseInstaller.dir = filepath.Join("nativetest", ctx.ModuleName())
	benchmark.binaryDecorator.baseInstaller.dir64 = filepath.Join("nativetest64", ctx.ModuleName())
	benchmark.binaryDecorator.baseInstaller.install(ctx, file)
}

func NewBenchmark(hod android.HostOrDeviceSupported) *Module {
	// Benchmarks aren't supported on Darwin
	if runtime.GOOS == "darwin" {
		switch hod {
		case android.HostAndDeviceSupported:
			hod = android.DeviceSupported
		case android.HostSupported:
			hod = android.NeitherHostNorDeviceSupported
		}
	}

	module, binary := NewBinary(hod)
	module.multilib = android.MultilibBoth
	binary.baseInstaller = NewTestInstaller()

	benchmark := &benchmarkDecorator{
		binaryDecorator: binary,
	}
	module.linker = benchmark
	module.installer = benchmark
	return module
}
