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

################################################################################
#
# Nanoapp Libc/Libm/Builtins build helper script
#
################################################################################

# Libm #########################################################################

LIBM_PATH := $(NANOHUB_DIR)/lib/libm

CFLAGS += -D_IEEE_LIBM

SRCS += $(LIBM_PATH)/ef_atan2.c
SRCS += $(LIBM_PATH)/ef_asin.c
SRCS += $(LIBM_PATH)/ef_exp.c
SRCS += $(LIBM_PATH)/ef_fmod.c
SRCS += $(LIBM_PATH)/ef_pow.c
SRCS += $(LIBM_PATH)/ef_rem_pio2.c
SRCS += $(LIBM_PATH)/ef_sqrt.c
SRCS += $(LIBM_PATH)/kf_cos.c
SRCS += $(LIBM_PATH)/kf_rem_pio2.c
SRCS += $(LIBM_PATH)/kf_sin.c
SRCS += $(LIBM_PATH)/sf_atan.c
SRCS += $(LIBM_PATH)/sf_cos.c
SRCS += $(LIBM_PATH)/sf_floor.c
SRCS += $(LIBM_PATH)/sf_fpclassify.c
SRCS += $(LIBM_PATH)/sf_round.c
SRCS += $(LIBM_PATH)/sf_scalbn.c
SRCS += $(LIBM_PATH)/sf_sin.c
SRCS += $(LIBM_PATH)/wf_atan2.c
SRCS += $(LIBM_PATH)/wf_asin.c
SRCS += $(LIBM_PATH)/wf_exp.c
SRCS += $(LIBM_PATH)/wf_fmod.c
SRCS += $(LIBM_PATH)/wf_pow.c

# Libc #########################################################################

LIBC_PATH := $(NANOHUB_DIR)/lib/libc

SRCS += $(LIBC_PATH)/memcmp.c
#SRCS += $(LIBC_PATH)/memcpy-armv7m.S
SRCS += $(LIBC_PATH)/memcpy.c
SRCS += $(LIBC_PATH)/memmove.c
SRCS += $(LIBC_PATH)/memset.c
SRCS += $(LIBC_PATH)/strcasecmp.c
SRCS += $(LIBC_PATH)/strncpy.c
SRCS += $(LIBC_PATH)/strlen.c

# C++ support

SRCS += $(LIBC_PATH)/crt.c
SRCS += $(LIBC_PATH)/aeabi.cpp
SRCS += $(LIBC_PATH)/cxa.cpp
SRCS += $(LIBC_PATH)/new.cpp
CFLAGS += -I$(LIBC_PATH)
CFLAGS += -DNANOHUB_PROVIDES_CXX_SUPPORT

# Builtins #####################################################################

BUILTINS_PATH := $(NANOHUB_DIR)/lib/builtins

SRCS += $(BUILTINS_PATH)/aeabi_ldivmod.S
SRCS += $(BUILTINS_PATH)/aeabi_uldivmod.S
SRCS += $(BUILTINS_PATH)/divdi3.c
SRCS += $(BUILTINS_PATH)/divmoddi4.c
SRCS += $(BUILTINS_PATH)/moddi3.c
SRCS += $(BUILTINS_PATH)/udivmoddi4.c
SRCS += $(BUILTINS_PATH)/umoddi3.c
SRCS += $(BUILTINS_PATH)/aeabi_f2d.c
SRCS += $(BUILTINS_PATH)/aeabi_llsl.c
SRCS += $(BUILTINS_PATH)/aeabi_llsr.c
SRCS += $(BUILTINS_PATH)/aeabi_ul2f.c
SRCS += $(BUILTINS_PATH)/aeabi_l2f.c
SRCS += $(BUILTINS_PATH)/aeabi_f2ulz.c
CFLAGS += -I$(BUILTINS_PATH)
