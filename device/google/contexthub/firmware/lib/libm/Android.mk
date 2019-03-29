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

LOCAL_MODULE := libnanolibm
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES :=      \
    ef_asin.c           \
    ef_atan2.c          \
    ef_exp.c            \
    ef_fmod.c           \
    ef_pow.c            \
    ef_rem_pio2.c       \
    ef_sqrt.c           \
    kf_cos.c            \
    kf_rem_pio2.c       \
    kf_sin.c            \
    sf_atan.c           \
    sf_cos.c            \
    sf_floor.c          \
    sf_fpclassify.c     \
    sf_round.c          \
    sf_scalbn.c         \
    sf_sin.c            \
    wf_asin.c           \
    wf_atan2.c          \
    wf_exp.c            \
    wf_fmod.c           \
    wf_pow.c            \

LOCAL_CFLAGS :=         \
    -DARM_MATH_CM4      \
    -D__FPU_PRESENT     \
    -D_IEEE_LIBM        \

include $(BUILD_NANOHUB_APP_STATIC_LIBRARY)
