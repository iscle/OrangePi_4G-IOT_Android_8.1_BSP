package main

import (
	mkparser "android/soong/androidmk/parser"
	"fmt"
	"strings"

	bpparser "github.com/google/blueprint/parser"
)

const (
	clear_vars = "__android_mk_clear_vars"
)

type bpVariable struct {
	name         string
	variableType bpparser.Type
}

type variableAssignmentContext struct {
	file    *bpFile
	prefix  string
	mkvalue *mkparser.MakeString
	append  bool
}

var rewriteProperties = map[string](func(variableAssignmentContext) error){
	// custom functions
	"LOCAL_C_INCLUDES":            localIncludeDirs,
	"LOCAL_EXPORT_C_INCLUDE_DIRS": exportIncludeDirs,
	"LOCAL_LDFLAGS":               ldflags,
	"LOCAL_MODULE_CLASS":          prebuiltClass,
	"LOCAL_MODULE_STEM":           stem,
	"LOCAL_MODULE_HOST_OS":        hostOs,
	"LOCAL_SRC_FILES":             srcFiles,
	"LOCAL_SANITIZE":              sanitize(""),
	"LOCAL_SANITIZE_DIAG":         sanitize("diag."),

	// composite functions
	"LOCAL_MODULE_TAGS": includeVariableIf(bpVariable{"tags", bpparser.ListType}, not(valueDumpEquals("optional"))),

	// skip functions
	"LOCAL_ADDITIONAL_DEPENDENCIES": skip, // TODO: check for only .mk files?
	"LOCAL_CPP_EXTENSION":           skip,
	"LOCAL_MODULE_SUFFIX":           skip, // TODO
	"LOCAL_PATH":                    skip, // Nothing to do, except maybe avoid the "./" in paths?
	"LOCAL_PRELINK_MODULE":          skip, // Already phased out
}

// adds a group of properties all having the same type
func addStandardProperties(propertyType bpparser.Type, properties map[string]string) {
	for key, val := range properties {
		rewriteProperties[key] = includeVariable(bpVariable{val, propertyType})
	}
}

