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

// Soong is a builder for Android that uses Blueprint to parse Blueprints
// files and Ninja to do the dependency tracking and subprocess management.
// Soong itself is responsible for converting the modules read by Blueprint
// into build rules, which will be written to a build.ninja file by Blueprint.
//
// Android build concepts:
//
// Device
// A device is a piece of hardware that will be running Android.  It may specify
// global settings like architecture, filesystem configuration, initialization
// scripts, and device drivers.  A device may support all variants of a single
// piece of hardware, or multiple devices may be used for different variants.
// A build is never targeted directly at a device, it is always targeted at a
// "product".
//
// Product
// A product is a configuration of a device, often for a specific market or
// use case.  It is sometimes referred to as a "SKU".  A product defines
// global settings like supported languages, supported use cases, preinstalled
// modules, and user-visible behavior choices.  A product selects one and only
// one device.
//
// Module
// A module is a definition of something to be built.  It may be a C library or
// binary, a java library, an Android app, etc.  A module may be built for multiple
// targets, even in a single build, for example host and device, or 32-bit device
// and 64-bit device.
//
// Installed module
// An installed module is one that has been requested by the selected product,
// or a dependency of an installed module.
//
// Target architecture
// The target architecture is the preferred architecture supported by the selected
// device.  It is most commonly 32-bit arm, but may also be 64-bit arm, 32-bit or
// 64-bit x86, or mips.
//
// Secondary architecture
// The secondary architecture specifies the architecture to compile a second copy
// of some modules for devices that support multiple architectures, for example
// 64-bit devices that also support 32-bit binaries.
package soong
