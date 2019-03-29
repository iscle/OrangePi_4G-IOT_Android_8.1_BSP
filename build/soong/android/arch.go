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
	"fmt"
	"reflect"
	"runtime"
	"strings"

	"github.com/google/blueprint/proptools"
)

var (
	archTypeList []ArchType

	Arm    = newArch("arm", "lib32")
	Arm64  = newArch("arm64", "lib64")
	Mips   = newArch("mips", "lib32")
	Mips64 = newArch("mips64", "lib64")
	X86    = newArch("x86", "lib32")
	X86_64 = newArch("x86_64", "lib64")

	Common = ArchType{
		Name: "common",
	}
)

var archTypeMap = map[string]ArchType{
	"arm":    Arm,
	"arm64":  Arm64,
	"mips":   Mips,
	"mips64": Mips64,
	"x86":    X86,
	"x86_64": X86_64,
}

/*
Example blueprints file containing all variant property groups, with comment listing what type
of variants get properties in that group:

module {
    arch: {
        arm: {
            // Host or device variants with arm architecture
        },
        arm64: {
            // Host or device variants with arm64 architecture
        },
        mips: {
            // Host or device variants with mips architecture
        },
        mips64: {
            // Host or device variants with mips64 architecture
        },
        x86: {
            // Host or device variants with x86 architecture
        },
        x86_64: {
            // Host or device variants with x86_64 architecture
        },
    },
    multilib: {
        lib32: {
            // Host or device variants for 32-bit architectures
        },
        lib64: {
            // Host or device variants for 64-bit architectures
        },
    },
    target: {
        android: {
            // Device variants
        },
        host: {
            // Host variants
        },
        linux: {
            // Linux host variants
        },
        darwin: {
            // Darwin host variants
        },
        windows: {
            // Windows host variants
        },
        not_windows: {
            // Non-windows host variants
        },
    },
}
*/

var archVariants = map[ArchType][]string{}
var archFeatures = map[ArchType][]string{}
var archFeatureMap = map[ArchType]map[string][]string{}

func RegisterArchVariants(arch ArchType, variants ...string) {
	checkCalledFromInit()
	archVariants[arch] = append(archVariants[arch], variants...)
}

func RegisterArchFeatures(arch ArchType, features ...string) {
	checkCalledFromInit()
	archFeatures[arch] = append(archFeatures[arch], features...)
}

func RegisterArchVariantFeatures(arch ArchType, variant string, features ...string) {
	checkCalledFromInit()
	if variant != "" && !inList(variant, archVariants[arch]) {
		panic(fmt.Errorf("Invalid variant %q for arch %q", variant, arch))
	}

	for _, feature := range features {
		if !inList(feature, archFeatures[arch]) {
			panic(fmt.Errorf("Invalid feature %q for arch %q variant %q", feature, arch, variant))
		}
	}

	if archFeatureMap[arch] == nil {
		archFeatureMap[arch] = make(map[string][]string)
	}
	archFeatureMap[arch][variant] = features
}

// An Arch indicates a single CPU architecture.
type Arch struct {
	ArchType     ArchType
	ArchVariant  string
	CpuVariant   string
	Abi          []string
	ArchFeatures []string
	Native       bool
}

func (a Arch) String() string {
	s := a.ArchType.String()
	if a.ArchVariant != "" {
		s += "_" + a.ArchVariant
	}
	if a.CpuVariant != "" {
		s += "_" + a.CpuVariant
	}
	return s
}

type ArchType struct {
	Name     string
	Field    string
	Multilib string
}

func newArch(name, multilib string) ArchType {
	archType := ArchType{
		Name:     name,
		Field:    proptools.FieldNameForProperty(name),
		Multilib: multilib,
	}
	archTypeList = append(archTypeList, archType)
	return archType
}

func (a ArchType) String() string {
	return a.Name
}

var BuildOs = func() OsType {
	switch runtime.GOOS {
	case "linux":
		return Linux
	case "darwin":
		return Darwin
	default:
		panic(fmt.Sprintf("unsupported OS: %s", runtime.GOOS))
	}
}()

