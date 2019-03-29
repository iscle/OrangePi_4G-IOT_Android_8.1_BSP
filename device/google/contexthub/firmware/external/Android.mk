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

LOCAL_MODULE := libnanomath_os
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES :=                      \
    arm/arm_sin_cos_f32.c               \
    freebsd/lib/msun/src/e_atan2f.c     \
    freebsd/lib/msun/src/e_expf.c       \
    freebsd/lib/msun/src/s_atanf.c      \


LOCAL_CFLAGS :=    \
    -DFLT_EVAL_METHOD=0 \

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/freebsd/lib/msun/src \
    $(NANOHUB_OS_PATH)/inc \
    $(NANOHUB_OS_PATH)/src/platform/$(AUX_ARCH)/inc \
    $(NANOHUB_OS_PATH)/src/platform/$(AUX_ARCH)/inc/plat/cmsis \
    $(NANOHUB_OS_PATH)/src/cpu/$(AUX_CPU)/inc \

LOCAL_EXPORT_C_INCLUDE_DIRS := \
    $(LOCAL_PATH)/freebsd/inc \

include $(BUILD_NANOHUB_OS_STATIC_LIBRARY)

include $(CLEAR_NANO_VARS)

LOCAL_MODULE := libnanomath
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES :=                      \
    arm/arm_sin_cos_f32.c               \
    freebsd/lib/msun/src/e_atan2f.c     \
    freebsd/lib/msun/src/e_expf.c       \
    freebsd/lib/msun/src/s_atanf.c      \

LOCAL_CFLAGS :=    \
    -DFLT_EVAL_METHOD=0 \

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/freebsd/lib/msun/src \
    $(NANOHUB_OS_PATH)/inc \
    $(NANOHUB_OS_PATH)/src/platform/$(AUX_ARCH)/inc \
    $(NANOHUB_OS_PATH)/src/platform/$(AUX_ARCH)/inc/plat/cmsis \
    $(NANOHUB_OS_PATH)/src/cpu/$(AUX_CPU)/inc \

LOCAL_EXPORT_C_INCLUDE_DIRS := \
    $(LOCAL_PATH)/freebsd/inc \

include $(BUILD_NANOHUB_APP_STATIC_LIBRARY)
