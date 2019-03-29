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
	"bytes"
	"context"
	"flag"
	"fmt"
	"io/ioutil"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"

	"android/soong/ui/build"
	"android/soong/ui/logger"
	"android/soong/ui/tracer"
)

// We default to number of cpus / 4, which seems to be the sweet spot for my
// system. I suspect this is mostly due to memory or disk bandwidth though, and
// may depend on the size ofthe source tree, so this probably isn't a great
// default.
func detectNumJobs() int {
	if runtime.NumCPU() < 4 {
		return 1
	}
	return runtime.NumCPU() / 4
}

var numJobs = flag.Int("j", detectNumJobs(), "number of parallel kati jobs")

var keep = flag.Bool("keep", false, "keep successful output files")

var outDir = flag.String("out", "", "path to store output directories (defaults to tmpdir under $OUT when empty)")
var alternateResultDir = flag.Bool("dist", false, "write select results to $DIST_DIR (or <out>/dist when empty)")

var onlyConfig = flag.Bool("only-config", false, "Only run product config (not Soong or Kati)")
var onlySoong = flag.Bool("only-soong", false, "Only run product config and Soong (not Kati)")

var buildVariant = flag.String("variant", "eng", "build variant to use")

const errorLeadingLines = 20
const errorTrailingLines = 20

type Product struct {
	ctx     build.Context
	config  build.Config
	logFile string
}

type Status struct {
	cur    int
	total  int
	failed int

	ctx           build.Context
	haveBlankLine bool
	smartTerminal bool

	lock sync.Mutex
}

func NewStatus(ctx build.Context) *Status {
	return &Status{
		ctx:           ctx,
		haveBlankLine: true,
		smartTerminal: ctx.IsTerminal(),
	}
}

func (s *Status) SetTotal(total int) {
	s.total = total
}

func (s *Status) Fail(product string, err error, logFile string) {
	s.Finish(product)

	s.lock.Lock()
	defer s.lock.Unlock()

	if s.smartTerminal && !s.haveBlankLine {
		fmt.Fprintln(s.ctx.Stdout())
		s.haveBlankLine = true
	}

	s.failed++
	fmt.Fprintln(s.ctx.Stderr(), "FAILED:", product)
	s.ctx.Verboseln("FAILED:", product)

	if logFile != "" {
		data, err := ioutil.ReadFile(logFile)
		if err == nil {
			lines := strings.Split(strings.TrimSpace(string(data)), "\n")
			if len(lines) > errorLeadingLines+errorTrailingLines+1 {
				lines[errorLeadingLines] = fmt.Sprintf("... skipping %d lines ...",
					len(lines)-errorLeadingLines-errorTrailingLines)

				lines = append(lines[:errorLeadingLines+1],
					lines[len(lines)-errorTrailingLines:]...)
			}
			for _, line := range lines {
				fmt.Fprintln(s.ctx.Stderr(), "> ", line)
				s.ctx.Verboseln(line)
			}
		}
	}

	s.ctx.Print(err)
}

func (s *Status) Finish(product string) {
	s.lock.Lock()
	defer s.lock.Unlock()

	s.cur++
	line := fmt.Sprintf("[%d/%d] %s", s.cur, s.total, product)

	if s.smartTerminal {
		if max, ok := s.ctx.TermWidth(); ok {
			if len(line) > max {
				line = line[:max]
			}
		}

		fmt.Fprint(s.ctx.Stdout(), "\r", line, "\x1b[K")
		s.haveBlankLine = false
	} else {
		s.ctx.Println(line)
	}
}

func (s *Status) Finished() int {
	s.lock.Lock()
	defer s.lock.Unlock()

	if !s.haveBlankLine {
		fmt.Fprintln(s.ctx.Stdout())
		s.haveBlankLine = true
	}
	return s.failed
}

