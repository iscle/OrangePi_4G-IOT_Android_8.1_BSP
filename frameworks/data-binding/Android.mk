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

LOCAL_PATH := $(call my-dir)

my_prebuilt_version := 1.0-rc0

#######################################
include $(CLEAR_VARS)
LOCAL_MODULE := databinding-baselibrary
LOCAL_SRC_FILES := prebuilds/$(my_prebuilt_version)/databinding-baseLibrary.jar
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_SUFFIX := $(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_BUILT_MODULE_STEM := javalib.jar
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

#######################################
include $(CLEAR_VARS)
LOCAL_MODULE := databinding-library
LOCAL_SRC_FILES := prebuilds/$(my_prebuilt_version)/databinding-library.aar
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_SUFFIX := $(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_BUILT_MODULE_STEM := javalib.jar
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

#######################################
include $(CLEAR_VARS)
LOCAL_MODULE := databinding-adapters
LOCAL_SRC_FILES := prebuilds/$(my_prebuilt_version)/databinding-adapters.aar
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_SUFFIX := $(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_BUILT_MODULE_STEM := javalib.jar
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

my_prebuilt_version :=
