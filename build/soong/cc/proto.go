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
	"github.com/google/blueprint"
	"github.com/google/blueprint/proptools"

	"android/soong/android"
)

func init() {
	pctx.HostBinToolVariable("protocCmd", "aprotoc")
}

var (
	proto = pctx.AndroidStaticRule("protoc",
		blueprint.RuleParams{
			Command:     "$protocCmd --cpp_out=$outDir $protoFlags $in",
			CommandDeps: []string{"$protocCmd"},
		}, "protoFlags", "outDir")
)

// TODO(ccross): protos are often used to communicate between multiple modules.  If the only
// way to convert a proto to source is to reference it as a source file, and external modules cannot
// reference source files in other modules, then every module that owns a proto file will need to
// export a library for every type of external user (lite vs. full, c vs. c++ vs. java).  It would
// be better to support a proto module type that exported a proto file along with some include dirs,
// and then external modules could depend on the proto module but use their own settings to
// generate the source.

func genProto(ctx android.ModuleContext, protoFile android.Path,
	protoFlags string) (android.ModuleGenPath, android.ModuleGenPath) {

	outFile := android.GenPathWithExt(ctx, "proto", protoFile, "pb.cc")
	headerFile := android.GenPathWithExt(ctx, "proto", protoFile, "pb.h")
	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:        proto,
		Description: "protoc " + protoFile.Rel(),
		Outputs:     android.WritablePaths{outFile, headerFile},
		Input:       protoFile,
		Args: map[string]string{
			"outDir":     protoDir(ctx).String(),
			"protoFlags": protoFlags,
		},
	})

	return outFile, headerFile
}

// protoDir returns the module's "gen/proto" directory
func protoDir(ctx android.ModuleContext) android.ModuleGenPath {
	return android.PathForModuleGen(ctx, "proto")
}

// protoSubDir returns the module's "gen/proto/path/to/module" directory
func protoSubDir(ctx android.ModuleContext) android.ModuleGenPath {
	return android.PathForModuleGen(ctx, "proto", ctx.ModuleDir())
}

type ProtoProperties struct {
	Proto struct {
		// Proto generator type (full, lite)
		Type *string `android:"arch_variant"`

		// Link statically against the protobuf runtime
		Static bool `android:"arch_variant"`

		// list of directories that will be added to the protoc include paths.
		Include_dirs []string

		// list of directories relative to the Android.bp file that will
		// be added to the protoc include paths.
		Local_include_dirs []string
	} `android:"arch_variant"`
}

func protoDeps(ctx BaseModuleContext, deps Deps, p *ProtoProperties) Deps {
	var lib string
	var static bool

	switch proptools.String(p.Proto.Type) {
	case "full":
		if ctx.sdk() {
			lib = "libprotobuf-cpp-full-ndk"
			static = true
		} else {
			lib = "libprotobuf-cpp-full"
		}
	case "lite", "":
		if ctx.sdk() {
			lib = "libprotobuf-cpp-lite-ndk"
			static = true
		} else {
			lib = "libprotobuf-cpp-lite"
			if p.Proto.Static {
				static = true
			}
		}
	default:
		ctx.PropertyErrorf("proto.type", "unknown proto type %q",
			proptools.String(p.Proto.Type))
	}

	if static {
		deps.StaticLibs = append(deps.StaticLibs, lib)
		deps.ReexportStaticLibHeaders = append(deps.ReexportStaticLibHeaders, lib)
	} else {
		deps.SharedLibs = append(deps.SharedLibs, lib)
		deps.ReexportSharedLibHeaders = append(deps.ReexportSharedLibHeaders, lib)
	}

	return deps
}

func protoFlags(ctx ModuleContext, flags Flags, p *ProtoProperties) Flags {
	flags.CFlags = append(flags.CFlags, "-DGOOGLE_PROTOBUF_NO_RTTI")
	flags.GlobalFlags = append(flags.GlobalFlags,
		"-I"+protoSubDir(ctx).String(),
		"-I"+protoDir(ctx).String(),
	)

	if len(p.Proto.Local_include_dirs) > 0 {
		localProtoIncludeDirs := android.PathsForModuleSrc(ctx, p.Proto.Local_include_dirs)
		flags.protoFlags = append(flags.protoFlags, includeDirsToFlags(localProtoIncludeDirs))
	}
	if len(p.Proto.Include_dirs) > 0 {
		rootProtoIncludeDirs := android.PathsForSource(ctx, p.Proto.Include_dirs)
		flags.protoFlags = append(flags.protoFlags, includeDirsToFlags(rootProtoIncludeDirs))
	}

	flags.protoFlags = append(flags.protoFlags, "-I .")

	return flags
}
