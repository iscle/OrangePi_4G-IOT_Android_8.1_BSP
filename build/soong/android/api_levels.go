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

package android

import (
	"encoding/json"
	"path/filepath"

	"github.com/google/blueprint"
)

func init() {
	RegisterSingletonType("api_levels", ApiLevelsSingleton)
}

func ApiLevelsSingleton() blueprint.Singleton {
	return &apiLevelsSingleton{}
}

type apiLevelsSingleton struct{}

func createApiLevelsJson(ctx blueprint.SingletonContext, file string,
	apiLevelsMap map[string]int) {

	jsonStr, err := json.Marshal(apiLevelsMap)
	if err != nil {
		ctx.Errorf(err.Error())
	}

	ctx.Build(pctx, blueprint.BuildParams{
		Rule:        WriteFile,
		Description: "generate " + filepath.Base(file),
		Outputs:     []string{file},
		Args: map[string]string{
			"content": string(jsonStr[:]),
		},
	})
}

func GetApiLevelsJson(ctx PathContext) Path {
	return PathForOutput(ctx, "api_levels.json")
}

func (a *apiLevelsSingleton) GenerateBuildActions(ctx blueprint.SingletonContext) {
	baseApiLevel := 9000
	apiLevelsMap := map[string]int{}
	for i, codename := range ctx.Config().(Config).PlatformVersionAllCodenames() {
		apiLevelsMap[codename] = baseApiLevel + i
	}

	apiLevelsJson := GetApiLevelsJson(ctx)
	createApiLevelsJson(ctx, apiLevelsJson.String(), apiLevelsMap)
}
