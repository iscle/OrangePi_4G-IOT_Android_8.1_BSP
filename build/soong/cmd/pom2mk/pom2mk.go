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

package main

import (
	"encoding/xml"
	"flag"
	"fmt"
	"io"
	"io/ioutil"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"text/template"

	"github.com/google/blueprint/proptools"
)

type RewriteNames []RewriteName
type RewriteName struct {
	regexp *regexp.Regexp
	repl   string
}

func (r *RewriteNames) String() string {
	return ""
}

func (r *RewriteNames) Set(v string) error {
	split := strings.SplitN(v, "=", 2)
	if len(split) != 2 {
		return fmt.Errorf("Must be in the form of <regex>=<replace>")
	}
	regex, err := regexp.Compile(split[0])
	if err != nil {
		return nil
	}
	*r = append(*r, RewriteName{
		regexp: regex,
		repl:   split[1],
	})
	return nil
}

func (r *RewriteNames) Rewrite(name string) string {
	for _, r := range *r {
		if r.regexp.MatchString(name) {
			return r.regexp.ReplaceAllString(name, r.repl)
		}
	}
	return name
}

var rewriteNames = RewriteNames{}

type ExtraDeps map[string][]string

func (d ExtraDeps) String() string {
	return ""
}

func (d ExtraDeps) Set(v string) error {
	split := strings.SplitN(v, "=", 2)
	if len(split) != 2 {
		return fmt.Errorf("Must be in the form of <module>=<module>[,<module>]")
	}
	d[split[0]] = strings.Split(split[1], ",")
	return nil
}

var extraDeps = make(ExtraDeps)

type Dependency struct {
	XMLName xml.Name `xml:"dependency"`

	GroupId    string `xml:"groupId"`
	ArtifactId string `xml:"artifactId"`
	Version    string `xml:"version"`
	Type       string `xml:"type"`

	Scope string `xml:"scope"`
}

type Pom struct {
	XMLName xml.Name `xml:"http://maven.apache.org/POM/4.0.0 project"`

	ArtifactFile string `xml:"-"`

	GroupId    string `xml:"groupId"`
	ArtifactId string `xml:"artifactId"`
	Version    string `xml:"version"`
	Packaging  string `xml:"packaging"`

	Dependencies []Dependency `xml:"dependencies>dependency"`
}

func (p Pom) MkName() string {
	return rewriteNames.Rewrite(p.ArtifactId)
}

func (p Pom) MkDeps() []string {
	var ret []string
	for _, d := range p.Dependencies {
		if d.Type != "aar" {
			continue
		}
		name := rewriteNames.Rewrite(d.ArtifactId)
		ret = append(ret, name)
		ret = append(ret, extraDeps[name]...)
	}
	return ret
}

var mkTemplate = template.Must(template.New("mk").Parse(`
include $(CLEAR_VARS)
LOCAL_MODULE := {{.MkName}}
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_SRC_FILES := {{.ArtifactFile}}
LOCAL_BUILT_MODULE_STEM := javalib.jar
LOCAL_MODULE_SUFFIX := .{{.Packaging}}
LOCAL_USE_AAPT2 := true
LOCAL_STATIC_ANDROID_LIBRARIES := \
{{range .MkDeps}}  {{.}} \
{{end}}
include $(BUILD_PREBUILT)
`))

func convert(filename string, out io.Writer) error {
	data, err := ioutil.ReadFile(filename)
	if err != nil {
		return err
	}

	var pom Pom
	err = xml.Unmarshal(data, &pom)
	if err != nil {
		return err
	}

	if pom.Packaging == "" {
		pom.Packaging = "jar"
	}

	pom.ArtifactFile = strings.TrimSuffix(filename, ".pom") + "." + pom.Packaging

	return mkTemplate.Execute(out, pom)
}

func main() {
	flag.Usage = func() {
		fmt.Fprintf(os.Stderr, `pom2mk, a tool to create Android.mk files from maven repos

The tool will extract the necessary information from *.pom files to create an Android.mk whose
aar libraries can be linked against when using AAPT2.

Usage: %s [--rewrite <regex>=<replace>] [--extra-deps <module>=<module>[,<module>]] <dir>

  -rewrite <regex>=<replace>
     rewrite can be used to specify mappings between the artifactId in the pom files and module
     names in the Android.mk files. This can be specified multiple times, the first matching
     regex will be used.
  -extra-deps <module>=<module>[,<module>]
     Some Android.mk modules have transitive dependencies that must be specified when they are
     depended upon (like android-support-v7-mediarouter requires android-support-v7-appcompat).
     This may be specified multiple times to declare these dependencies.
  <dir>
     The directory to search for *.pom files under.

The makefile is written to stdout, to be put in the current directory (often as Android.mk)
`, os.Args[0])
	}

	flag.Var(&extraDeps, "extra-deps", "Extra dependencies needed when depending on a module")
	flag.Var(&rewriteNames, "rewrite", "Regex(es) to rewrite artifact names")
	flag.Parse()

	if flag.NArg() != 1 {
		flag.Usage()
		os.Exit(1)
	}

	dir := flag.Arg(0)
	absDir, err := filepath.Abs(dir)
	if err != nil {
		fmt.Println(os.Stderr, "Failed to get absolute directory:", err)
		os.Exit(1)
	}

	var filenames []string
	err = filepath.Walk(absDir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		name := info.Name()
		if info.IsDir() {
			if strings.HasPrefix(name, ".") {
				return filepath.SkipDir
			}
			return nil
		}

		if strings.HasPrefix(name, ".") {
			return nil
		}

		if strings.HasSuffix(name, ".pom") {
			path, err = filepath.Rel(absDir, path)
			if err != nil {
				return err
			}
			filenames = append(filenames, filepath.Join(dir, path))
		}
		return nil
	})
	if err != nil {
		fmt.Fprintln(os.Stderr, "Error walking files:", err)
		os.Exit(1)
	}

	if len(filenames) == 0 {
		fmt.Fprintln(os.Stderr, "Error: no *.pom files found under", dir)
		os.Exit(1)
	}

	sort.Strings(filenames)

	fmt.Println("# Automatically generated with:")
	fmt.Println("# pom2mk", strings.Join(proptools.ShellEscape(os.Args[1:]), " "))
	fmt.Println("LOCAL_PATH := $(call my-dir)")

	for _, filename := range filenames {
		err := convert(filename, os.Stdout)
		if err != nil {
			fmt.Fprintln(os.Stderr, "Error converting", filename, err)
			os.Exit(1)
		}
	}
}
