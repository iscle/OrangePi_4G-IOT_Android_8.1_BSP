# Copyright (C) 2017 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := CtsVendorJniTestCases

# Don't include this package in any target.
LOCAL_MODULE_TAGS := optional

# Include both the 32 and 64 bit versions
LOCAL_MULTILIB := both

# When built, explicitly put it in the vendor partition.
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR_APPS)

LOCAL_STATIC_JAVA_LIBRARIES := android-support-test ctstestrunner

LOCAL_JNI_SHARED_LIBRARIES := libvendorjnitest

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_SDK_VERSION := current

# TODO(jiyong) make this really a part of CTS
# This can't be done right now since VNDK runtime restriction is optional
# at least for O-MR1.
# Tag this module as a cts test artifact
#LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

include $(BUILD_CTS_PACKAGE)

# Include the associated library's makefile.
include $(call all-makefiles-under,$(LOCAL_PATH))