func init() {
	addStandardProperties(bpparser.StringType,
		map[string]string{
			"LOCAL_MODULE":                  "name",
			"LOCAL_CXX_STL":                 "stl",
			"LOCAL_STRIP_MODULE":            "strip",
			"LOCAL_MULTILIB":                "compile_multilib",
			"LOCAL_ARM_MODE_HACK":           "instruction_set",
			"LOCAL_SDK_VERSION":             "sdk_version",
			"LOCAL_NDK_STL_VARIANT":         "stl",
			"LOCAL_JAR_MANIFEST":            "manifest",
			"LOCAL_JARJAR_RULES":            "jarjar_rules",
			"LOCAL_CERTIFICATE":             "certificate",
			"LOCAL_PACKAGE_NAME":            "name",
			"LOCAL_MODULE_RELATIVE_PATH":    "relative_install_path",
			"LOCAL_PROTOC_OPTIMIZE_TYPE":    "proto.type",
			"LOCAL_MODULE_OWNER":            "owner",
			"LOCAL_RENDERSCRIPT_TARGET_API": "renderscript.target_api",
		})
	addStandardProperties(bpparser.ListType,
		map[string]string{
			"LOCAL_SRC_FILES_EXCLUDE":             "exclude_srcs",
			"LOCAL_HEADER_LIBRARIES":              "header_libs",
			"LOCAL_SHARED_LIBRARIES":              "shared_libs",
			"LOCAL_STATIC_LIBRARIES":              "static_libs",
			"LOCAL_WHOLE_STATIC_LIBRARIES":        "whole_static_libs",
			"LOCAL_SYSTEM_SHARED_LIBRARIES":       "system_shared_libs",
			"LOCAL_ASFLAGS":                       "asflags",
			"LOCAL_CLANG_ASFLAGS":                 "clang_asflags",
			"LOCAL_CFLAGS":                        "cflags",
			"LOCAL_CONLYFLAGS":                    "conlyflags",
			"LOCAL_CPPFLAGS":                      "cppflags",
			"LOCAL_REQUIRED_MODULES":              "required",
			"LOCAL_LDLIBS":                        "host_ldlibs",
			"LOCAL_CLANG_CFLAGS":                  "clang_cflags",
			"LOCAL_YACCFLAGS":                     "yaccflags",
			"LOCAL_SANITIZE_RECOVER":              "sanitize.recover",
			"LOCAL_LOGTAGS_FILES":                 "logtags",
			"LOCAL_EXPORT_HEADER_LIBRARY_HEADERS": "export_header_lib_headers",
			"LOCAL_EXPORT_SHARED_LIBRARY_HEADERS": "export_shared_lib_headers",
			"LOCAL_EXPORT_STATIC_LIBRARY_HEADERS": "export_static_lib_headers",
			"LOCAL_INIT_RC":                       "init_rc",
			"LOCAL_TIDY_FLAGS":                    "tidy_flags",
			// TODO: This is comma-separated, not space-separated
			"LOCAL_TIDY_CHECKS":           "tidy_checks",
			"LOCAL_RENDERSCRIPT_INCLUDES": "renderscript.include_dirs",
			"LOCAL_RENDERSCRIPT_FLAGS":    "renderscript.flags",

			"LOCAL_JAVA_RESOURCE_DIRS":    "resource_dirs",
			"LOCAL_JAVACFLAGS":            "javacflags",
			"LOCAL_DX_FLAGS":              "dxflags",
			"LOCAL_JAVA_LIBRARIES":        "libs",
			"LOCAL_STATIC_JAVA_LIBRARIES": "static_libs",
			"LOCAL_AIDL_INCLUDES":         "aidl.include_dirs",
			"LOCAL_AAPT_FLAGS":            "aaptflags",
			"LOCAL_PACKAGE_SPLITS":        "package_splits",
			"LOCAL_COMPATIBILITY_SUITE":   "test_suites",
		})
	addStandardProperties(bpparser.BoolType,
		map[string]string{
			// Bool properties
			"LOCAL_IS_HOST_MODULE":          "host",
			"LOCAL_CLANG":                   "clang",
			"LOCAL_FORCE_STATIC_EXECUTABLE": "static_executable",
			"LOCAL_NATIVE_COVERAGE":         "native_coverage",
			"LOCAL_NO_CRT":                  "nocrt",
			"LOCAL_ALLOW_UNDEFINED_SYMBOLS": "allow_undefined_symbols",
			"LOCAL_RTTI_FLAG":               "rtti",
			"LOCAL_NO_STANDARD_LIBRARIES":   "no_standard_libraries",
			"LOCAL_PACK_MODULE_RELOCATIONS": "pack_relocations",
			"LOCAL_TIDY":                    "tidy",
			"LOCAL_PROPRIETARY_MODULE":      "proprietary",
			"LOCAL_VENDOR_MODULE":           "vendor",

			"LOCAL_EXPORT_PACKAGE_RESOURCES": "export_package_resources",
		})
}

type listSplitFunc func(bpparser.Expression) (string, bpparser.Expression, error)

func emptyList(value bpparser.Expression) bool {
	if list, ok := value.(*bpparser.List); ok {
		return len(list.Values) == 0
	}
	return false
}

func splitBpList(val bpparser.Expression, keyFunc listSplitFunc) (lists map[string]bpparser.Expression, err error) {
	lists = make(map[string]bpparser.Expression)

	switch val := val.(type) {
	case *bpparser.Operator:
		listsA, err := splitBpList(val.Args[0], keyFunc)
		if err != nil {
			return nil, err
		}

		listsB, err := splitBpList(val.Args[1], keyFunc)
		if err != nil {
			return nil, err
		}

		for k, v := range listsA {
			if !emptyList(v) {
				lists[k] = v
			}
		}

		for k, vB := range listsB {
			if emptyList(vB) {
				continue
			}

			if vA, ok := lists[k]; ok {
				expression := val.Copy().(*bpparser.Operator)
				expression.Args = [2]bpparser.Expression{vA, vB}
				lists[k] = expression
			} else {
				lists[k] = vB
			}
		}
	case *bpparser.Variable:
		key, value, err := keyFunc(val)
		if err != nil {
			return nil, err
		}
		if value.Type() == bpparser.ListType {
			lists[key] = value
		} else {
			lists[key] = &bpparser.List{
				Values: []bpparser.Expression{value},
			}
		}
	case *bpparser.List:
		for _, v := range val.Values {
			key, value, err := keyFunc(v)
			if err != nil {
				return nil, err
			}
			l := lists[key]
			if l == nil {
				l = &bpparser.List{}
			}
			l.(*bpparser.List).Values = append(l.(*bpparser.List).Values, value)
			lists[key] = l
		}
	default:
		panic(fmt.Errorf("unexpected type %t", val))
	}

	return lists, nil
}

