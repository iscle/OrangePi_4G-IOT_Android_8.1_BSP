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

NANOTOOL_VERSION := 1.2.0

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    androidcontexthub.cpp \
    apptohostevent.cpp \
    calibrationfile.cpp \
    contexthub.cpp \
    log.cpp \
    logevent.cpp \
    nanomessage.cpp \
    nanotool.cpp \
    resetreasonevent.cpp \
    sensorevent.cpp

# JSON file handling from chinook
COMMON_UTILS_DIR := ../common
LOCAL_SRC_FILES += \
    $(COMMON_UTILS_DIR)/file.cpp \
    $(COMMON_UTILS_DIR)/JSONObject.cpp

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/$(COMMON_UTILS_DIR)

LOCAL_SHARED_LIBRARIES := \
    liblog \
    libstagefright_foundation \
    libutils

LOCAL_CFLAGS += -Wall -Werror -Wextra
LOCAL_CFLAGS += -std=c++11
LOCAL_CFLAGS += -DNANOTOOL_VERSION_STR='"version $(NANOTOOL_VERSION)"'

LOCAL_MODULE := nanotool

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_OWNER := google

include $(BUILD_EXECUTABLE)
