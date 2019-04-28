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

LOCAL_PACKAGE_NAME := OverviewApp

LOCAL_OVERRIDES_PACKAGES += Launcher2 Launcher3

LOCAL_MODULE_TAGS := optional

include packages/apps/Car/libs/car-apps-common/car-apps-common.mk
include packages/apps/Car/libs/car-stream-ui-lib/car-stream-ui-lib.mk

LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4
LOCAL_STATIC_JAVA_LIBRARIES += car-stream-lib

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_DEX_PREOPT := false

# Include android-support-annotations, if not already included
ifeq (,$(findstring android-support-annotations,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_STATIC_JAVA_LIBRARIES += android-support-annotations
endif

# Include support-v7-appcompat, if not already included
ifeq (,$(findstring android-support-v7-appcompat,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_RESOURCE_DIR += frameworks/support/v7/appcompat/res
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.appcompat
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-appcompat
endif

# Include support-v7-recyclerview, if not already included
ifeq (,$(findstring android-support-v7-recyclerview,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_RESOURCE_DIR += frameworks/support/v7/recyclerview/res
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.recyclerview
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-recyclerview
endif

# Include support-v7-cardview, if not already included
ifeq (,$(findstring android-support-v7-cardview,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_RESOURCE_DIR += frameworks/support/v7/cardview/res
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.cardview
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-cardview
endif

include $(BUILD_PACKAGE)

##################################################################

include $(CLEAR_VARS)

include $(BUILD_MULTI_PREBUILT)

endif