func splitLocalGlobalPath(value bpparser.Expression) (string, bpparser.Expression, error) {
	switch v := value.(type) {
	case *bpparser.Variable:
		if v.Name == "LOCAL_PATH" {
			return "local", &bpparser.String{
				Value: ".",
			}, nil
		} else {
			// TODO: Should we split variables?
			return "global", value, nil
		}
	case *bpparser.Operator:
		if v.Type() != bpparser.StringType {
			return "", nil, fmt.Errorf("splitLocalGlobalPath expected a string, got %s", value.Type)
		}

		if v.Operator != '+' {
			return "global", value, nil
		}

		firstOperand := v.Args[0]
		secondOperand := v.Args[1]
		if firstOperand.Type() != bpparser.StringType {
			return "global", value, nil
		}

		if _, ok := firstOperand.(*bpparser.Operator); ok {
			return "global", value, nil
		}

		if variable, ok := firstOperand.(*bpparser.Variable); !ok || variable.Name != "LOCAL_PATH" {
			return "global", value, nil
		}

		local := secondOperand
		if s, ok := secondOperand.(*bpparser.String); ok {
			if strings.HasPrefix(s.Value, "/") {
				s.Value = s.Value[1:]
			}
		}
		return "local", local, nil
	case *bpparser.String:
		return "global", value, nil
	default:
		return "", nil, fmt.Errorf("splitLocalGlobalPath expected a string, got %s", value.Type)

	}
}

func localIncludeDirs(ctx variableAssignmentContext) error {
	val, err := makeVariableToBlueprint(ctx.file, ctx.mkvalue, bpparser.ListType)
	if err != nil {
		return err
	}

	lists, err := splitBpList(val, splitLocalGlobalPath)
	if err != nil {
		return err
	}

	if global, ok := lists["global"]; ok && !emptyList(global) {
		err = setVariable(ctx.file, ctx.append, ctx.prefix, "include_dirs", global, true)
		if err != nil {
			return err
		}
	}

	if local, ok := lists["local"]; ok && !emptyList(local) {
		err = setVariable(ctx.file, ctx.append, ctx.prefix, "local_include_dirs", local, true)
		if err != nil {
			return err
		}
	}

	return nil
}

func exportIncludeDirs(ctx variableAssignmentContext) error {
	val, err := makeVariableToBlueprint(ctx.file, ctx.mkvalue, bpparser.ListType)
	if err != nil {
		return err
	}

	lists, err := splitBpList(val, splitLocalGlobalPath)
	if err != nil {
		return err
	}

	if local, ok := lists["local"]; ok && !emptyList(local) {
		err = setVariable(ctx.file, ctx.append, ctx.prefix, "export_include_dirs", local, true)
		if err != nil {
			return err
		}
		ctx.append = true
	}

	// Add any paths that could not be converted to local relative paths to export_include_dirs
	// anyways, they will cause an error if they don't exist and can be fixed manually.
	if global, ok := lists["global"]; ok && !emptyList(global) {
		err = setVariable(ctx.file, ctx.append, ctx.prefix, "export_include_dirs", global, true)
		if err != nil {
			return err
		}
	}

	return nil
}

func stem(ctx variableAssignmentContext) error {
	val, err := makeVariableToBlueprint(ctx.file, ctx.mkvalue, bpparser.StringType)
	if err != nil {
		return err
	}
	varName := "stem"

	if exp, ok := val.(*bpparser.Operator); ok && exp.Operator == '+' {
		if variable, ok := exp.Args[0].(*bpparser.Variable); ok && variable.Name == "LOCAL_MODULE" {
			varName = "suffix"
			val = exp.Args[1]
		}
	}

	return setVariable(ctx.file, ctx.append, ctx.prefix, varName, val, true)
}