func main() {
	log := logger.New(os.Stderr)
	defer log.Cleanup()

	flag.Parse()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	trace := tracer.New(log)
	defer trace.Close()

	build.SetupSignals(log, cancel, func() {
		trace.Close()
		log.Cleanup()
	})

	buildCtx := build.Context{&build.ContextImpl{
		Context:        ctx,
		Logger:         log,
		Tracer:         trace,
		StdioInterface: build.StdioImpl{},
	}}

	status := NewStatus(buildCtx)

	config := build.NewConfig(buildCtx)
	if *outDir == "" {
		name := "multiproduct-" + time.Now().Format("20060102150405")

		*outDir = filepath.Join(config.OutDir(), name)

		// Ensure the empty files exist in the output directory
		// containing our output directory too. This is mostly for
		// safety, but also triggers the ninja_build file so that our
		// build servers know that they can parse the output as if it
		// was ninja output.
		build.SetupOutDir(buildCtx, config)

		if err := os.MkdirAll(*outDir, 0777); err != nil {
			log.Fatalf("Failed to create tempdir: %v", err)
		}

		if !*keep {
			defer func() {
				if status.Finished() == 0 {
					os.RemoveAll(*outDir)
				}
			}()
		}
	}
	config.Environment().Set("OUT_DIR", *outDir)
	log.Println("Output directory:", *outDir)

	build.SetupOutDir(buildCtx, config)
	if *alternateResultDir {
		logsDir := filepath.Join(config.DistDir(), "logs")
		os.MkdirAll(logsDir, 0777)
		log.SetOutput(filepath.Join(logsDir, "soong.log"))
		trace.SetOutput(filepath.Join(logsDir, "build.trace"))
	} else {
		log.SetOutput(filepath.Join(config.OutDir(), "soong.log"))
		trace.SetOutput(filepath.Join(config.OutDir(), "build.trace"))
	}

	vars, err := build.DumpMakeVars(buildCtx, config, nil, nil, []string{"all_named_products"})
	if err != nil {
		log.Fatal(err)
	}
	products := strings.Fields(vars["all_named_products"])
	log.Verbose("Got product list:", products)

	status.SetTotal(len(products))

	var wg sync.WaitGroup
	productConfigs := make(chan Product, len(products))

	// Run the product config for every product in parallel
	for _, product := range products {
		wg.Add(1)
		go func(product string) {
			var stdLog string

			defer wg.Done()
			defer logger.Recover(func(err error) {
				status.Fail(product, err, stdLog)
			})

			productOutDir := filepath.Join(config.OutDir(), product)
			productLogDir := productOutDir
			if *alternateResultDir {
				productLogDir = filepath.Join(config.DistDir(), product)
				if err := os.MkdirAll(productLogDir, 0777); err != nil {
					log.Fatalf("Error creating log directory: %v", err)
				}
			}

			if err := os.MkdirAll(productOutDir, 0777); err != nil {
				log.Fatalf("Error creating out directory: %v", err)
			}

			stdLog = filepath.Join(productLogDir, "std.log")
			f, err := os.Create(stdLog)
			if err != nil {
				log.Fatalf("Error creating std.log: %v", err)
			}

			productLog := logger.New(&bytes.Buffer{})
			productLog.SetOutput(filepath.Join(productLogDir, "soong.log"))

			productCtx := build.Context{&build.ContextImpl{
				Context:        ctx,
				Logger:         productLog,
				Tracer:         trace,
				StdioInterface: build.NewCustomStdio(nil, f, f),
				Thread:         trace.NewThread(product),
			}}

			productConfig := build.NewConfig(productCtx)
			productConfig.Environment().Set("OUT_DIR", productOutDir)
			productConfig.Lunch(productCtx, product, *buildVariant)

			build.Build(productCtx, productConfig, build.BuildProductConfig)
			productConfigs <- Product{productCtx, productConfig, stdLog}
		}(product)
	}
	go func() {
		defer close(productConfigs)
		wg.Wait()
	}()

	var wg2 sync.WaitGroup
	// Then run up to numJobs worth of Soong and Kati
	for i := 0; i < *numJobs; i++ {
		wg2.Add(1)
		go func() {
			defer wg2.Done()
			for product := range productConfigs {
				func() {
					defer logger.Recover(func(err error) {
						status.Fail(product.config.TargetProduct(), err, product.logFile)
					})

					buildWhat := 0
					if !*onlyConfig {
						buildWhat |= build.BuildSoong
						if !*onlySoong {
							buildWhat |= build.BuildKati
						}
					}
					build.Build(product.ctx, product.config, buildWhat)
					if !*keep {
						os.RemoveAll(product.config.OutDir())
					}
					status.Finish(product.config.TargetProduct())
				}()
			}
		}()
	}
	wg2.Wait()

	if count := status.Finished(); count > 0 {
		log.Fatalln(count, "products failed")
	}
}