var (
	osTypeList      []OsType
	commonTargetMap = make(map[string]Target)

	NoOsType    OsType
	Linux       = NewOsType("linux", Host, false)
	Darwin      = NewOsType("darwin", Host, false)
	LinuxBionic = NewOsType("linux_bionic", Host, true)
	Windows     = NewOsType("windows", HostCross, true)
	Android     = NewOsType("android", Device, false)

	osArchTypeMap = map[OsType][]ArchType{
		Linux:       []ArchType{X86, X86_64},
		LinuxBionic: []ArchType{X86_64},
		Darwin:      []ArchType{X86, X86_64},
		Windows:     []ArchType{X86, X86_64},
		Android:     []ArchType{Arm, Arm64, Mips, Mips64, X86, X86_64},
	}
)

type OsType struct {
	Name, Field string
	Class       OsClass

	DefaultDisabled bool
}

type OsClass int

const (
	Generic OsClass = iota
	Device
	Host
	HostCross
)

func (class OsClass) String() string {
	switch class {
	case Generic:
		return "generic"
	case Device:
		return "device"
	case Host:
		return "host"
	case HostCross:
		return "host cross"
	default:
		panic(fmt.Errorf("unknown class %d", class))
	}
}

func (os OsType) String() string {
	return os.Name
}

func NewOsType(name string, class OsClass, defDisabled bool) OsType {
	os := OsType{
		Name:  name,
		Field: strings.Title(name),
		Class: class,

		DefaultDisabled: defDisabled,
	}
	osTypeList = append(osTypeList, os)

	if _, found := commonTargetMap[name]; found {
		panic(fmt.Errorf("Found Os type duplicate during OsType registration: %q", name))
	} else {
		commonTargetMap[name] = Target{Os: os, Arch: Arch{ArchType: Common}}
	}

	return os
}

func osByName(name string) OsType {
	for _, os := range osTypeList {
		if os.Name == name {
			return os
		}
	}

	return NoOsType
}

type Target struct {
	Os   OsType
	Arch Arch
}

func (target Target) String() string {
	return target.Os.String() + "_" + target.Arch.String()
}

func archMutator(mctx BottomUpMutatorContext) {
	var module Module
	var ok bool
	if module, ok = mctx.Module().(Module); !ok {
		return
	}

	if !module.base().ArchSpecific() {
		return
	}

	osClasses := module.base().OsClassSupported()

	var moduleTargets []Target
	primaryModules := make(map[int]bool)

	for _, class := range osClasses {
		targets := mctx.AConfig().Targets[class]
		if len(targets) == 0 {
			continue
		}
		var multilib string
		switch class {
		case Device:
			multilib = module.base().commonProperties.Target.Android.Compile_multilib
		case Host, HostCross:
			multilib = module.base().commonProperties.Target.Host.Compile_multilib
		}
		if multilib == "" {
			multilib = module.base().commonProperties.Compile_multilib
		}
		if multilib == "" {
			multilib = module.base().commonProperties.Default_multilib
		}
		var prefer32 bool
		switch class {
		case Device:
			prefer32 = mctx.AConfig().DevicePrefer32BitExecutables()
		case HostCross:
			// Windows builds always prefer 32-bit
			prefer32 = true
		}
		targets, err := decodeMultilib(multilib, targets, prefer32)
		if err != nil {
			mctx.ModuleErrorf("%s", err.Error())
		}
		if len(targets) > 0 {
			primaryModules[len(moduleTargets)] = true
			moduleTargets = append(moduleTargets, targets...)
		}
	}

	if len(moduleTargets) == 0 {
		module.base().commonProperties.Enabled = boolPtr(false)
		return
	}

	targetNames := make([]string, len(moduleTargets))

	for i, target := range moduleTargets {
		targetNames[i] = target.String()
	}

	modules := mctx.CreateVariations(targetNames...)
	for i, m := range modules {
		m.(Module).base().SetTarget(moduleTargets[i], primaryModules[i])
		m.(Module).base().setArchProperties(mctx)
	}
}

