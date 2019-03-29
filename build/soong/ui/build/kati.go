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
	"bufio"
	"crypto/md5"
	"fmt"
	"io"
	"io/ioutil"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
)

var spaceSlashReplacer = strings.NewReplacer("/", "_", " ", "_")

// genKatiSuffix creates a suffix for kati-generated files so that we can cache
// them based on their inputs. So this should encode all common changes to Kati
// inputs. Currently that includes the TARGET_PRODUCT, kati-processed command
// line arguments, and the directories specified by mm/mmm.
func genKatiSuffix(ctx Context, config Config) {
	katiSuffix := "-" + config.TargetProduct()
	if args := config.KatiArgs(); len(args) > 0 {
		katiSuffix += "-" + spaceSlashReplacer.Replace(strings.Join(args, "_"))
	}
	if oneShot, ok := config.Environment().Get("ONE_SHOT_MAKEFILE"); ok {
		katiSuffix += "-" + spaceSlashReplacer.Replace(oneShot)
	}

	// If the suffix is too long, replace it with a md5 hash and write a
	// file that contains the original suffix.
	if len(katiSuffix) > 64 {
		shortSuffix := "-" + fmt.Sprintf("%x", md5.Sum([]byte(katiSuffix)))
		config.SetKatiSuffix(shortSuffix)

		ctx.Verbosef("Kati ninja suffix too long: %q", katiSuffix)
		ctx.Verbosef("Replacing with: %q", shortSuffix)

		if err := ioutil.WriteFile(strings.TrimSuffix(config.KatiNinjaFile(), "ninja")+"suf", []byte(katiSuffix), 0777); err != nil {
			ctx.Println("Error writing suffix file:", err)
		}
	} else {
		config.SetKatiSuffix(katiSuffix)
	}
}

func runKati(ctx Context, config Config) {
	ctx.BeginTrace("kati")
	defer ctx.EndTrace()

	genKatiSuffix(ctx, config)

	executable := config.PrebuiltBuildTool("ckati")
	args := []string{
		"--ninja",
		"--ninja_dir=" + config.OutDir(),
		"--ninja_suffix=" + config.KatiSuffix(),
		"--regen",
		"--ignore_optional_include=" + filepath.Join(config.OutDir(), "%.P"),
		"--detect_android_echo",
		"--color_warnings",
		"--gen_all_targets",
		"--werror_find_emulator",
		"-f", "build/core/main.mk",
	}

	if !config.Environment().IsFalse("KATI_EMULATE_FIND") {
		args = append(args, "--use_find_emulator")
	}

	args = append(args, config.KatiArgs()...)

	args = append(args,
		"BUILDING_WITH_NINJA=true",
		"SOONG_ANDROID_MK="+config.SoongAndroidMk(),
		"SOONG_MAKEVARS_MK="+config.SoongMakeVarsMk())

	if config.UseGoma() {
		args = append(args, "-j"+strconv.Itoa(config.Parallel()))
	}

	cmd := Command(ctx, config, "ckati", executable, args...)
	cmd.Sandbox = katiSandbox
	pipe, err := cmd.StdoutPipe()
	if err != nil {
		ctx.Fatalln("Error getting output pipe for ckati:", err)
	}
	cmd.Stderr = cmd.Stdout

	// Kati leaks memory, so ensure leak detection is turned off
	cmd.Environment.Set("ASAN_OPTIONS", "detect_leaks=0")

	cmd.StartOrFatal()
	katiRewriteOutput(ctx, pipe)
	cmd.WaitOrFatal()
}

var katiIncludeRe = regexp.MustCompile(`^(\[\d+/\d+] )?including [^ ]+ ...$`)

func katiRewriteOutput(ctx Context, pipe io.ReadCloser) {
	haveBlankLine := true
	smartTerminal := ctx.IsTerminal()

	scanner := bufio.NewScanner(pipe)
	for scanner.Scan() {
		line := scanner.Text()
		verbose := katiIncludeRe.MatchString(line)

		// For verbose lines, write them on the current line without a newline,
		// then overwrite them if the next thing we're printing is another
		// verbose line.
		if smartTerminal && verbose {
			// Limit line width to the terminal width, otherwise we'll wrap onto
			// another line and we won't delete the previous line.
			//
			// Run this on every line in case the window has been resized while
			// we're printing. This could be optimized to only re-run when we
			// get SIGWINCH if it ever becomes too time consuming.
			if max, ok := termWidth(ctx.Stdout()); ok {
				if len(line) > max {
					// Just do a max. Ninja elides the middle, but that's
					// more complicated and these lines aren't that important.
					line = line[:max]
				}
			}

			// Move to the beginning on the line, print the output, then clear
			// the rest of the line.
			fmt.Fprint(ctx.Stdout(), "\r", line, "\x1b[K")
			haveBlankLine = false
			continue
		} else if smartTerminal && !haveBlankLine {
			// If we've previously written a verbose message, send a newline to save
			// that message instead of overwriting it.
			fmt.Fprintln(ctx.Stdout())
			haveBlankLine = true
		} else if !smartTerminal {
			// Most editors display these as garbage, so strip them out.
			line = string(stripAnsiEscapes([]byte(line)))
		}

		// Assume that non-verbose lines are important enough for stderr
		fmt.Fprintln(ctx.Stderr(), line)
	}

	// Save our last verbose line.
	if !haveBlankLine {
		fmt.Fprintln(ctx.Stdout())
	}
}
