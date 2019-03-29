// Copyright 2016 Google Inc. All rights reserved.
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
	"sync"
	"sync/atomic"
)

type OncePer struct {
	values     atomic.Value
	valuesLock sync.Mutex
}

type valueMap map[interface{}]interface{}

// Once computes a value the first time it is called with a given key per OncePer, and returns the
// value without recomputing when called with the same key.  key must be hashable.
func (once *OncePer) Once(key interface{}, value func() interface{}) interface{} {
	// Atomically load the map without locking.  If this is the first call Load() will return nil
	// and the type assertion will fail, leaving a nil map in m, but that's OK since m is only used
	// for reads.
	m, _ := once.values.Load().(valueMap)
	if v, ok := m[key]; ok {
		return v
	}

	once.valuesLock.Lock()
	defer once.valuesLock.Unlock()

	// Check again with the lock held
	m, _ = once.values.Load().(valueMap)
	if v, ok := m[key]; ok {
		return v
	}

	// Copy the existing map
	newMap := make(valueMap, len(m))
	for k, v := range m {
		newMap[k] = v
	}

	v := value()

	newMap[key] = v
	once.values.Store(newMap)

	return v
}

func (once *OncePer) OnceStringSlice(key interface{}, value func() []string) []string {
	return once.Once(key, func() interface{} { return value() }).([]string)
}

func (once *OncePer) Once2StringSlice(key interface{}, value func() ([]string, []string)) ([]string, []string) {
	type twoStringSlice [2][]string
	s := once.Once(key, func() interface{} {
		var s twoStringSlice
		s[0], s[1] = value()
		return s
	}).(twoStringSlice)
	return s[0], s[1]
}