func filterArchStruct(prop reflect.Type) (reflect.Type, bool) {
	var fields []reflect.StructField

	ptr := prop.Kind() == reflect.Ptr
	if ptr {
		prop = prop.Elem()
	}

	for i := 0; i < prop.NumField(); i++ {
		field := prop.Field(i)
		if !proptools.HasTag(field, "android", "arch_variant") {
			continue
		}

		// The arch_variant field isn't necessary past this point
		// Instead of wasting space, just remove it. Go also has a
		// 16-bit limit on structure name length. The name is constructed
		// based on the Go source representation of the structure, so
		// the tag names count towards that length.
		//
		// TODO: handle the uncommon case of other tags being involved
		if field.Tag == `android:"arch_variant"` {
			field.Tag = ""
		}

		// Recurse into structs
		switch field.Type.Kind() {
		case reflect.Struct:
			var ok bool
			field.Type, ok = filterArchStruct(field.Type)
			if !ok {
				continue
			}
		case reflect.Ptr:
			if field.Type.Elem().Kind() == reflect.Struct {
				nestedType, ok := filterArchStruct(field.Type.Elem())
				if !ok {
					continue
				}
				field.Type = reflect.PtrTo(nestedType)
			}
		case reflect.Interface:
			panic("Interfaces are not supported in arch_variant properties")
		}

		fields = append(fields, field)
	}
	if len(fields) == 0 {
		return nil, false
	}

	ret := reflect.StructOf(fields)
	if ptr {
		ret = reflect.PtrTo(ret)
	}
	return ret, true
}

func createArchType(props reflect.Type) reflect.Type {
	props, ok := filterArchStruct(props)
	if !ok {
		return nil
	}

	variantFields := func(names []string) []reflect.StructField {
		ret := make([]reflect.StructField, len(names))

		for i, name := range names {
			ret[i].Name = name
			ret[i].Type = props
		}

		return ret
	}

	archFields := make([]reflect.StructField, len(archTypeList))
	for i, arch := range archTypeList {
		variants := []string{}

		for _, archVariant := range archVariants[arch] {
			archVariant := variantReplacer.Replace(archVariant)
			variants = append(variants, proptools.FieldNameForProperty(archVariant))
		}
		for _, feature := range archFeatures[arch] {
			feature := variantReplacer.Replace(feature)
			variants = append(variants, proptools.FieldNameForProperty(feature))
		}

		fields := variantFields(variants)

		fields = append([]reflect.StructField{reflect.StructField{
			Name:      "BlueprintEmbed",
			Type:      props,
			Anonymous: true,
		}}, fields...)

		archFields[i] = reflect.StructField{
			Name: arch.Field,
			Type: reflect.StructOf(fields),
		}
	}
	archType := reflect.StructOf(archFields)

	multilibType := reflect.StructOf(variantFields([]string{"Lib32", "Lib64"}))

	targets := []string{
		"Host",
		"Android64",
		"Android32",
		"Not_windows",
		"Arm_on_x86",
		"Arm_on_x86_64",
	}
	for _, os := range osTypeList {
		targets = append(targets, os.Field)

		for _, archType := range osArchTypeMap[os] {
			targets = append(targets, os.Field+"_"+archType.Name)
		}
	}

	targetType := reflect.StructOf(variantFields(targets))
	return reflect.StructOf([]reflect.StructField{
		reflect.StructField{
			Name: "Arch",
			Type: archType,
		},
		reflect.StructField{
			Name: "Multilib",
			Type: multilibType,
		},
		reflect.StructField{
			Name: "Target",
			Type: targetType,
		},
	})
}

var archPropTypeMap OncePer

func InitArchModule(m Module) {

	base := m.base()

	base.generalProperties = m.GetProperties()

	for _, properties := range base.generalProperties {
		propertiesValue := reflect.ValueOf(properties)
		t := propertiesValue.Type()
		if propertiesValue.Kind() != reflect.Ptr {
			panic(fmt.Errorf("properties must be a pointer to a struct, got %T",
				propertiesValue.Interface()))
		}

		propertiesValue = propertiesValue.Elem()
		if propertiesValue.Kind() != reflect.Struct {
			panic(fmt.Errorf("properties must be a pointer to a struct, got %T",
				propertiesValue.Interface()))
		}

		archPropType := archPropTypeMap.Once(t, func() interface{} {
			return createArchType(t)
		})

		if archPropType != nil {
			base.archProperties = append(base.archProperties, reflect.New(archPropType.(reflect.Type)).Interface())
		} else {
			base.archProperties = append(base.archProperties, nil)
		}
	}

	for _, asp := range base.archProperties {
		if asp != nil {
			m.AddProperties(asp)
		}
	}

	base.customizableProperties = m.GetProperties()
}

var variantReplacer = strings.NewReplacer("-", "_", ".", "_")