func hostOs(ctx variableAssignmentContext) error {
	val, err := makeVariableToBlueprint(ctx.file, ctx.mkvalue, bpparser.ListType)
	if err != nil {
		return err
	}

	inList := func(s string) bool {
		for _, v := range val.(*bpparser.List).Values {
			if v.(*bpparser.String).Value == s {
				return true
			}
		}
		return false
	}

	falseValue := &bpparser.Bool{
		Value: false,
	}

	trueValue := &bpparser.Bool{
		Value: true,
	}

	if inList("windows") {
		err = setVariable(ctx.file, ctx.append, "target.windows", "enabled", trueValue, true)
	}

	if !inList("linux") && err == nil {
		err = setVariable(ctx.file, ctx.append, "target.linux", "enabled", falseValue, true)
	}

	if !inList("darwin") && err == nil {
		err = setVariable(ctx.file, ctx.append, "target.darwin", "enabled", falseValue, true)
	}

	return err
}

func splitSrcsLogtags(value bpparser.Expression) (string, bpparser.Expression, error) {
	switch v := value.(type) {
	case *bpparser.Variable:
		// TODO: attempt to split variables?
		return "srcs", value, nil
	case *bpparser.Operator:
		// TODO: attempt to handle expressions?
		return "srcs", value, nil
	case *bpparser.String:
		if strings.HasSuffix(v.Value, ".logtags") {
			return "logtags", value, nil
		}
		return "srcs", value, nil
	default:
		return "", nil, fmt.Errorf("splitSrcsLogtags expected a string, got %s", value.Type())
	}

}

func srcFiles(ctx variableAssignmentContext) error {
	val, err := makeVariableToBlueprint(ctx.file, ctx.mkvalue, bpparser.ListType)
	if err != nil {
		return err
	}

	lists, err := splitBpList(val, splitSrcsLogtags)

	if srcs, ok := lists["srcs"]; ok && !emptyList(srcs) {
		err = setVariable(ctx.file, ctx.append, ctx.prefix, "srcs", srcs, true)
		if err != nil {
			return err
		}
	}

	if logtags, ok := lists["logtags"]; ok && !emptyList(logtags) {
		err = setVariable(ctx.file, true, ctx.prefix, "logtags", logtags, true)
		if err != nil {
			return err
		}
	}

	return nil
}

func sanitize(sub string) func(ctx variableAssignmentContext) error {
	return func(ctx variableAssignmentContext) error {
		val, err := makeVariableToBlueprint(ctx.file, ctx.mkvalue, bpparser.ListType)
		if err != nil {
			return err
		}

		if _, ok := val.(*bpparser.List); !ok {
			return fmt.Errorf("unsupported sanitize expression")
		}

		misc := &bpparser.List{}

		for _, v := range val.(*bpparser.List).Values {
			switch v := v.(type) {
			case *bpparser.Variable, *bpparser.Operator:
				ctx.file.errorf(ctx.mkvalue, "unsupported sanitize expression")
			case *bpparser.String:
				switch v.Value {
				case "never", "address", "coverage", "thread", "undefined", "cfi":
					bpTrue := &bpparser.Bool{
						Value: true,
					}
					err = setVariable(ctx.file, false, ctx.prefix, "sanitize."+sub+v.Value, bpTrue, true)
					if err != nil {
						return err
					}
				default:
					misc.Values = append(misc.Values, v)
				}
			default:
				return fmt.Errorf("sanitize expected a string, got %s", v.Type())
			}
		}

		if len(misc.Values) > 0 {
			err = setVariable(ctx.file, false, ctx.prefix, "sanitize."+sub+"misc_undefined", misc, true)
			if err != nil {
				return err
			}
		}

		return err
	}
}

func prebuiltClass(ctx variableAssignmentContext) error {
	class := ctx.mkvalue.Value(nil)
	if v, ok := prebuiltTypes[class]; ok {
		ctx.file.scope.Set("BUILD_PREBUILT", v)
	} else {
		// reset to default
		ctx.file.scope.Set("BUILD_PREBUILT", "prebuilt")
	}
	return nil
}

