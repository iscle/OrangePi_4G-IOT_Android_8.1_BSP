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

package tracer

import (
	"bufio"
	"os"
	"strconv"
	"strings"
)

func (t *tracerImpl) ImportMicrofactoryLog(filename string) {
	if _, err := os.Stat(filename); err != nil {
		return
	}

	f, err := os.Open(filename)
	if err != nil {
		t.log.Println("Error opening microfactory trace:", err)
		return
	}
	defer f.Close()

	entries := []*eventEntry{}
	begin := map[string][]uint64{}
	s := bufio.NewScanner(f)
	for s.Scan() {
		fields := strings.SplitN(s.Text(), " ", 3)
		if len(fields) != 3 {
			t.log.Verboseln("Unknown line in microfactory trace:", s.Text())
			continue
		}
		timestamp, err := strconv.ParseUint(fields[0], 10, 64)
		if err != nil {
			t.log.Verboseln("Failed to parse timestamp in microfactory trace:", err)
		}

		if fields[1] == "B" {
			begin[fields[2]] = append(begin[fields[2]], timestamp)
		} else if beginTimestamps, ok := begin[fields[2]]; ok {
			entries = append(entries, &eventEntry{
				Name:  fields[2],
				Begin: beginTimestamps[len(beginTimestamps)-1],
				End:   timestamp,
			})
			begin[fields[2]] = beginTimestamps[:len(beginTimestamps)-1]
		}
	}

	t.importEvents(entries)
}
