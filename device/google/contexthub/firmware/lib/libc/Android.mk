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

LOCAL_PATH := $(call my-dir)

include $(CLEAR_NANO_VARS)

src_files :=            \
    memcmp.c            \
    memmove.c           \
    memset.c            \
    strcasecmp.c        \
    strlen.c            \
    strncpy.c           \

LOCAL_MODULE := libnanolibc_os
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(src_files)

LOCAL_SRC_FILES_cortexm4 += memcpy-armv7m.S
LOCAL_SRC_FILES_x86 += memcpy.c

include $(BUILD_NANOHUB_OS_STATIC_LIBRARY)

include $(CLEAR_NANO_VARS)

LOCAL_MODULE := libnanolibc
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(src_files)
LOCAL_SRC_FILES +=      \
    memcpy.c            \
    aeabi.cpp           \
    cxa.cpp             \
    new.cpp             \
    crt.c               \

LOCAL_C_INCLUDES = $(LOCAL_PATH)
LOCAL_EXPORT_C_INCLUDE_DIRS := \
    $(LOCAL_C_INCLUDES)

include $(BUILD_NANOHUB_APP_STATIC_LIBRARY)
