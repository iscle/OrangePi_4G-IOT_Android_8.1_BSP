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

ifneq ($(NANOHUB_OS_PATH),)

include $(CLEAR_NANO_VARS)

LOCAL_MODULE := libnanohub_os
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    os/core/appSec.c \
    os/core/eventQ.c \
    os/core/floatRt.c \
    os/core/heap.c \
    os/core/hostIntf.c \
    os/core/hostIntfI2c.c \
    os/core/hostIntfSpi.c \
    os/core/nanohubCommand.c \
    os/core/nanohub_chre.c \
    os/core/osApi.c \
    os/core/printf.c \
    os/core/sensors.c \
    os/core/seos.c \
    os/core/simpleQ.c \
    os/core/syscall.c \
    os/core/slab.c \
    os/core/spi.c \
    os/core/timer.c \
    os/core/trylock.c \
    os/algos/ap_hub_sync.c \

LOCAL_C_INCLUDES := \
    $(NANOHUB_OS_PATH)/external/freebsd/inc \
    $(NANOHUB_OS_PATH)/os/algos \

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_C_INCLUDES)

include $(BUILD_NANOHUB_OS_STATIC_LIBRARY)

########################################################
# BOOT LOADER BINARY
########################################################

include $(CLEAR_NANO_VARS)

LOCAL_MODULE := nanohub_bl

LOCAL_SRC_FILES := \
    os/core/bl.c   \

LOCAL_STATIC_LIBRARIES := \
    libnanohub_common_bl \
    libnanohub_os \
    libnanolibc_os \

LOCAL_OBJCOPY_SECT_cortexm4 := .bl .data .eedata

include $(BUILD_NANOHUB_BL_EXECUTABLE)

########################################################
# NANOHUB OS BINARY
########################################################

include $(CLEAR_NANO_VARS)

LOCAL_MODULE := nanohub_os

LOCAL_CFLAGS := \
    -DPLATFORM_HW_VER=0 \

LOCAL_WHOLE_STATIC_LIBRARIES := \
    libnanohub_os \

LOCAL_STATIC_LIBRARIES := \
    libnanomath_os \
    libnanolibc_os \

LOCAL_OBJCOPY_SECT_cortexm4 := .data .text

include $(BUILD_NANOHUB_OS_EXECUTABLE)

include $(call first-makefiles-under,$(NANOHUB_OS_PATH))

endif # NANOHUB_OS_PATH
