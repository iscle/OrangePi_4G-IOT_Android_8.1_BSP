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

// This file implements the logic of bpfix and also provides a programmatic interface

package bpfix

import (
	"bytes"
	"fmt"
	"github.com/google/blueprint/parser"
)

// A FixRequest specifies the details of which fixes to apply to an individual file
// A FixRequest doesn't specify whether to do a dry run or where to write the results; that's in cmd/bpfix.go
type FixRequest struct {
	simplifyKnownRedundantVariables bool
	removeEmptyLists                bool
}

func NewFixRequest() FixRequest {
	return FixRequest{}
}

func (r FixRequest) AddAll() (result FixRequest) {
	result = r
	result.simplifyKnownRedundantVariables = true
	result.removeEmptyLists = true
	return result
}

// FixTree repeatedly applies the fixes listed in the given FixRequest to the given File
// until there is no fix that affects the tree
func FixTree(tree *parser.File, config FixRequest) (fixed *parser.File, err error) {
	prevIdentifier, err := fingerprint(tree)
	if err != nil {
		return nil, err
	}

	fixed = tree
	maxNumIterations := 20
	i := 0
	for {
		fixed, err = fixTreeOnce(fixed, config)
		newIdentifier, err := fingerprint(tree)
		if err != nil {
			return nil, err
		}
		if bytes.Equal(newIdentifier, prevIdentifier) {
			break
		}
		prevIdentifier = newIdentifier
		// any errors from a previous iteration generally get thrown away and overwritten by errors on the next iteration

		// detect infinite loop
		i++
		if i >= maxNumIterations {
			return nil, fmt.Errorf("Applied fixes %s times and yet the tree continued to change. Is there an infinite loop?", i)
			break
		}
	}
	return fixed, err
}

// returns a unique identifier for the given tree that can be used to determine whether the tree changed
func fingerprint(tree *parser.File) (fingerprint []byte, err error) {
	bytes, err := parser.Print(tree)
	if err != nil {
		return nil, err
	}
	return bytes, nil
}

func fixTreeOnce(tree *parser.File, config FixRequest) (fixed *parser.File, err error) {
	if config.simplifyKnownRedundantVariables {
		tree, err = simplifyKnownPropertiesDuplicatingEachOther(tree)
		if err != nil {
			return nil, err
		}
	}
	if config.removeEmptyLists {
		tree, err = removePropertiesHavingTheirDefaultValues(tree)
		if err != nil {
			return nil, err
		}
	}
	return tree, err
}

func simplifyKnownPropertiesDuplicatingEachOther(tree *parser.File) (fixed *parser.File, err error) {
	// remove from local_include_dirs anything in export_include_dirs
	fixed, err = removeMatchingModuleListProperties(tree, "export_include_dirs", "local_include_dirs")
	return fixed, err
}

// removes from <items> every item present in <removals>
func filterExpressionList(items *parser.List, removals *parser.List) {
	writeIndex := 0
	for _, item := range items.Values {
		included := true
		for _, removal := range removals.Values {
			equal, err := parser.ExpressionsAreSame(item, removal)
			if err != nil {
				continue
			}
			if equal {
				included = false
				break
			}
		}
		if included {
			items.Values[writeIndex] = item
			writeIndex++
		}
	}
	items.Values = items.Values[:writeIndex]
}

// Remove each modules[i].Properties[<legacyName>][j] that matches a modules[i].Properties[<canonicalName>][k]
func removeMatchingModuleListProperties(tree *parser.File, canonicalName string, legacyName string) (fixed *parser.File, err error) {
	for _, def := range tree.Defs {
		mod, ok := def.(*parser.Module)
		if !ok {
			continue
		}
		legacy, ok := mod.GetProperty(legacyName)
		if !ok {
			continue
		}
		legacyList, ok := legacy.Value.(*parser.List)
		if !ok {
			continue
		}
		canonical, ok := mod.GetProperty(canonicalName)
		if !ok {
			continue
		}
		canonicalList, ok := canonical.Value.(*parser.List)
		if !ok {
			continue
		}
		filterExpressionList(legacyList, canonicalList)
	}
	return tree, nil
}

func removePropertiesHavingTheirDefaultValues(tree *parser.File) (fixed *parser.File, err error) {
	for _, def := range tree.Defs {
		mod, ok := def.(*parser.Module)
		if !ok {
			continue
		}
		writeIndex := 0
		for _, prop := range mod.Properties {
			val := prop.Value
			keep := true
			switch val := val.(type) {
			case *parser.List:
				if len(val.Values) == 0 {
					keep = false
				}
				break
			default:
				keep = true
			}
			if keep {
				mod.Properties[writeIndex] = prop
				writeIndex++
			}
		}
		mod.Properties = mod.Properties[:writeIndex]
	}
	return tree, nil
}
