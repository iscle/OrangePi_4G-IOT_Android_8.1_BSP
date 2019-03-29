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

// env implements the environment JSON file handling for the soong_env command line tool run before
// the builder and for the env writer in the builder.
package env

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"os"
	"sort"
)

type envFileEntry struct{ Key, Value string }
type envFileData []envFileEntry

func WriteEnvFile(filename string, envDeps map[string]string) error {
	contents := make(envFileData, 0, len(envDeps))
	for key, value := range envDeps {
		contents = append(contents, envFileEntry{key, value})
	}

	sort.Sort(contents)

	data, err := json.MarshalIndent(contents, "", "    ")
	if err != nil {
		return err
	}

	data = append(data, '\n')

	err = ioutil.WriteFile(filename, data, 0664)
	if err != nil {
		return err
	}

	return nil
}

func StaleEnvFile(filename string) (bool, error) {
	data, err := ioutil.ReadFile(filename)
	if err != nil {
		return true, err
	}

	var contents envFileData

	err = json.Unmarshal(data, &contents)
	if err != nil {
		return true, err
	}

	var changed []string
	for _, entry := range contents {
		key := entry.Key
		old := entry.Value
		cur := os.Getenv(key)
		if old != cur {
			changed = append(changed, fmt.Sprintf("%s (%q -> %q)", key, old, cur))
		}
	}

	if len(changed) > 0 {
		fmt.Printf("environment variables changed value:\n")
		for _, s := range changed {
			fmt.Printf("   %s\n", s)
		}
		return true, nil
	}

	return false, nil
}

func (e envFileData) Len() int {
	return len(e)
}

func (e envFileData) Less(i, j int) bool {
	return e[i].Key < e[j].Key
}

func (e envFileData) Swap(i, j int) {
	e[i], e[j] = e[j], e[i]
}
