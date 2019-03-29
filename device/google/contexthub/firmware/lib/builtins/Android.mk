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

LOCAL_MODULE := libnanobuiltins
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES_cortexm4 := \
    aeabi_ldivmod.S         \
    aeabi_uldivmod.S        \
    divdi3.c                \
    divmoddi4.c             \
    moddi3.c                \
    udivmoddi4.c            \
    umoddi3.c               \
    aeabi_f2d.c             \
    aeabi_llsl.c            \
    aeabi_llsr.c            \
    aeabi_ul2f.c            \
    aeabi_l2f.c             \
    aeabi_f2ulz.c           \

LOCAL_C_INCLUDES = $(LOCAL_PATH)
LOCAL_EXPORT_C_INCLUDE_DIRS := \
    $(LOCAL_C_INCLUDES)

include $(BUILD_NANOHUB_APP_STATIC_LIBRARY)
