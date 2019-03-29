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

LOCAL_CFLAGS += \
    -Wall \
    -Werror \
    \
    -Waddress \
    -Wempty-body \
    -Wenum-compare \
    -Wlogical-op \
    -Wmissing-declarations \
    -Wpointer-arith \
    -Wshadow \
    \
    -fdata-sections \
    -ffunction-sections \
    -fno-strict-aliasing \
    -fno-unwind-tables \
    -fstack-reuse=all \
    -fvisibility=hidden \

LOCAL_CPPFLAGS +=       \
    -std=c++11          \
    -fno-exceptions     \
    -fno-rtti           \

LOCAL_LDFLAGS +=                        \
    -nostartfiles                       \
    -nostdlib                           \
    -Wl,--gc-sections                   \
    -Wl,--no-undefined                  \
    -Wl,--no-allow-shlib-undefined      \

LOCAL_CFLAGS_cortexm4 += \
    -mthumb \
    -mcpu=cortex-m4 \
    -march=armv7e-m \
    -mfloat-abi=softfp \
    -mfpu=fpv4-sp-d16 \
    -mno-thumb-interwork \
    -ffast-math \
    -fsingle-precision-constant \
    -DARM \
    -DUSE_NANOHUB_FLOAT_RUNTIME \
    -DARM_MATH_CM4 \
    -D__FPU_PRESENT \
    -DCPU_NUM_PERSISTENT_RAM_BITS=32 \

LOCAL_CFLAGS_x86 += \
    -march=core2 \
    -msse2 \
    -DSYSCALL_VARARGS_PARAMS_PASSED_AS_PTRS \

LOCAL_CFLAGS_stm32 += \
    -DPLATFORM_HW_VER=0 \

# CHRE-specific
LOCAL_CFLAGS += \
    -DCHRE_MESSAGE_TO_HOST_MAX_SIZE=128 \
    -DCHRE_NO_DOUBLE_SUPPORT \

# DEBUG/RELEASE-specific
DEBUG ?= -DDEBUG
RELEASE ?=

ifneq (,$(filter userdebug eng, $(TARGET_BUILD_VARIANT)))
LOCAL_CFLAGS += \
    $(DEBUG) \

else
LOCAL_CFLAGS += \
    $(RELEASE) \

endif