func ldflags(ctx variableAssignmentContext) error {
	val, err := makeVariableToBlueprint(ctx.file, ctx.mkvalue, bpparser.ListType)
	if err != nil {
		return err
	}

	lists, err := splitBpList(val, func(value bpparser.Expression) (string, bpparser.Expression, error) {
		// Anything other than "-Wl,--version_script," + LOCAL_PATH + "<path>" matches ldflags
		exp1, ok := value.(*bpparser.Operator)
		if !ok {
			return "ldflags", value, nil
		}

		exp2, ok := exp1.Args[0].(*bpparser.Operator)
		if !ok {
			return "ldflags", value, nil
		}

		if s, ok := exp2.Args[0].(*bpparser.String); !ok || s.Value != "-Wl,--version-script," {
			return "ldflags", value, nil
		}

		if v, ok := exp2.Args[1].(*bpparser.Variable); !ok || v.Name != "LOCAL_PATH" {
			ctx.file.errorf(ctx.mkvalue, "Unrecognized version-script")
			return "ldflags", value, nil
		}

		s, ok := exp1.Args[1].(*bpparser.String)
		if !ok {
			ctx.file.errorf(ctx.mkvalue, "Unrecognized version-script")
			return "ldflags", value, nil
		}

		s.Value = strings.TrimPrefix(s.Value, "/")

		return "version", s, nil
	})
	if err != nil {
		return err
	}

	if ldflags, ok := lists["ldflags"]; ok && !emptyList(ldflags) {
		err = setVariable(ctx.file, ctx.append, ctx.prefix, "ldflags", ldflags, true)
		if err != nil {
			return err
		}
	}

	if version_script, ok := lists["version"]; ok && !emptyList(version_script) {
		if len(version_script.(*bpparser.List).Values) > 1 {
			ctx.file.errorf(ctx.mkvalue, "multiple version scripts found?")
		}
		err = setVariable(ctx.file, false, ctx.prefix, "version_script", version_script.(*bpparser.List).Values[0], true)
		if err != nil {
			return err
		}
	}

	return nil
}

// given a conditional, returns a function that will insert a variable assignment or not, based on the conditional
func includeVariableIf(bpVar bpVariable, conditional func(ctx variableAssignmentContext) bool) func(ctx variableAssignmentContext) error {
	return func(ctx variableAssignmentContext) error {
		var err error
		if conditional(ctx) {
			err = includeVariableNow(bpVar, ctx)
		}
		return err
	}
}

// given a variable, returns a function that will always insert a variable assignment
func includeVariable(bpVar bpVariable) func(ctx variableAssignmentContext) error {
	return includeVariableIf(bpVar, always)
}

func includeVariableNow(bpVar bpVariable, ctx variableAssignmentContext) error {
	var val bpparser.Expression
	var err error
	val, err = makeVariableToBlueprint(ctx.file, ctx.mkvalue, bpVar.variableType)
	if err == nil {
		err = setVariable(ctx.file, ctx.append, ctx.prefix, bpVar.name, val, true)
	}
	return err
}

// given a function that returns a bool, returns a function that returns the opposite
func not(conditional func(ctx variableAssignmentContext) bool) func(ctx variableAssignmentContext) bool {
	return func(ctx variableAssignmentContext) bool {
		return !conditional(ctx)
	}
}

// returns a function that tells whether mkvalue.Dump equals the given query string
func valueDumpEquals(textToMatch string) func(ctx variableAssignmentContext) bool {
	return func(ctx variableAssignmentContext) bool {
		return (ctx.mkvalue.Dump() == textToMatch)
	}
}

func always(ctx variableAssignmentContext) bool {
	return true
}

func skip(ctx variableAssignmentContext) error {
	return nil
}

// Shorter suffixes of other suffixes must be at the end of the list
var propertyPrefixes = []struct{ mk, bp string }{
	{"arm", "arch.arm"},
	{"arm64", "arch.arm64"},
	{"mips", "arch.mips"},
	{"mips64", "arch.mips64"},
	{"x86", "arch.x86"},
	{"x86_64", "arch.x86_64"},
	{"32", "multilib.lib32"},
	// 64 must be after x86_64
	{"64", "multilib.lib64"},
	{"darwin", "target.darwin"},
	{"linux", "target.linux"},
	{"windows", "target.windows"},
}

