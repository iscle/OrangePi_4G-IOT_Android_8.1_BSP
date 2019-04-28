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
include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

include packages/apps/Car/libs/car-stream-ui-lib/car-stream-ui-lib.mk
include packages/apps/Car/libs/car-apps-common/car-apps-common.mk

include packages/services/Car/car-support-lib/car-support.mk

LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4
LOCAL_STATIC_JAVA_LIBRARIES += car-stream-lib

LOCAL_AAPT_FLAGS += \
        --auto-add-overlay \

LOCAL_AAPT_FLAGS += --extra-packages com.android.car.radio.service
LOCAL_STATIC_JAVA_LIBRARIES += car-radio-service

LOCAL_PACKAGE_NAME := Stream

LOCAL_MODULE_TAGS := optional

#TODO: determine if this service should be a privileged module.
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_DEX_PREOPT := false

# Include support-v7-cardview, if not already included
ifeq (,$(findstring android-support-v7-cardview,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_RESOURCE_DIR += frameworks/support/v7/cardview/res
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.cardview
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-cardview
endif

# Include support-v7-palette, if not already included
ifeq (,$(findstring android-support-v7-palette,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.palette
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-palette
endif

# Include android-support-annotations, if not already included
ifeq (,$(findstring android-support-annotations,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_STATIC_JAVA_LIBRARIES += android-support-annotations
endif

include $(BUILD_PACKAGE)

endif