func (a *ModuleBase) appendProperties(ctx BottomUpMutatorContext,
	dst interface{}, src reflect.Value, field, srcPrefix string) reflect.Value {

	src = src.FieldByName(field)
	if !src.IsValid() {
		ctx.ModuleErrorf("field %q does not exist", srcPrefix)
		return src
	}

	ret := src

	if src.Kind() == reflect.Struct {
		src = src.FieldByName("BlueprintEmbed")
	}

	order := func(property string,
		dstField, srcField reflect.StructField,
		dstValue, srcValue interface{}) (proptools.Order, error) {
		if proptools.HasTag(dstField, "android", "variant_prepend") {
			return proptools.Prepend, nil
		} else {
			return proptools.Append, nil
		}
	}

	err := proptools.ExtendMatchingProperties([]interface{}{dst}, src.Interface(), nil, order)
	if err != nil {
		if propertyErr, ok := err.(*proptools.ExtendPropertyError); ok {
			ctx.PropertyErrorf(propertyErr.Property, "%s", propertyErr.Err.Error())
		} else {
			panic(err)
		}
	}

	return ret
}

// Rewrite the module's properties structs to contain arch-specific values.
func (a *ModuleBase) setArchProperties(ctx BottomUpMutatorContext) {
	arch := a.Arch()
	os := a.Os()

	if arch.ArchType == Common {
		return
	}

	for i := range a.generalProperties {
		genProps := a.generalProperties[i]
		if a.archProperties[i] == nil {
			continue
		}
		archProps := reflect.ValueOf(a.archProperties[i]).Elem()

		archProp := archProps.FieldByName("Arch")
		multilibProp := archProps.FieldByName("Multilib")
		targetProp := archProps.FieldByName("Target")

		// Handle arch-specific properties in the form:
		// arch: {
		//     arm64: {
		//         key: value,
		//     },
		// },
		t := arch.ArchType

		field := proptools.FieldNameForProperty(t.Name)
		prefix := "arch." + t.Name
		archStruct := a.appendProperties(ctx, genProps, archProp, field, prefix)

		// Handle arch-variant-specific properties in the form:
		// arch: {
		//     variant: {
		//         key: value,
		//     },
		// },
		v := variantReplacer.Replace(arch.ArchVariant)
		if v != "" {
			field := proptools.FieldNameForProperty(v)
			prefix := "arch." + t.Name + "." + v
			a.appendProperties(ctx, genProps, archStruct, field, prefix)
		}

		// Handle cpu-variant-specific properties in the form:
		// arch: {
		//     variant: {
		//         key: value,
		//     },
		// },
		if arch.CpuVariant != arch.ArchVariant {
			c := variantReplacer.Replace(arch.CpuVariant)
			if c != "" {
				field := proptools.FieldNameForProperty(c)
				prefix := "arch." + t.Name + "." + c
				a.appendProperties(ctx, genProps, archStruct, field, prefix)
			}
		}

		// Handle arch-feature-specific properties in the form:
		// arch: {
		//     feature: {
		//         key: value,
		//     },
		// },
		for _, feature := range arch.ArchFeatures {
			field := proptools.FieldNameForProperty(feature)
			prefix := "arch." + t.Name + "." + feature
			a.appendProperties(ctx, genProps, archStruct, field, prefix)
		}

		// Handle multilib-specific properties in the form:
		// multilib: {
		//     lib32: {
		//         key: value,
		//     },
		// },
		field = proptools.FieldNameForProperty(t.Multilib)
		prefix = "multilib." + t.Multilib
		a.appendProperties(ctx, genProps, multilibProp, field, prefix)

		// Handle host-specific properties in the form:
		// target: {
		//     host: {
		//         key: value,
		//     },
		// },
		if os.Class == Host || os.Class == HostCross {
			field = "Host"
			prefix = "target.host"
			a.appendProperties(ctx, genProps, targetProp, field, prefix)
		}

		// Handle target OS properties in the form:
		// target: {
		//     linux: {
		//         key: value,
		//     },
		//     not_windows: {
		//         key: value,
		//     },
		//     linux_x86: {
		//         key: value,
		//     },
		//     linux_arm: {
		//         key: value,
		//     },
		//     android {
		//         key: value,
		//     },
		//     android_arm {
		//         key: value,
		//     },
		//     android_x86 {
		//         key: value,
		//     },
		// },
		// },
		field = os.Field
		prefix = "target." + os.Name
		a.appendProperties(ctx, genProps, targetProp, field, prefix)

		field = os.Field + "_" + t.Name
		prefix = "target." + os.Name + "_" + t.Name
		a.appendProperties(ctx, genProps, targetProp, field, prefix)

		if (os.Class == Host || os.Class == HostCross) && os != Windows {
			field := "Not_windows"
			prefix := "target.not_windows"
			a.appendProperties(ctx, genProps, targetProp, field, prefix)
		}

		// Handle 64-bit device properties in the form:
		// target {
		//     android64 {
		//         key: value,
		//     },
		//     android32 {
		//         key: value,
		//     },
		// },
		// WARNING: this is probably not what you want to use in your blueprints file, it selects
		// options for all targets on a device that supports 64-bit binaries, not just the targets
		// that are being compiled for 64-bit.  Its expected use case is binaries like linker and
		// debuggerd that need to know when they are a 32-bit process running on a 64-bit device
		if os.Class == Device {
			if ctx.AConfig().Android64() {
				field := "Android64"
				prefix := "target.android64"
				a.appendProperties(ctx, genProps, targetProp, field, prefix)
			} else {
				field := "Android32"
				prefix := "target.android32"
				a.appendProperties(ctx, genProps, targetProp, field, prefix)
			}

			if arch.ArchType == X86 && (hasArmAbi(arch) ||
				hasArmAndroidArch(ctx.AConfig().Targets[Device])) {
				field := "Arm_on_x86"
				prefix := "target.arm_on_x86"
				a.appendProperties(ctx, genProps, targetProp, field, prefix)
			}
			if arch.ArchType == X86_64 && (hasArmAbi(arch) ||
				hasArmAndroidArch(ctx.AConfig().Targets[Device])) {
				field := "Arm_on_x86_64"
				prefix := "target.arm_on_x86_64"
				a.appendProperties(ctx, genProps, targetProp, field, prefix)
			}
		}
	}
}

