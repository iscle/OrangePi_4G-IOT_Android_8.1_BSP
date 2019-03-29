# Copyright (C) 2008 The Android Open Source Project
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

# don't include this package in any target
LOCAL_MODULE_TAGS := optional
# and when built explicitly put it in the data partition
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

# Include both the 32 and 64 bit versions
LOCAL_MULTILIB := both

LOCAL_JAVA_LIBRARIES := voip-common conscrypt org.apache.http.legacy

LOCAL_JNI_SHARED_LIBRARIES := libcts_jni libnativedns_jni \
                              libnativemultinetwork_jni libnativehelper_compat_libc++

# include CtsTestServer as a temporary hack to free net.cts from cts.stub.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := CtsNetTestCases

LOCAL_STATIC_JAVA_LIBRARIES := \
    core-tests-support \
    compatibility-device-util \
    ctstestrunner \
    ctstestserver \
    mockwebserver \
    junit \
    legacy-android-test

# uncomment when b/13249961 is fixed
#LOCAL_SDK_VERSION := current

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

include $(BUILD_CTS_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
