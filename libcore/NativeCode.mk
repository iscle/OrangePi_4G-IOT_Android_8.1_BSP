# -*- mode: makefile -*-
# Copyright (C) 2007 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# Definitions for building the native code needed for the core library.
#

#
# Common definitions for host and target.
#

# These two definitions are used to help sanity check what's put in
# sub.mk. See, the "error" directives immediately below.
core_magic_local_target := ...//::default:://...
core_local_path := $(LOCAL_PATH)

# Include a submakefile, resolve its source file locations,
# and stick them on core_src_files.  The submakefiles are
# free to append to LOCAL_SRC_FILES, LOCAL_C_INCLUDES,
# LOCAL_SHARED_LIBRARIES, or LOCAL_STATIC_LIBRARIES, but nothing
# else. All other LOCAL_* variables will be ignored.
#
# $(1): directory containing the makefile to include
define include-core-native-dir
    LOCAL_SRC_FILES :=
    include $(LOCAL_PATH)/$(1)/sub.mk
    ifneq ($$(LOCAL_MODULE),$(core_magic_local_target))
        $$(error $(LOCAL_PATH)/$(1)/sub.mk should not include CLEAR_VARS \
            or define LOCAL_MODULE)
    endif
    ifneq ($$(LOCAL_PATH),$(core_local_path))
        $$(error $(LOCAL_PATH)/$(1)/sub.mk should not define LOCAL_PATH)
    endif
    core_src_files += $$(addprefix $(1)/,$$(LOCAL_SRC_FILES))
    LOCAL_SRC_FILES :=
endef

define include-openjdk-native-dir
    LOCAL_SRC_FILES :=
    include $(LOCAL_PATH)/$(1)/openjdksub.mk
    openjdk_core_src_files += $$(addprefix $(1)/,$$(LOCAL_SRC_FILES))
    LOCAL_SRC_FILES :=
endef

# Set up the default state. Note: We use CLEAR_VARS here, even though
# we aren't quite defining a new rule yet, to make sure that the
# sub.mk files don't see anything stray from the last rule that was
# set up.

include $(CLEAR_VARS)
LOCAL_MODULE := $(core_magic_local_target)
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/NativeCode.mk
core_src_files :=
openjdk_core_src_files :=

#Include the sub.mk for openjdk.
$(foreach dir, \
    ojluni/src/main/native, \
    $(eval $(call include-openjdk-native-dir,$(dir))))

# Include the sub.mk files.
$(foreach dir, \
    dalvik/src/main/native luni/src/main/native, \
    $(eval $(call include-core-native-dir,$(dir))))

# Extract out the allowed LOCAL_* variables.
core_c_includes := libcore/include $(LOCAL_C_INCLUDES)
core_shared_libraries := $(LOCAL_SHARED_LIBRARIES)
core_static_libraries := $(LOCAL_STATIC_LIBRARIES)
libart_cflags := $(LOCAL_CFLAGS) -Wall -Wextra -Werror
core_cppflags += -DU_USING_ICU_NAMESPACE=0
# TODO(narayan): Prune down this list of exclusions once the underlying
# issues have been fixed. Most of these are small changes except for
# -Wunused-parameter.
openjdk_cflags := $(libart_cflags) \
    -Wno-unused-parameter \
    -Wno-unused-variable \
    -Wno-parentheses-equality \
    -Wno-constant-logical-operand \
    -Wno-sometimes-uninitialized

core_test_files := \
  luni/src/test/native/dalvik_system_JniTest.cpp \
  luni/src/test/native/libcore_java_io_FileTest.cpp \
  luni/src/test/native/libcore_java_lang_ThreadTest.cpp \
  luni/src/test/native/libcore_java_nio_BufferTest.cpp \
  luni/src/test/native/libcore_util_NativeAllocationRegistryTest.cpp \

#
# Build for the target (device).
#