func forEachInterface(v reflect.Value, f func(reflect.Value)) {
	switch v.Kind() {
	case reflect.Interface:
		f(v)
	case reflect.Struct:
		for i := 0; i < v.NumField(); i++ {
			forEachInterface(v.Field(i), f)
		}
	case reflect.Ptr:
		forEachInterface(v.Elem(), f)
	default:
		panic(fmt.Errorf("Unsupported kind %s", v.Kind()))
	}
}

// Convert the arch product variables into a list of targets for each os class structs
func decodeTargetProductVariables(config *config) (map[OsClass][]Target, error) {
	variables := config.ProductVariables

	targets := make(map[OsClass][]Target)
	var targetErr error

	addTarget := func(os OsType, archName string, archVariant, cpuVariant *string, abi *[]string) {
		if targetErr != nil {
			return
		}

		arch, err := decodeArch(archName, archVariant, cpuVariant, abi)
		if err != nil {
			targetErr = err
			return
		}

		targets[os.Class] = append(targets[os.Class],
			Target{
				Os:   os,
				Arch: arch,
			})
	}

	if variables.HostArch == nil {
		return nil, fmt.Errorf("No host primary architecture set")
	}

	addTarget(BuildOs, *variables.HostArch, nil, nil, nil)

	if variables.HostSecondaryArch != nil && *variables.HostSecondaryArch != "" {
		addTarget(BuildOs, *variables.HostSecondaryArch, nil, nil, nil)
	}

	if config.Host_bionic != nil && *config.Host_bionic {
		addTarget(LinuxBionic, "x86_64", nil, nil, nil)
	}

	if variables.CrossHost != nil && *variables.CrossHost != "" {
		crossHostOs := osByName(*variables.CrossHost)
		if crossHostOs == NoOsType {
			return nil, fmt.Errorf("Unknown cross host OS %q", *variables.CrossHost)
		}

		if variables.CrossHostArch == nil || *variables.CrossHostArch == "" {
			return nil, fmt.Errorf("No cross-host primary architecture set")
		}

		addTarget(crossHostOs, *variables.CrossHostArch, nil, nil, nil)

		if variables.CrossHostSecondaryArch != nil && *variables.CrossHostSecondaryArch != "" {
			addTarget(crossHostOs, *variables.CrossHostSecondaryArch, nil, nil, nil)
		}
	}

	if variables.DeviceArch != nil && *variables.DeviceArch != "" {
		addTarget(Android, *variables.DeviceArch, variables.DeviceArchVariant,
			variables.DeviceCpuVariant, variables.DeviceAbi)

		if variables.DeviceSecondaryArch != nil && *variables.DeviceSecondaryArch != "" {
			addTarget(Android, *variables.DeviceSecondaryArch,
				variables.DeviceSecondaryArchVariant, variables.DeviceSecondaryCpuVariant,
				variables.DeviceSecondaryAbi)

			deviceArches := targets[Device]
			if deviceArches[0].Arch.ArchType.Multilib == deviceArches[1].Arch.ArchType.Multilib {
				deviceArches[1].Arch.Native = false
			}
		}
	}

	if targetErr != nil {
		return nil, targetErr
	}

	return targets, nil
}

