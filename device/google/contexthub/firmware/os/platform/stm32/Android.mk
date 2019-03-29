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

########################################################
# COMMON OS & BL defs
########################################################

NANOHUB_PALTFORM_PATH := $(LOCAL_PATH)

########################################################
# BOOT LOADER BINARY
########################################################

include $(CLEAR_NANO_VARS)

LOCAL_MODULE := libnanohub_bl_stm32
LOCAL_AUX_ARCH := stm32

LOCAL_SRC_FILES :=      \
    bl.c                \
    gpio.c              \
    pwr.c               \

include $(BUILD_NANOHUB_BL_STATIC_LIBRARY)

########################################################
# NANOHUB OS BINARY
########################################################

include $(CLEAR_NANO_VARS)

LOCAL_MODULE := libnanohub_os_stm32
LOCAL_AUX_ARCH := stm32

LOCAL_SRC_FILES := \
    apInt.c \
    crc.c \
    crt_stm32.c \
    dma.c \
    eeData.c \
    exti.c \
    gpio.c \
    hostIntf.c \
    i2c.c \
    mpu.c \
    platform.c \
    pwr.c \
    rtc.c \
    spi.c \
    syscfg.c \
    usart.c \
    wdt.c \

include $(BUILD_NANOHUB_OS_STATIC_LIBRARY)
