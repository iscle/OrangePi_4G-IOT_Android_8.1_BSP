# Copyright (C) 2010 The Android Open Source Project
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

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libctsos_jni

# Don't include this package in any configuration by default.
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
		CtsOsJniOnLoad.cpp \
		android_os_cts_CpuInstructions.cpp.arm \
		android_os_cts_TaggedPointer.cpp \
		android_os_cts_HardwareName.cpp \
		android_os_cts_OSFeatures.cpp \
		android_os_cts_NoExecutePermissionTest.cpp \
		android_os_cts_SeccompTest.cpp \
		android_os_cts_SharedMemory.cpp

LOCAL_C_INCLUDES := $(JNI_H_INCLUDE)

LOCAL_SHARED_LIBRARIES := libnativehelper_compat_libc++ liblog libdl libandroid
LOCAL_CXX_STL := none

LOCAL_STATIC_LIBRARIES := libc++_static libminijail

# Select the architectures on which seccomp-bpf are supported. This is used to
# include extra test files that will not compile on architectures where it is
# not supported.
ARCH_SUPPORTS_SECCOMP := 1
ifeq ($(strip $(TARGET_ARCH)),mips)
	ARCH_SUPPORTS_SECCOMP = 0
endif
ifeq ($(strip $(TARGET_ARCH)),mips64)
	ARCH_SUPPORTS_SECCOMP = 0
endif

ifeq ($(ARCH_SUPPORTS_SECCOMP),1)
	LOCAL_STATIC_LIBRARIES += external_seccomp_tests

	# This define controls the behavior of OSFeatures.needsSeccompSupport().
	LOCAL_CFLAGS += -DARCH_SUPPORTS_SECCOMP
endif

LOCAL_CFLAGS := -Wno-unused-parameter
LOCAL_CPPFLAGS_arm := -mcpu=generic

include $(BUILD_SHARED_LIBRARY)
