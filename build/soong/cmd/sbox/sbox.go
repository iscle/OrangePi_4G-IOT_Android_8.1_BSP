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
	"fmt"
	"io/ioutil"
	"os"
	"os/exec"
	"path"
	"path/filepath"
	"strings"
)

func main() {
	error := run()
	if error != nil {
		fmt.Fprintln(os.Stderr, error)
		os.Exit(1)
	}
}

var usage = "Usage: sbox -c <commandToRun> --sandbox-path <sandboxPath> --output-root <outputRoot> <outputFile> [<outputFile>...]\n" +
	"\n" +
	"Runs <commandToRun> and moves each <outputFile> out of <sandboxPath>\n" +
	"If any file in <outputFiles> is specified by absolute path, then <outputRoot> must be specified as well,\n" +
	"to enable sbox to compute the relative path within the sandbox of the specified output files"

func usageError(violation string) error {
	return fmt.Errorf("Usage error: %s.\n\n%s", violation, usage)
}

func run() error {
	var outFiles []string
	args := os.Args[1:]

	var rawCommand string
	var sandboxesRoot string
	removeTempDir := true
	var outputRoot string

	for i := 0; i < len(args); i++ {
		arg := args[i]
		if arg == "--sandbox-path" {
			sandboxesRoot = args[i+1]
			i++
		} else if arg == "-c" {
			rawCommand = args[i+1]
			i++
		} else if arg == "--output-root" {
			outputRoot = args[i+1]
			i++
		} else if arg == "--keep-out-dir" {
			removeTempDir = false
		} else {
			outFiles = append(outFiles, arg)
		}
	}
	if len(rawCommand) == 0 {
		return usageError("-c <commandToRun> is required and must be non-empty")
	}
	if outFiles == nil {
		return usageError("at least one output file must be given")
	}
	if len(sandboxesRoot) == 0 {
		// In practice, the value of sandboxesRoot will mostly likely be at a fixed location relative to OUT_DIR,
		// and the sbox executable will most likely be at a fixed location relative to OUT_DIR too, so
		// the value of sandboxesRoot will most likely be at a fixed location relative to the sbox executable
		// However, Soong also needs to be able to separately remove the sandbox directory on startup (if it has anything left in it)
		// and by passing it as a parameter we don't need to duplicate its value
		return usageError("--sandbox-path <sandboxPath> is required and must be non-empty")
	}

	// Rewrite output file paths to be relative to output root
	// This facilitates matching them up against the corresponding paths in the temporary directory in case they're absolute
	for i, filePath := range outFiles {
		if path.IsAbs(filePath) {
			if len(outputRoot) == 0 {
				return fmt.Errorf("Absolute path %s requires nonempty value for --output-root", filePath)
			}
		}
		relativePath, err := filepath.Rel(outputRoot, filePath)
		if err != nil {
			return err
		}
		outFiles[i] = relativePath
	}

	os.MkdirAll(sandboxesRoot, 0777)

	tempDir, err := ioutil.TempDir(sandboxesRoot, "sbox")
	if err != nil {
		return fmt.Errorf("Failed to create temp dir: %s", err)
	}

	// In the common case, the following line of code is what removes the sandbox
	// If a fatal error occurs (such as if our Go process is killed unexpectedly),
	// then at the beginning of the next build, Soong will retry the cleanup
	defer func() {
		// in some cases we decline to remove the temp dir, to facilitate debugging
		if removeTempDir {
			os.RemoveAll(tempDir)
		}
	}()

	if strings.Contains(rawCommand, "__SBOX_OUT_DIR__") {
		rawCommand = strings.Replace(rawCommand, "__SBOX_OUT_DIR__", tempDir, -1)
	}

	if strings.Contains(rawCommand, "__SBOX_OUT_FILES__") {
		// expands into a space-separated list of output files to be generated into the sandbox directory
		tempOutPaths := []string{}
		for _, outputPath := range outFiles {
			tempOutPath := path.Join(tempDir, outputPath)
			tempOutPaths = append(tempOutPaths, tempOutPath)
		}
		pathsText := strings.Join(tempOutPaths, " ")
		rawCommand = strings.Replace(rawCommand, "__SBOX_OUT_FILES__", pathsText, -1)
	}

	for _, filePath := range outFiles {
		os.MkdirAll(path.Join(tempDir, filepath.Dir(filePath)), 0777)
	}

	commandDescription := rawCommand

	cmd := exec.Command("bash", "-c", rawCommand)
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	err = cmd.Run()

	if exit, ok := err.(*exec.ExitError); ok && !exit.Success() {
		return fmt.Errorf("sbox command (%s) failed with err %#v\n", commandDescription, err.Error())
	} else if err != nil {
		return err
	}

	// validate that all files are created properly
	var outputErrors []error
	for _, filePath := range outFiles {
		tempPath := filepath.Join(tempDir, filePath)
		fileInfo, err := os.Stat(tempPath)
		if err != nil {
			outputErrors = append(outputErrors, fmt.Errorf("failed to create expected output file: %s\n", tempPath))
			continue
		}
		if fileInfo.IsDir() {
			outputErrors = append(outputErrors, fmt.Errorf("Output path %s refers to a directory, not a file. This is not permitted because it prevents robust up-to-date checks\n", filePath))
		}
	}
	if len(outputErrors) > 0 {
		// Keep the temporary output directory around in case a user wants to inspect it for debugging purposes.
		// Soong will delete it later anyway.
		removeTempDir = false
		return fmt.Errorf("mismatch between declared and actual outputs in sbox command (%s):\n%v", commandDescription, outputErrors)
	}
	// the created files match the declared files; now move them
	for _, filePath := range outFiles {
		tempPath := filepath.Join(tempDir, filePath)
		destPath := filePath
		if len(outputRoot) != 0 {
			destPath = filepath.Join(outputRoot, filePath)
		}
		err := os.Rename(tempPath, destPath)
		if err != nil {
			return err
		}
	}

	// TODO(jeffrygaston) if a process creates more output files than it declares, should there be a warning?
	return nil
}
