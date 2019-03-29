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

package main

import (
	"bytes"
	"fmt"
	"os"
	"strings"
	"testing"

	bpparser "github.com/google/blueprint/parser"
)

var testCases = []struct {
	desc     string
	in       string
	expected string
}{
	{
		desc: "basic cc_library_shared with comments",
		in: `
#
# Copyright
#

# Module Comment
include $(CLEAR_VARS)
# Name Comment
LOCAL_MODULE := test
# Source comment
LOCAL_SRC_FILES_EXCLUDE := a.c
# Second source comment
LOCAL_SRC_FILES_EXCLUDE += b.c
include $(BUILD_SHARED_LIBRARY)`,
		expected: `
//
// Copyright
//

// Module Comment
cc_library_shared {
    // Name Comment
    name: "test",
    // Source comment
    exclude_srcs: ["a.c"] + ["b.c"], // Second source comment

}`,
	},
	{
		desc: "split local/global include_dirs (1)",
		in: `
include $(CLEAR_VARS)
LOCAL_C_INCLUDES := $(LOCAL_PATH)
include $(BUILD_SHARED_LIBRARY)`,
		expected: `
cc_library_shared {
    local_include_dirs: ["."],
}`,
	},
	{
		desc: "split local/global include_dirs (2)",
		in: `
include $(CLEAR_VARS)
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
include $(BUILD_SHARED_LIBRARY)`,
		expected: `
cc_library_shared {
    local_include_dirs: ["include"],
}`,
	},
	{
		desc: "split local/global include_dirs (3)",
		in: `
include $(CLEAR_VARS)
LOCAL_C_INCLUDES := system/core/include
include $(BUILD_SHARED_LIBRARY)`,
		expected: `
cc_library_shared {
    include_dirs: ["system/core/include"],
}`,
	},
	{
		desc: "split local/global include_dirs (4)",
		in: `
input := testing/include
include $(CLEAR_VARS)
# Comment 1
LOCAL_C_INCLUDES := $(LOCAL_PATH) $(LOCAL_PATH)/include system/core/include $(input)
# Comment 2
LOCAL_C_INCLUDES += $(TOP)/system/core/include $(LOCAL_PATH)/test/include
# Comment 3
include $(BUILD_SHARED_LIBRARY)`,
		expected: `
input = ["testing/include"]
cc_library_shared {
    // Comment 1
    include_dirs: ["system/core/include"] + input + ["system/core/include"], // Comment 2
    local_include_dirs: ["."] + ["include"] + ["test/include"],
    // Comment 3
}`,
	},
	{
		desc: "LOCAL_MODULE_STEM",
		in: `
include $(CLEAR_VARS)
LOCAL_MODULE := libtest
LOCAL_MODULE_STEM := $(LOCAL_MODULE).so
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libtest2
LOCAL_MODULE_STEM := testing.so
include $(BUILD_SHARED_LIBRARY)
`,
		expected: `
cc_library_shared {
    name: "libtest",
    suffix: ".so",
}

cc_library_shared {
    name: "libtest2",
    stem: "testing.so",
}
`,
	},
	{
		desc: "LOCAL_MODULE_HOST_OS",
		in: `
include $(CLEAR_VARS)
LOCAL_MODULE := libtest
LOCAL_MODULE_HOST_OS := linux darwin windows
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libtest2
LOCAL_MODULE_HOST_OS := linux
include $(BUILD_SHARED_LIBRARY)
`,
		expected: `
cc_library_shared {
    name: "libtest",
    target: {
        windows: {
            enabled: true,
        }
    }
}

cc_library_shared {
    name: "libtest2",
    target: {
        darwin: {
            enabled: false,
        }
    }
}
`,
	},
	{
		desc: "LOCAL_RTTI_VALUE",
		in: `
include $(CLEAR_VARS)
LOCAL_MODULE := libtest
LOCAL_RTTI_FLAG := # Empty flag
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libtest2
LOCAL_RTTI_FLAG := -frtti
include $(BUILD_SHARED_LIBRARY)
`,
		expected: `
cc_library_shared {
    name: "libtest",
    rtti: false, // Empty flag
}

cc_library_shared {
    name: "libtest2",
    rtti: true,
}
`,
	},
	{
		desc: "LOCAL_ARM_MODE",
		in: `
include $(CLEAR_VARS)
LOCAL_ARM_MODE := arm
include $(BUILD_SHARED_LIBRARY)
`,
		expected: `
cc_library_shared {
    arch: {
        arm: {
            instruction_set: "arm",
        },
    },
}
`,
	},
	{
		desc: "*.logtags in LOCAL_SRC_FILES",
		in: `
include $(CLEAR_VARS)
LOCAL_SRC_FILES := events.logtags
LOCAL_SRC_FILES += a.c events2.logtags
include $(BUILD_SHARED_LIBRARY)
`,
		expected: `
cc_library_shared {
    logtags: ["events.logtags"] + ["events2.logtags"],
    srcs: ["a.c"],
}
`,
	},
	{
		desc: "LOCAL_LOGTAGS_FILES and *.logtags in LOCAL_SRC_FILES",
		in: `
include $(CLEAR_VARS)
LOCAL_LOGTAGS_FILES := events.logtags
LOCAL_SRC_FILES := events2.logtags
include $(BUILD_SHARED_LIBRARY)
`,
		expected: `
cc_library_shared {
    logtags: ["events.logtags"] + ["events2.logtags"],
}
`,
	},
	{
		desc: "_<OS> suffixes",
		in: `
include $(CLEAR_VARS)
LOCAL_SRC_FILES_darwin := darwin.c
LOCAL_SRC_FILES_linux := linux.c
LOCAL_SRC_FILES_windows := windows.c
include $(BUILD_SHARED_LIBRARY)
`,
		expected: `
cc_library_shared {
    target: {
        darwin: {
            srcs: ["darwin.c"],
        },
        linux: {
            srcs: ["linux.c"],
        },
        windows: {
            srcs: ["windows.c"],
        },
    },
}
`,
	},
	{
		desc: "LOCAL_SANITIZE := never",
		in: `
include $(CLEAR_VARS)
LOCAL_SANITIZE := never
include $(BUILD_SHARED_LIBRARY)
`,
		expected: `
cc_library_shared {
    sanitize: {
        never: true,
    },
}
`,
	},
	{
		desc: "LOCAL_SANITIZE unknown parameter",
		in: `
include $(CLEAR_VARS)
LOCAL_SANITIZE := thread cfi asdf
LOCAL_SANITIZE_DIAG := cfi
LOCAL_SANITIZE_RECOVER := qwert
include $(BUILD_SHARED_LIBRARY)
`,
		expected: `
cc_library_shared {
    sanitize: {
        thread: true,
        cfi: true,
        misc_undefined: ["asdf"],
        diag: {
            cfi: true,
        },
        recover: ["qwert"],
    },
}
`,
	},
	{
		desc: "LOCAL_SANITIZE_RECOVER",
		in: `
include $(CLEAR_VARS)
LOCAL_SANITIZE_RECOVER := shift-exponent
include $(BUILD_SHARED_LIBRARY)
`,
		expected: `
cc_library_shared {
    sanitize: {
	recover: ["shift-exponent"],
    },
}
`,
	},
	{
		desc: "version_script in LOCAL_LDFLAGS",
		in: `
include $(CLEAR_VARS)
LOCAL_LDFLAGS := -Wl,--link-opt -Wl,--version-script,$(LOCAL_PATH)/exported32.map
include $(BUILD_SHARED_LIBRARY)
`,
		expected: `
cc_library_shared {
    ldflags: ["-Wl,--link-opt"],
    version_script: "exported32.map",
}
`,
	},
	{
		desc: "Handle TOP",
		in: `
include $(CLEAR_VARS)
LOCAL_C_INCLUDES := $(TOP)/system/core/include $(TOP)
include $(BUILD_SHARED_LIBRARY)
`,
		expected: `
cc_library_shared {
	include_dirs: ["system/core/include", "."],
}
`,
	},
	{
		desc: "Remove LOCAL_MODULE_TAGS optional",
		in: `
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
include $(BUILD_SHARED_LIBRARY)
`,

		expected: `
cc_library_shared {

}
`,
	},
	{
		desc: "Keep LOCAL_MODULE_TAGS non-optional",
		in: `
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := debug
include $(BUILD_SHARED_LIBRARY)
`,

		expected: `
cc_library_shared {
	tags: ["debug"],
}
`,
	},
}

func reformatBlueprint(input string) string {
	file, errs := bpparser.Parse("<testcase>", bytes.NewBufferString(input), bpparser.NewScope(nil))
	if len(errs) > 0 {
		for _, err := range errs {
			fmt.Fprintln(os.Stderr, err)
		}
		panic(fmt.Sprintf("%d parsing errors in testcase:\n%s", len(errs), input))
	}

	res, err := bpparser.Print(file)
	if err != nil {
		panic(fmt.Sprintf("Error printing testcase: %q", err))
	}

	return string(res)
}

func TestEndToEnd(t *testing.T) {
	for i, test := range testCases {
		expected := reformatBlueprint(test.expected)

		got, errs := convertFile(fmt.Sprintf("<testcase %d>", i), bytes.NewBufferString(test.in))
		if len(errs) > 0 {
			t.Errorf("Unexpected errors: %q", errs)
			continue
		}

		if got != expected {
			t.Errorf("failed testcase '%s'\ninput:\n%s\n\nexpected:\n%s\ngot:\n%s\n", test.desc, strings.TrimSpace(test.in), expected, got)
		}
	}
}
