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

# cts-signature-common java library
# =================================

include $(CLEAR_VARS)

# don't include this package in any target
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_MODULE := cts-signature-common

LOCAL_SDK_VERSION := current

include $(BUILD_STATIC_JAVA_LIBRARY)

# signature-hostside java library (for testing)
# ============================================================

include $(CLEAR_VARS)

# These files are for device-side only, so filter-out for host library
LOCAL_DEVICE_ONLY_SOURCES := %/CurrentApi.java %/ApiDocumentParser.java

LOCAL_SRC_FILES := $(filter-out $(LOCAL_DEVICE_ONLY_SOURCES), $(call all-java-files-under, src))

LOCAL_MODULE := signature-hostside

LOCAL_MODULE_TAGS := optional

include $(BUILD_HOST_JAVA_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