// hasArmAbi returns true if arch has at least one arm ABI
func hasArmAbi(arch Arch) bool {
	for _, abi := range arch.Abi {
		if strings.HasPrefix(abi, "arm") {
			return true
		}
	}
	return false
}

// hasArmArch returns true if targets has at least arm Android arch
func hasArmAndroidArch(targets []Target) bool {
	for _, target := range targets {
		if target.Os == Android && target.Arch.ArchType == Arm {
			return true
		}
	}
	return false
}

type archConfig struct {
	arch        string
	archVariant string
	cpuVariant  string
	abi         []string
}

func getMegaDeviceConfig() []archConfig {
	return []archConfig{
		// armv5 is only used for unbundled apps
		//{"arm", "armv5te", "", []string{"armeabi"}},
		{"arm", "armv7-a", "generic", []string{"armeabi-v7a"}},
		{"arm", "armv7-a-neon", "generic", []string{"armeabi-v7a"}},
		{"arm", "armv7-a-neon", "cortex-a7", []string{"armeabi-v7a"}},
		{"arm", "armv7-a-neon", "cortex-a8", []string{"armeabi-v7a"}},
		{"arm", "armv7-a-neon", "cortex-a9", []string{"armeabi-v7a"}},
		{"arm", "armv7-a-neon", "cortex-a15", []string{"armeabi-v7a"}},
		{"arm", "armv7-a-neon", "cortex-a53", []string{"armeabi-v7a"}},
		{"arm", "armv7-a-neon", "cortex-a53.a57", []string{"armeabi-v7a"}},
		{"arm", "armv7-a-neon", "cortex-a73", []string{"armeabi-v7a"}},
		{"arm", "armv7-a-neon", "denver", []string{"armeabi-v7a"}},
		{"arm", "armv7-a-neon", "krait", []string{"armeabi-v7a"}},
		{"arm", "armv7-a-neon", "kryo", []string{"armeabi-v7a"}},
		{"arm64", "armv8-a", "cortex-a53", []string{"arm64-v8a"}},
		{"arm64", "armv8-a", "cortex-a73", []string{"arm64-v8a"}},
		{"arm64", "armv8-a", "denver64", []string{"arm64-v8a"}},
		{"arm64", "armv8-a", "kryo", []string{"arm64-v8a"}},
		{"mips", "mips32-fp", "", []string{"mips"}},
		{"mips", "mips32r2-fp", "", []string{"mips"}},
		{"mips", "mips32r2-fp-xburst", "", []string{"mips"}},
		//{"mips", "mips32r6", "", []string{"mips"}},
		{"mips", "mips32r2dsp-fp", "", []string{"mips"}},
		{"mips", "mips32r2dspr2-fp", "", []string{"mips"}},
		// mips64r2 is mismatching 64r2 and 64r6 libraries during linking to libgcc
		//{"mips64", "mips64r2", "", []string{"mips64"}},
		{"mips64", "mips64r6", "", []string{"mips64"}},
		{"x86", "", "", []string{"x86"}},
		{"x86", "atom", "", []string{"x86"}},
		{"x86", "haswell", "", []string{"x86"}},
		{"x86", "ivybridge", "", []string{"x86"}},
		{"x86", "sandybridge", "", []string{"x86"}},
		{"x86", "silvermont", "", []string{"x86"}},
		{"x86", "x86_64", "", []string{"x86"}},
		{"x86_64", "", "", []string{"x86_64"}},
		{"x86_64", "haswell", "", []string{"x86_64"}},
		{"x86_64", "ivybridge", "", []string{"x86_64"}},
		{"x86_64", "sandybridge", "", []string{"x86_64"}},
		{"x86_64", "silvermont", "", []string{"x86_64"}},
	}
}

