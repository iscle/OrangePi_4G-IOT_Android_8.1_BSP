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

LOCAL_SRC_FILES := \
  src/android/media/cts/CodecImage.java \
  src/android/media/cts/YUVImage.java \
  src/android/media/cts/CodecUtils.java

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := ctsmediautil

LOCAL_SDK_VERSION := current

include $(BUILD_STATIC_JAVA_LIBRARY)

include $(CLEAR_VARS)

# don't include this package in any target
LOCAL_MODULE_TAGS := optional

# and when built explicitly put it in the data partition
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_DEX_PREOPT := false

LOCAL_PROGUARD_ENABLED := disabled

# include both the 32 and 64 bit versions
LOCAL_MULTILIB := both

LOCAL_STATIC_JAVA_LIBRARIES := \
    compatibility-device-util \
    ctsdeviceutillegacy \
    ctsmediautil \
    ctstestrunner \
    ctstestserver \
    junit \
    legacy-android-test \
    ndkaudio \
    testng

LOCAL_JNI_SHARED_LIBRARIES := \
    libaudio_jni \
    libctsimagereader_jni \
    libctsmediadrm_jni \
    libctsmediacodec_jni \
    libnativehelper_compat_libc++ \
    libndkaudioLib

# do not compress VP9 video files
LOCAL_AAPT_FLAGS := -0 .vp9
LOCAL_AAPT_FLAGS += -0 .ts

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := CtsMediaTestCases

# uncomment when b/13249737 is fixed
#LOCAL_SDK_VERSION := current

LOCAL_JAVA_LIBRARIES += android.test.runner org.apache.http.legacy

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

include $(BUILD_CTS_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
