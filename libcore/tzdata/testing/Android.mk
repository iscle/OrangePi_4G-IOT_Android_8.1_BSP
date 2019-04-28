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

LOCAL_PATH:= $(call my-dir)

# Library of test-support classes for tzdata updates. Shared between CTS and other tests.
include $(CLEAR_VARS)
LOCAL_MODULE := tzdata-testing
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-java-files-under, src/main)
LOCAL_JAVACFLAGS := -encoding UTF-8
LOCAL_JAVA_LIBRARIES := core-oj core-libart
LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
include $(BUILD_STATIC_JAVA_LIBRARY)

# Library of test-support classes for tzdata updates. Shared between CTS and other tests.
include $(CLEAR_VARS)
LOCAL_MODULE := tzdata-testing-host
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-java-files-under, src/main)
LOCAL_JAVACFLAGS := -encoding UTF-8
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
include $(BUILD_HOST_JAVA_LIBRARY)

# Host version of the above library. For libcore host testing.
include $(CLEAR_VARS)
LOCAL_MODULE := tzdata-testing-hostdex
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-java-files-under, src/main)
LOCAL_JAVA_LIBRARIES := core-oj-hostdex core-libart-hostdex
LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_JAVACFLAGS := -encoding UTF-8
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
include $(BUILD_HOST_DALVIK_STATIC_JAVA_LIBRARY)
