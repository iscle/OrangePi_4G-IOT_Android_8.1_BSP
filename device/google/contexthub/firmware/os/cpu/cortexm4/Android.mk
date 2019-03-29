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

include $(CLEAR_NANO_VARS)

LOCAL_MODULE := libnanohub_bl_cortexm4
LOCAL_AUX_CPU := cortexm4

LOCAL_SRC_FILES := \
    atomic.c \
    atomicBitset.c \
    cpu.c \

include $(BUILD_NANOHUB_BL_STATIC_LIBRARY)

include $(CLEAR_NANO_VARS)

LOCAL_MODULE := libnanohub_os_cortexm4
LOCAL_AUX_CPU := cortexm4

LOCAL_SRC_FILES := \
    appSupport.c \
    atomic.c \
    atomicBitset.c \
    cpu.c \
    cpuMath.c \
    pendsv.c \

include $(BUILD_NANOHUB_OS_STATIC_LIBRARY)