func getNdkAbisConfig() []archConfig {
	return []archConfig{
		{"arm", "armv5te", "", []string{"armeabi"}},
		{"arm64", "armv8-a", "", []string{"arm64-v8a"}},
		{"mips", "mips32-fp", "", []string{"mips"}},
		{"mips64", "mips64r6", "", []string{"mips64"}},
		{"x86", "", "", []string{"x86"}},
		{"x86_64", "", "", []string{"x86_64"}},
	}
}

func decodeArchSettings(archConfigs []archConfig) ([]Target, error) {
	var ret []Target

	for _, config := range archConfigs {
		arch, err := decodeArch(config.arch, &config.archVariant,
			&config.cpuVariant, &config.abi)
		if err != nil {
			return nil, err
		}
		arch.Native = false
		ret = append(ret, Target{
			Os:   Android,
			Arch: arch,
		})
	}

	return ret, nil
}

// Convert a set of strings from product variables into a single Arch struct
func decodeArch(arch string, archVariant, cpuVariant *string, abi *[]string) (Arch, error) {
	stringPtr := func(p *string) string {
		if p != nil {
			return *p
		}
		return ""
	}

	slicePtr := func(p *[]string) []string {
		if p != nil {
			return *p
		}
		return nil
	}

	archType, ok := archTypeMap[arch]
	if !ok {
		return Arch{}, fmt.Errorf("unknown arch %q", arch)
	}

	a := Arch{
		ArchType:    archType,
		ArchVariant: stringPtr(archVariant),
		CpuVariant:  stringPtr(cpuVariant),
		Abi:         slicePtr(abi),
		Native:      true,
	}

	if a.ArchVariant == a.ArchType.Name || a.ArchVariant == "generic" {
		a.ArchVariant = ""
	}

	if a.CpuVariant == a.ArchType.Name || a.CpuVariant == "generic" {
		a.CpuVariant = ""
	}

	for i := 0; i < len(a.Abi); i++ {
		if a.Abi[i] == "" {
			a.Abi = append(a.Abi[:i], a.Abi[i+1:]...)
			i--
		}
	}

	if featureMap, ok := archFeatureMap[archType]; ok {
		a.ArchFeatures = featureMap[a.ArchVariant]
	}

	return a, nil
}

func filterMultilibTargets(targets []Target, multilib string) []Target {
	var ret []Target
	for _, t := range targets {
		if t.Arch.ArchType.Multilib == multilib {
			ret = append(ret, t)
		}
	}
	return ret
}

func getCommonTargets(targets []Target) []Target {
	var ret []Target
	set := make(map[string]bool)

	for _, t := range targets {
		if _, found := set[t.Os.String()]; !found {
			set[t.Os.String()] = true
			ret = append(ret, commonTargetMap[t.Os.String()])
		}
	}

	return ret
}

// Use the module multilib setting to select one or more targets from a target list
func decodeMultilib(multilib string, targets []Target, prefer32 bool) ([]Target, error) {
	buildTargets := []Target{}
	if multilib == "first" {
		if prefer32 {
			multilib = "prefer32"
		} else {
			multilib = "prefer64"
		}
	}
	switch multilib {
	case "common":
		buildTargets = append(buildTargets, getCommonTargets(targets)...)
	case "both":
		if prefer32 {
			buildTargets = append(buildTargets, filterMultilibTargets(targets, "lib32")...)
			buildTargets = append(buildTargets, filterMultilibTargets(targets, "lib64")...)
		} else {
			buildTargets = append(buildTargets, filterMultilibTargets(targets, "lib64")...)
			buildTargets = append(buildTargets, filterMultilibTargets(targets, "lib32")...)
		}
	case "32":
		buildTargets = filterMultilibTargets(targets, "lib32")
	case "64":
		buildTargets = filterMultilibTargets(targets, "lib64")
	case "prefer32":
		buildTargets = filterMultilibTargets(targets, "lib32")
		if len(buildTargets) == 0 {
			buildTargets = filterMultilibTargets(targets, "lib64")
		}
	case "prefer64":
		buildTargets = filterMultilibTargets(targets, "lib64")
		if len(buildTargets) == 0 {
			buildTargets = filterMultilibTargets(targets, "lib32")
		}
	default:
		return nil, fmt.Errorf(`compile_multilib must be "both", "first", "32", "64", or "prefer32" found %q`,
			multilib)
	}

	return buildTargets, nil
}
