#
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
#

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_JNI_SHARED_LIBRARIES := libstaticsharednativelibprovider

LOCAL_PACKAGE_NAME := CtsStaticSharedNativeLibProvider

LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_AAPT_FLAGS := --shared-lib

LOCAL_EXPORT_PACKAGE_RESOURCES := true

LOCAL_MULTILIB := both

LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/keysets/cts-keyset-test-b

include $(BUILD_CTS_SUPPORT_PACKAGE)

#########################################################################
# Build Shared Library
#########################################################################

LOCAL_PATH:= $(LOCAL_PATH)/native

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_CFLAGS := -Wall -Wextra -Werror

LOCAL_SRC_FILES := $(call all-cpp-files-under)

LOCAL_MODULE := libstaticsharednativelibprovider

LOCAL_C_INCLUDES := $(LOCAL_PATH)/native/version.h

LOCAL_CXX_STL := none

include $(BUILD_SHARED_LIBRARY)
