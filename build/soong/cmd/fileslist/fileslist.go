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

// fileslist.py replacement written in GO, which utilizes multi-cores.

package main

import (
	"crypto/sha256"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"sync"
)

const (
	MAX_DEFAULT_PARA = 24
)

func defaultPara() int {
	ret := runtime.NumCPU()
	if ret > MAX_DEFAULT_PARA {
		return MAX_DEFAULT_PARA
	}
	return ret
}

var (
	para = flag.Int("para", defaultPara(), "Number of goroutines")
)

// Represents each file.
type Node struct {
	SHA256 string
	Name   string // device side path.
	Size   int64
	path   string // host side path.
	stat   os.FileInfo
}

func newNode(hostPath string, devicePath string, stat os.FileInfo) Node {
	return Node{Name: devicePath, path: hostPath, stat: stat}
}

// Scan a Node and returns true if it should be added to the result.
func (n *Node) scan() bool {
	n.Size = n.stat.Size()

	// Calculate SHA256.
	h := sha256.New()
	if n.stat.Mode()&os.ModeSymlink == 0 {
		f, err := os.Open(n.path)
		if err != nil {
			panic(err)
		}
		defer f.Close()

		if _, err := io.Copy(h, f); err != nil {
			panic(err)
		}
	} else {
		// Hash the content of symlink, not the file it points to.
		s, err := os.Readlink(n.path)
		if err != nil {
			panic(err)
		}
		if _, err := io.WriteString(h, s); err != nil {
			panic(err)
		}
	}
	n.SHA256 = fmt.Sprintf("%x", h.Sum(nil))
	return true
}

func main() {
	flag.Parse()

	allOutput := make([]Node, 0, 1024) // Store all outputs.
	mutex := &sync.Mutex{}             // Guard allOutput

	ch := make(chan Node) // Pass nodes to goroutines.

	var wg sync.WaitGroup // To wait for all goroutines.
	wg.Add(*para)

	// Scan files in multiple goroutines.
	for i := 0; i < *para; i++ {
		go func() {
			defer wg.Done()

			output := make([]Node, 0, 1024) // Local output list.
			for node := range ch {
				if node.scan() {
					output = append(output, node)
				}
			}
			// Add to the global output list.
			mutex.Lock()
			allOutput = append(allOutput, output...)
			mutex.Unlock()
		}()
	}

	// Walk the directories and find files to scan.
	for _, dir := range flag.Args() {
		absDir, err := filepath.Abs(dir)
		if err != nil {
			panic(err)
		}
		deviceRoot := filepath.Clean(absDir + "/..")
		err = filepath.Walk(dir, func(path string, stat os.FileInfo, err error) error {
			if err != nil {
				panic(err)
			}
			if stat.IsDir() {
				return nil
			}
			absPath, err := filepath.Abs(path)
			if err != nil {
				panic(err)
			}
			devicePath, err := filepath.Rel(deviceRoot, absPath)
			if err != nil {
				panic(err)
			}
			devicePath = "/" + devicePath
			ch <- newNode(absPath, devicePath, stat)
			return nil
		})
		if err != nil {
			panic(err)
		}
	}

	// Wait until all the goroutines finish.
	close(ch)
	wg.Wait()

	// Sort the entries and dump as json.
	sort.Slice(allOutput, func(i, j int) bool {
		if allOutput[i].Size > allOutput[j].Size {
			return true
		}
		if allOutput[i].Size == allOutput[j].Size && strings.Compare(allOutput[i].Name, allOutput[j].Name) > 0 {
			return true
		}
		return false
	})

	j, err := json.MarshalIndent(allOutput, "", "  ")
	if err != nil {
		panic(nil)
	}

	fmt.Printf("%s\n", j)
}
