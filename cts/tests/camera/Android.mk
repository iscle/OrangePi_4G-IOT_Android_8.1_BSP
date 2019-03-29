# Copyright (C) 2015 The Android Open Source Project
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

# CtsCameraUtils package

include $(CLEAR_VARS)

LOCAL_STATIC_JAVA_LIBRARIES := compatibility-device-util \
	mockito-target-minus-junit4 \
	android-ex-camera2

LOCAL_SRC_FILES := \
	$(call all-java-files-under, utils/src) \
	$(call all-renderscript-files-under, utils/src)

LOCAL_MODULE := CtsCameraUtils

LOCAL_SDK_VERSION := current
-include cts/error_prone_rules_tests.mk
include $(BUILD_STATIC_JAVA_LIBRARY)

# CtsCameraTestCases package

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

# Include both the 32 and 64 bit versions
LOCAL_MULTILIB := both

LOCAL_STATIC_JAVA_LIBRARIES := compatibility-device-util \
	ctstestrunner \
	mockito-target-minus-junit4 \
	android-ex-camera2 \
	CtsCameraUtils

LOCAL_JNI_SHARED_LIBRARIES := \
	libctscamera2_jni \
	libnativehelper_compat_libc++

LOCAL_NDK_STL_VARIANT := c++_shared

LOCAL_SRC_FILES := \
	$(call all-java-files-under, src) \
	$(call all-renderscript-files-under, src)

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_PACKAGE_NAME := CtsCameraTestCases

LOCAL_SDK_VERSION := test_current

LOCAL_JAVA_LIBRARIES := android.test.runner

cts_runtime_hint := 120

include $(BUILD_CTS_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
