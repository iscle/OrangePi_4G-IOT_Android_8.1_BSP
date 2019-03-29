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
	"path/filepath"
)

func runSoongBootstrap(ctx Context, config Config) {
	ctx.BeginTrace("bootstrap soong")
	defer ctx.EndTrace()

	cmd := Command(ctx, config, "soong bootstrap", "./bootstrap.bash")
	cmd.Environment.Set("BUILDDIR", config.SoongOutDir())
	cmd.Sandbox = soongSandbox
	cmd.Stdout = ctx.Stdout()
	cmd.Stderr = ctx.Stderr()
	cmd.RunOrFatal()
}

func runSoong(ctx Context, config Config) {
	ctx.BeginTrace("soong")
	defer ctx.EndTrace()

	cmd := Command(ctx, config, "soong",
		filepath.Join(config.SoongOutDir(), "soong"), "-w", "dupbuild=err")
	if config.IsVerbose() {
		cmd.Args = append(cmd.Args, "-v")
	}
	cmd.Environment.Set("SKIP_NINJA", "true")
	cmd.Sandbox = soongSandbox
	cmd.Stdin = ctx.Stdin()
	cmd.Stdout = ctx.Stdout()
	cmd.Stderr = ctx.Stderr()
	cmd.RunOrFatal()
}