include $(CLEAR_VARS)
LOCAL_CFLAGS += $(libart_cflags)
LOCAL_CPPFLAGS += $(core_cppflags)
LOCAL_SRC_FILES += $(core_src_files)
LOCAL_C_INCLUDES += $(core_c_includes)
LOCAL_SHARED_LIBRARIES += $(core_shared_libraries) libcrypto libdl libexpat libicuuc libicui18n libnativehelper libz libutils
LOCAL_STATIC_LIBRARIES += $(core_static_libraries) libziparchive libbase
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libjavacore
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/NativeCode.mk
LOCAL_CXX_STL := libc++
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_CFLAGS += $(libart_cflags)
LOCAL_CPPFLAGS += $(core_cppflags)
ifeq ($(TARGET_ARCH),arm)
# Ignore "note: the mangling of 'va_list' has changed in GCC 4.4"
LOCAL_CFLAGS += -Wno-psabi
endif

# Define the rules.
LOCAL_CFLAGS += $(openjdk_cflags)
LOCAL_SRC_FILES := $(openjdk_core_src_files)
LOCAL_C_INCLUDES := $(core_c_includes)
LOCAL_SHARED_LIBRARIES := $(core_shared_libraries) libcrypto libicuuc libssl libz
LOCAL_SHARED_LIBRARIES += libopenjdkjvm libnativehelper libdl
LOCAL_STATIC_LIBRARIES := $(core_static_libraries) libfdlibm
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libopenjdk
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/ojluni/NOTICE
LOCAL_CXX_STL := libc++
include $(BUILD_SHARED_LIBRARY)

# Debug version of libopenjdk. Depends on libopenjdkjvmd.
include $(CLEAR_VARS)

LOCAL_CFLAGS += $(libart_cflags)
LOCAL_CPPFLAGS += $(core_cppflags)
ifeq ($(TARGET_ARCH),arm)
# Ignore "note: the mangling of 'va_list' has changed in GCC 4.4"
LOCAL_CFLAGS += -Wno-psabi
endif

LOCAL_CFLAGS += $(openjdk_cflags)
LOCAL_SRC_FILES := $(openjdk_core_src_files)
LOCAL_C_INCLUDES := $(core_c_includes)
LOCAL_SHARED_LIBRARIES := $(core_shared_libraries) libcrypto libicuuc libssl libz
LOCAL_SHARED_LIBRARIES += libopenjdkjvmd libnativehelper libdl
LOCAL_STATIC_LIBRARIES := $(core_static_libraries) libfdlibm
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libopenjdkd
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/ojluni/NOTICE
LOCAL_CXX_STL := libc++
include $(BUILD_SHARED_LIBRARY)

# Test JNI library.
ifeq ($(LIBCORE_SKIP_TESTS),)

include $(CLEAR_VARS)
LOCAL_CFLAGS += $(libart_cflags)
LOCAL_CPPFLAGS += $(core_cppflags)
LOCAL_SRC_FILES += $(core_test_files)
LOCAL_C_INCLUDES += libcore/include
LOCAL_SHARED_LIBRARIES += libnativehelper_compat_libc++
LOCAL_MODULE_TAGS := optional
LOCAL_STRIP_MODULE := keep_symbols
LOCAL_MODULE := libjavacoretests
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/NativeCode.mk
LOCAL_CXX_STL := libc++
include $(BUILD_SHARED_LIBRARY)

endif # LIBCORE_SKIP_TESTS

# Set of gtest unit tests.
include $(CLEAR_VARS)
# Add -fno-builtin so that the compiler doesn't attempt to inline
# memcpy calls that are not really aligned.
LOCAL_CFLAGS += $(libart_cflags) -fno-builtin
LOCAL_CPPFLAGS += $(core_cppflags)
LOCAL_SRC_FILES += \
  luni/src/test/native/libcore_io_Memory_test.cpp \

LOCAL_C_INCLUDES += libcore/include
LOCAL_MODULE_TAGS := debug
LOCAL_MODULE := libjavacore-unit-tests
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/NativeCode.mk
LOCAL_SHARED_LIBRARIES := libnativehelper
LOCAL_CXX_STL := libc++
include $(BUILD_NATIVE_TEST)

# Set of benchmarks for libjavacore functions.
include $(CLEAR_VARS)
LOCAL_CFLAGS += $(libart_cflags)
LOCAL_CPPFLAGS += $(core_cppflags)
LOCAL_SRC_FILES += \
  luni/src/benchmark/native/libcore_io_Memory_bench.cpp \

