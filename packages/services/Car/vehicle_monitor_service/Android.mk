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
#
LOCAL_PATH:= $(call my-dir)
##################################
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(patsubst ./%,%, $(shell cd $(LOCAL_PATH); \
    find . -name "*.cpp" -and -not -name ".*"))

LOCAL_SRC_FILES := $(filter-out main_vehiclemonitor.cpp, $(LOCAL_SRC_FILES))

LOCAL_C_INCLUDES += \
    frameworks/base/include \
    packages/services/Car/libvehiclemonitor/include

LOCAL_SHARED_LIBRARIES := \
    libbinder

LOCAL_MODULE := libvehiclemonitorservice
LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS  += -Werror

include $(BUILD_STATIC_LIBRARY)

##################################
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    main_vehiclemonitor.cpp

LOCAL_C_INCLUDES += \
    frameworks/base/include \
    packages/services/Car/libvehiclemonitor/include

LOCAL_SHARED_LIBRARIES := \
    libbinder \
    liblog \
    libutils \
    libvehiclemonitor-native

LOCAL_WHOLE_STATIC_LIBRARIES := \
    libvehiclemonitorservice

LOCAL_STRIP_MODULE := keep_symbols

LOCAL_MODULE := vehicle_monitor_service
LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS  += -Werror

include $(BUILD_EXECUTABLE)
