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
	"os"
	"strings"

	"android/soong/env"

	"github.com/google/blueprint"
)

// This file supports dependencies on environment variables.  During build manifest generation,
// any dependency on an environment variable is added to a list.  During the singleton phase
// a JSON file is written containing the current value of all used environment variables.
// The next time the top-level build script is run, it uses the soong_env executable to
// compare the contents of the environment variables, rewriting the file if necessary to cause
// a manifest regeneration.

var originalEnv map[string]string

func init() {
	originalEnv = make(map[string]string)
	for _, env := range os.Environ() {
		idx := strings.IndexRune(env, '=')
		if idx != -1 {
			originalEnv[env[:idx]] = env[idx+1:]
		}
	}
	os.Clearenv()
}

func EnvSingleton() blueprint.Singleton {
	return &envSingleton{}
}

type envSingleton struct{}

func (c *envSingleton) GenerateBuildActions(ctx blueprint.SingletonContext) {
	envDeps := ctx.Config().(Config).EnvDeps()

	envFile := PathForOutput(ctx, ".soong.environment")
	if ctx.Failed() {
		return
	}

	err := env.WriteEnvFile(envFile.String(), envDeps)
	if err != nil {
		ctx.Errorf(err.Error())
	}

	ctx.AddNinjaFileDeps(envFile.String())
}