var conditionalTranslations = map[string]map[bool]string{
	"($(HOST_OS),darwin)": {
		true:  "target.darwin",
		false: "target.not_darwin"},
	"($(HOST_OS), darwin)": {
		true:  "target.darwin",
		false: "target.not_darwin"},
	"($(HOST_OS),windows)": {
		true:  "target.windows",
		false: "target.not_windows"},
	"($(HOST_OS), windows)": {
		true:  "target.windows",
		false: "target.not_windows"},
	"($(HOST_OS),linux)": {
		true:  "target.linux",
		false: "target.not_linux"},
	"($(HOST_OS), linux)": {
		true:  "target.linux",
		false: "target.not_linux"},
	"($(BUILD_OS),darwin)": {
		true:  "target.darwin",
		false: "target.not_darwin"},
	"($(BUILD_OS), darwin)": {
		true:  "target.darwin",
		false: "target.not_darwin"},
	"($(BUILD_OS),linux)": {
		true:  "target.linux",
		false: "target.not_linux"},
	"($(BUILD_OS), linux)": {
		true:  "target.linux",
		false: "target.not_linux"},
	"(,$(TARGET_BUILD_APPS))": {
		false: "product_variables.unbundled_build"},
	"($(TARGET_BUILD_PDK),true)": {
		true: "product_variables.pdk"},
	"($(TARGET_BUILD_PDK), true)": {
		true: "product_variables.pdk"},
}

func mydir(args []string) string {
	return "."
}

func allJavaFilesUnder(args []string) string {
	dir := ""
	if len(args) > 0 {
		dir = strings.TrimSpace(args[0])
	}

	return fmt.Sprintf("%s/**/*.java", dir)
}

func allSubdirJavaFiles(args []string) string {
	return "**/*.java"
}

var moduleTypes = map[string]string{
	"BUILD_SHARED_LIBRARY":        "cc_library_shared",
	"BUILD_STATIC_LIBRARY":        "cc_library_static",
	"BUILD_HOST_SHARED_LIBRARY":   "cc_library_host_shared",
	"BUILD_HOST_STATIC_LIBRARY":   "cc_library_host_static",
	"BUILD_HEADER_LIBRARY":        "cc_library_headers",
	"BUILD_EXECUTABLE":            "cc_binary",
	"BUILD_HOST_EXECUTABLE":       "cc_binary_host",
	"BUILD_NATIVE_TEST":           "cc_test",
	"BUILD_HOST_NATIVE_TEST":      "cc_test_host",
	"BUILD_NATIVE_BENCHMARK":      "cc_benchmark",
	"BUILD_HOST_NATIVE_BENCHMARK": "cc_benchmark_host",

	"BUILD_JAVA_LIBRARY":             "java_library",
	"BUILD_STATIC_JAVA_LIBRARY":      "java_library_static",
	"BUILD_HOST_JAVA_LIBRARY":        "java_library_host",
	"BUILD_HOST_DALVIK_JAVA_LIBRARY": "java_library_host_dalvik",
	"BUILD_PACKAGE":                  "android_app",
}

var prebuiltTypes = map[string]string{
	"SHARED_LIBRARIES": "cc_prebuilt_library_shared",
	"STATIC_LIBRARIES": "cc_prebuilt_library_static",
	"EXECUTABLES":      "cc_prebuilt_binary",
	"JAVA_LIBRARIES":   "prebuilt_java_library",
}

var soongModuleTypes = map[string]bool{}

func androidScope() mkparser.Scope {
	globalScope := mkparser.NewScope(nil)
	globalScope.Set("CLEAR_VARS", clear_vars)
	globalScope.SetFunc("my-dir", mydir)
	globalScope.SetFunc("all-java-files-under", allJavaFilesUnder)
	globalScope.SetFunc("all-subdir-java-files", allSubdirJavaFiles)

	for k, v := range moduleTypes {
		globalScope.Set(k, v)
		soongModuleTypes[v] = true
	}
	for _, v := range prebuiltTypes {
		soongModuleTypes[v] = true
	}

	return globalScope
}
