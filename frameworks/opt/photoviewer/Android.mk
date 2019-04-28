# Copyright (C) 2012 The Android Open Source Project
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

##################################################
# Build appcompat library
include $(CLEAR_VARS)

appcompat_res_dirs := appcompat/res res
LOCAL_MODULE := libphotoviewer_appcompat

LOCAL_STATIC_ANDROID_LIBRARIES := \
    android-support-annotations \
    android-support-compat \
    android-support-core-ui \
    android-support-core-utils \
    android-support-fragment \
    android-support-v7-appcompat

LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := \
     $(call all-java-files-under, src) \
     $(call all-java-files-under, appcompat/src) \
     $(call all-logtags-files-under, src)

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(appcompat_res_dirs))
LOCAL_USE_AAPT2 := true

include $(BUILD_STATIC_JAVA_LIBRARY)

##################################################
# Build non-appcompat library
include $(CLEAR_VARS)

activity_res_dirs := activity/res res
LOCAL_MODULE := libphotoviewer

LOCAL_STATIC_ANDROID_LIBRARIES := \
    android-support-annotations \
    android-support-compat \
    android-support-core-ui \
    android-support-core-utils \
    android-support-fragment

LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := \
     $(call all-java-files-under, src) \
     $(call all-java-files-under, activity/src) \
     $(call all-logtags-files-under, src)

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(activity_res_dirs))
LOCAL_USE_AAPT2 := true

include $(BUILD_STATIC_JAVA_LIBRARY)



##################################################
# Build all sub-directories

include $(call all-makefiles-under,$(LOCAL_PATH))
