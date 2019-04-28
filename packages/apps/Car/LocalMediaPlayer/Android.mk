#
# Copyright (C) 2016 The Android Open Source Project
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

ifneq ($(TARGET_BUILD_PDK), true)

LOCAL_PATH:= $(call my-dir)

# Proto dependencies
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-proto-files-under, proto)
LOCAL_MODULE := LocalMediaPlayer-proto
LOCAL_PROTOC_OPTIMIZE_TYPE := nano
LOCAL_PROTOC_FLAGS := --proto_path=$(LOCAL_PATH)/proto

include $(BUILD_STATIC_JAVA_LIBRARY)


# Actual Package

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_PACKAGE_NAME := LocalMediaPlayer

LOCAL_CERTIFICATE := platform

LOCAL_MODULE_TAGS := optional

LOCAL_PRIVILEGED_MODULE := true

include packages/apps/Car/libs/car-stream-ui-lib/car-stream-ui-lib.mk

LOCAL_STATIC_JAVA_LIBRARIES += \
        car-stream-lib \
        LocalMediaPlayer-proto

LOCAL_STATIC_ANDROID_LIBRARIES := \
        android-support-v4 \
        android-support-design

LOCAL_USE_AAPT2 := true

# Include support-v7-appcompat, if not already included
ifeq (,$(findstring android-support-v7-appcompat,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_RESOURCE_DIR += frameworks/support/v7/appcompat/res
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.appcompat
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-appcompat
endif

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_DEX_PREOPT := false

include packages/services/Car/car-support-lib/car-support.mk

include $(BUILD_PACKAGE)

endif
