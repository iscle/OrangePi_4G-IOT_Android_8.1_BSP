#
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
#
LOCAL_PATH := $(call my-dir)

# dasm (dalvik assembler) java library
# ============================================================

include $(CLEAR_VARS)
LOCAL_IS_HOST_MODULE := true
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_MODULE := dasm
LOCAL_SRC_FILES := etc/dasm
LOCAL_ADDITIONAL_DEPENDENCIES := $(HOST_OUT_JAVA_LIBRARIES)/dasm$(COMMON_JAVA_PACKAGE_SUFFIX)
include $(BUILD_PREBUILT)

INTERNAL_DALVIK_MODULES += $(LOCAL_INSTALLED_MODULE)

include $(LOCAL_PATH)/src/Android.mk
