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
	"runtime"
	"sort"
	"strings"
)

func JoinWithPrefix(strs []string, prefix string) string {
	if len(strs) == 0 {
		return ""
	}

	if len(strs) == 1 {
		return prefix + strs[0]
	}

	n := len(" ") * (len(strs) - 1)
	for _, s := range strs {
		n += len(prefix) + len(s)
	}

	ret := make([]byte, 0, n)
	for i, s := range strs {
		if i != 0 {
			ret = append(ret, ' ')
		}
		ret = append(ret, prefix...)
		ret = append(ret, s...)
	}
	return string(ret)
}

func sortedKeys(m map[string][]string) []string {
	s := make([]string, 0, len(m))
	for k := range m {
		s = append(s, k)
	}
	sort.Strings(s)
	return s
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

func prefixInList(s string, list []string) bool {
	for _, prefix := range list {
		if strings.HasPrefix(s, prefix) {
			return true
		}
	}
	return false
}

// checkCalledFromInit panics if a Go package's init function is not on the
// call stack.
func checkCalledFromInit() {
	for skip := 3; ; skip++ {
		_, funcName, ok := callerName(skip)
		if !ok {
			panic("not called from an init func")
		}

		if funcName == "init" || strings.HasPrefix(funcName, "init·") {
			return
		}
	}
}

// callerName returns the package path and function name of the calling
// function.  The skip argument has the same meaning as the skip argument of
// runtime.Callers.
func callerName(skip int) (pkgPath, funcName string, ok bool) {
	var pc [1]uintptr
	n := runtime.Callers(skip+1, pc[:])
	if n != 1 {
		return "", "", false
	}

	f := runtime.FuncForPC(pc[0])
	fullName := f.Name()

	lastDotIndex := strings.LastIndex(fullName, ".")
	if lastDotIndex == -1 {
		panic("unable to distinguish function name from package")
	}

	if fullName[lastDotIndex-1] == ')' {
		// The caller is a method on some type, so it's name looks like
		// "pkg/path.(type).method".  We need to go back one dot farther to get
		// to the package name.
		lastDotIndex = strings.LastIndex(fullName[:lastDotIndex], ".")
	}

	pkgPath = fullName[:lastDotIndex]
	funcName = fullName[lastDotIndex+1:]
	ok = true
	return
}
