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

NANOHUB_PALTFORM_PATH := $(LOCAL_PATH)

########################################################
# NANOHUB OS BINARY
########################################################

include $(CLEAR_NANO_VARS)

LOCAL_MODULE := libnanohub_os_native
LOCAL_AUX_ARCH := native

LOCAL_SRC_FILES := \
    hostIntf.c \
    i2c.c \
    platform.c \
    rtc.c \
    spi.c \

include $(BUILD_NANOHUB_OS_STATIC_LIBRARY)
