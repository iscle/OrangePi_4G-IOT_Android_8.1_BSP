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

package cc

import (
	"fmt"
	"regexp"
	"strings"

	"android/soong/android"
)

// Efficiently converts a list of include directories to a single string
// of cflags with -I prepended to each directory.
func includeDirsToFlags(dirs android.Paths) string {
	return android.JoinWithPrefix(dirs.Strings(), "-I")
}

func includeFilesToFlags(files android.Paths) string {
	return android.JoinWithPrefix(files.Strings(), "-include ")
}

func ldDirsToFlags(dirs []string) string {
	return android.JoinWithPrefix(dirs, "-L")
}

func libNamesToFlags(names []string) string {
	return android.JoinWithPrefix(names, "-l")
}

func indexList(s string, list []string) int {
	for i, l := range list {
		if l == s {
			return i
		}
	}

	return -1
}

func inList(s string, list []string) bool {
	return indexList(s, list) != -1
}

func filterList(list []string, filter []string) (remainder []string, filtered []string) {
	for _, l := range list {
		if inList(l, filter) {
			filtered = append(filtered, l)
		} else {
			remainder = append(remainder, l)
		}
	}

	return
}

func removeListFromList(list []string, filter_out []string) (result []string) {
	result = make([]string, 0, len(list))
	for _, l := range list {
		if !inList(l, filter_out) {
			result = append(result, l)
		}
	}
	return
}

func removeFromList(s string, list []string) (bool, []string) {
	i := indexList(s, list)
	if i != -1 {
		return true, append(list[:i], list[i+1:]...)
	} else {
		return false, list
	}
}

var libNameRegexp = regexp.MustCompile(`^lib(.*)$`)

func moduleToLibName(module string) (string, error) {
	matches := libNameRegexp.FindStringSubmatch(module)
	if matches == nil {
		return "", fmt.Errorf("Library module name %s does not start with lib", module)
	}
	return matches[1], nil
}

func flagsToBuilderFlags(in Flags) builderFlags {
	return builderFlags{
		globalFlags:   strings.Join(in.GlobalFlags, " "),
		arFlags:       strings.Join(in.ArFlags, " "),
		asFlags:       strings.Join(in.AsFlags, " "),
		cFlags:        strings.Join(in.CFlags, " "),
		toolingCFlags: strings.Join(in.ToolingCFlags, " "),
		conlyFlags:    strings.Join(in.ConlyFlags, " "),
		cppFlags:      strings.Join(in.CppFlags, " "),
		yaccFlags:     strings.Join(in.YaccFlags, " "),
		protoFlags:    strings.Join(in.protoFlags, " "),
		aidlFlags:     strings.Join(in.aidlFlags, " "),
		rsFlags:       strings.Join(in.rsFlags, " "),
		ldFlags:       strings.Join(in.LdFlags, " "),
		libFlags:      strings.Join(in.libFlags, " "),
		tidyFlags:     strings.Join(in.TidyFlags, " "),
		sAbiFlags:     strings.Join(in.SAbiFlags, " "),
		yasmFlags:     strings.Join(in.YasmFlags, " "),
		toolchain:     in.Toolchain,
		clang:         in.Clang,
		coverage:      in.Coverage,
		tidy:          in.Tidy,
		sAbiDump:      in.SAbiDump,

		systemIncludeFlags: strings.Join(in.SystemIncludeFlags, " "),

		groupStaticLibs: in.GroupStaticLibs,
	}
}

func addPrefix(list []string, prefix string) []string {
	for i := range list {
		list[i] = prefix + list[i]
	}
	return list
}

func addSuffix(list []string, suffix string) []string {
	for i := range list {
		list[i] = list[i] + suffix
	}
	return list
}
