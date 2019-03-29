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

include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES := libcutils libc

LOCAL_SRC_FILES := \
        flash.c \
        i2c.c \
        spi.c \
        stm32_bl.c \
        stm32f4_crc.c \
        uart.c

LOCAL_CFLAGS := -Wall -Werror -Wextra

LOCAL_MODULE := stm32_flash

LOCAL_MODULE_TAGS := optional

include $(BUILD_EXECUTABLE)
