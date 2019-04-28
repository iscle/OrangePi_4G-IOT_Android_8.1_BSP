# Copyright 2016 The Android Open Source Project
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

#
# libhdrplusclient
#
include $(CLEAR_VARS)

LOCAL_SRC_FILES :=  \
    EaselManagerClient.cpp \
    HdrPlusClientUtils.cpp

LOCAL_SHARED_LIBRARIES := liblog

# For AOSP builds, use dummy implementation.
ifeq ($(wildcard vendor/google_easel),)
    LOCAL_CFLAGS += -DUSE_DUMMY_IMPL=1
else
    LOCAL_CFLAGS += -DUSE_DUMMY_IMPL=0
    LOCAL_SHARED_LIBRARIES += libhdrplusclientimpl
endif

LOCAL_HEADER_LIBRARIES := \
    libsystem_headers \
    libutils_headers
LOCAL_EXPORT_HEADER_LIBRARY_HEADERS := \
    libutils_headers

LOCAL_C_INCLUDES += \
    $(LOCAL_PATH)/include \
    hardware/google/easel/camera/include

LOCAL_CFLAGS += -Wall -Wextra -Werror

LOCAL_EXPORT_C_INCLUDE_DIRS += \
    $(LOCAL_PATH)/include \
    hardware/google/easel/camera/include

LOCAL_MODULE:= libhdrplusclient
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_OWNER := google

include $(BUILD_SHARED_LIBRARY)