LOCAL_C_INCLUDES += libcore/include
LOCAL_MODULE_TAGS := debug
LOCAL_MODULE := libjavacore-benchmarks
LOCAL_COMPATIBILITY_SUITE := device-tests
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/NativeCode.mk
LOCAL_SHARED_LIBRARIES := libnativehelper
LOCAL_CXX_STL := libc++
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := $(LOCAL_MODULE)32
LOCAL_MODULE_STEM_64 := $(LOCAL_MODULE)64
include $(BUILD_NATIVE_BENCHMARK)


#
# Build for the host.
#

ifeq ($(HOST_OS),linux)

include $(CLEAR_VARS)
LOCAL_CLANG := true
LOCAL_SRC_FILES += $(core_src_files)
LOCAL_CFLAGS += $(libart_cflags)
LOCAL_C_INCLUDES += $(core_c_includes)
LOCAL_CPPFLAGS += $(core_cppflags)
LOCAL_LDLIBS += -ldl -lpthread
ifeq ($(HOST_OS),linux)
LOCAL_LDLIBS += -lrt
endif
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libjavacore
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/NativeCode.mk
LOCAL_SHARED_LIBRARIES += $(core_shared_libraries) libexpat libicuuc libicui18n libcrypto libz-host libziparchive
LOCAL_STATIC_LIBRARIES += $(core_static_libraries)
LOCAL_MULTILIB := both
LOCAL_CXX_STL := libc++
include $(BUILD_HOST_SHARED_LIBRARY)

# Debug version of libopenjdk (host). Depends on libopenjdkjvmd.
include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(openjdk_core_src_files)
LOCAL_C_INCLUDES := $(core_c_includes)
LOCAL_CFLAGS := -D_LARGEFILE64_SOURCE -D_GNU_SOURCE -DLINUX -D__GLIBC__ # Sigh.
LOCAL_CFLAGS += $(openjdk_cflags)
LOCAL_SHARED_LIBRARIES := $(core_shared_libraries) libicuuc libcrypto libz-host
LOCAL_SHARED_LIBRARIES += libopenjdkjvmd libnativehelper
LOCAL_STATIC_LIBRARIES := $(core_static_libraries) libfdlibm
LOCAL_MODULE_TAGS := optional
LOCAL_LDLIBS += -ldl -lpthread -lrt
LOCAL_MODULE := libopenjdkd
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/ojluni/NOTICE
LOCAL_MULTILIB := both
include $(BUILD_HOST_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(openjdk_core_src_files)
LOCAL_C_INCLUDES := $(core_c_includes)
LOCAL_CFLAGS := -D_LARGEFILE64_SOURCE -D_GNU_SOURCE -DLINUX -D__GLIBC__ # Sigh.
LOCAL_CFLAGS += $(openjdk_cflags)
LOCAL_SHARED_LIBRARIES := $(core_shared_libraries) libicuuc libcrypto libz-host
LOCAL_SHARED_LIBRARIES += libopenjdkjvm libnativehelper
LOCAL_STATIC_LIBRARIES := $(core_static_libraries) libfdlibm
LOCAL_MODULE_TAGS := optional
LOCAL_LDLIBS += -ldl -lpthread -lrt
LOCAL_MODULE := libopenjdk
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/ojluni/NOTICE
LOCAL_MULTILIB := both
include $(BUILD_HOST_SHARED_LIBRARY)

ifeq ($(LIBCORE_SKIP_TESTS),)
    include $(CLEAR_VARS)
    LOCAL_CLANG := true
    LOCAL_SRC_FILES += $(core_test_files)
    LOCAL_CFLAGS += $(libart_cflags)
    LOCAL_C_INCLUDES += libcore/include
    LOCAL_CPPFLAGS += $(core_cppflags)
    LOCAL_LDLIBS += -ldl -lpthread
    LOCAL_MODULE_TAGS := optional
    LOCAL_MODULE := libjavacoretests
    LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/NativeCode.mk
    LOCAL_SHARED_LIBRARIES := libnativehelper
    LOCAL_MULTILIB := both
    LOCAL_CXX_STL := libc++
    include $(BUILD_HOST_SHARED_LIBRARY)
endif # LIBCORE_SKIP_TESTS

endif # HOST_OS == linux
