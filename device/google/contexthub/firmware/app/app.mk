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
# NanoApp C/C++ Makefile Utils
#
################################################################################

# Configuration ################################################################

# Toolchain Prefix
ifndef CROSS_COMPILE
  $(error Please set the environment variable CROSS_COMPILE to the complete \
          path to the toolchain directory plus the binary prefix, e.g. export \
          CROSS_COMPILE=~/bin/gcc-arm-none-eabi-4_8-2014q3/bin/arm-none-eabi-)
endif

PREFIX = $(CROSS_COMPILE)
TOOLCHAIN_DIR = $(shell dirname `which $(CROSS_COMPILE)gcc`)/..

# NANOHUB_DIR is relative to CWD (which is always APP Makefile dir)

NANOAPP_POSTPROCESS := $(NANOHUB_DIR)/../util/nanoapp_postprocess/nanoapp_postprocess
NANOAPP_SIGN := $(NANOHUB_DIR)/../util/nanoapp_sign/nanoapp_sign

# TOP_RELPATH is ANDROID_TOP relative to NANOHUB_DIR
TOP_RELPATH := ../../../..
TOP_ABSPATH := $(realpath $(NANOHUB_DIR)/$(TOP_RELPATH))

# for local variants there is always a path; for out-of-tree variants there may be
# - a $(VARIANT) soft link under firmware/variant subdir, or
# - VARIANT_PATH is found as vendor/<vendor_name>/<variant_name>/nanohub, or
# - explicitly provided in VARIANT_CONFIG_PATH (path, where <variant>_conf.mk is located)
#
ifeq ($(VARIANT_CONFIG_PATH),)
  variant_conf := $(wildcard $(TOP_ABSPATH)/vendor/*/*/nanohub/$(VARIANT)_conf.mk)
  ifeq ($(words $(variant_conf)),1)
    VARIANT_CONFIG_PATH := $(patsubst $(TOP_ABSPATH)/%/$(VARIANT)_conf.mk,%,$(variant_conf))
  else
    VARIANT_CONFIG_PATH := device/google/contexthub/firmware/variant/$(VARIANT)
  endif
endif

include $(TOP_ABSPATH)/$(VARIANT_CONFIG_PATH)/$(VARIANT)_conf.mk

# $(VARIANT)_conf.mk defines VARIANT_PATH, PLATFORM, CHIP, CPU, VARIANT
# VARIANT_PATH from $(VARIANT)_conf.mk is ANDROID_TOP relative

# change VARIANT_PATH to become CWD-relative
VARIANT_PATH := $(NANOHUB_DIR)/$(TOP_RELPATH)/$(VARIANT_PATH)

# all output goes here
ifndef OUT
OUT:=out/nanohub/$(VARIANT)/app/$(BIN)
else
ifneq ($(filter $(TOP_ABSPATH)/out/target/product/%,$(OUT)),)
# this looks like Android OUT env var; update it
IMAGE_TARGET_OUT:=$(OUT)/vendor/firmware/$(BIN).napp
OUT:=$(OUT)/nanohub/$(VARIANT)/app/$(BIN)
endif
endif

################################################################################
#
# Nanoapp Libc/Libm Utils
#
################################################################################

include $(NANOHUB_DIR)/lib/lib.mk

# Tools ########################################################################

AS := $(PREFIX)gcc
CC := $(PREFIX)gcc
CXX := $(PREFIX)g++
OBJCOPY := $(PREFIX)objcopy
OBJDUMP := $(PREFIX)objdump

# Assembly Flags ###############################################################

AS_FLAGS +=

# C++ Flags ####################################################################

CXX_CFLAGS += -std=c++11
CXX_CFLAGS += -fno-exceptions
CXX_CFLAGS += -fno-rtti

# C Flags ######################################################################

C_CFLAGS +=

# Common Flags #################################################################

# Defines
CFLAGS += -DAPP_ID=$(APP_ID)
CFLAGS += -DAPP_VERSION=$(APP_VERSION)
CFLAGS += -D__NANOHUB__

# Optimization/debug
CFLAGS += -Os
CFLAGS += -g

# Include paths
CFLAGS += -I$(NANOHUB_DIR)/os/inc
CFLAGS += -I$(NANOHUB_DIR)/os/platform/$(PLATFORM)/inc
CFLAGS += -I$(NANOHUB_DIR)/os/cpu/$(CPU)/inc
CFLAGS += -I$(VARIANT_PATH)/inc
CFLAGS += -I$(NANOHUB_DIR)/../lib/include

# Warnings/error configuration.
CFLAGS += -Wall
CFLAGS += -Werror
CFLAGS += -Wmissing-declarations
CFLAGS += -Wlogical-op
CFLAGS += -Waddress
CFLAGS += -Wempty-body
CFLAGS += -Wpointer-arith
CFLAGS += -Wenum-compare
CFLAGS += -Wdouble-promotion
CFLAGS += -Wshadow
CFLAGS += -Wno-attributes

# Produce position independent code.
CFLAGS += -fpic
CFLAGS += -mno-pic-data-is-text-relative
CFLAGS += -msingle-pic-base
CFLAGS += -mpic-register=r9

# Code generation options for Cortex-M4F
CFLAGS += -mthumb
CFLAGS += -mcpu=cortex-m4
CFLAGS += -march=armv7e-m
CFLAGS += -mfloat-abi=softfp
CFLAGS += -mfpu=fpv4-sp-d16
CFLAGS += -mno-thumb-interwork
CFLAGS += -ffast-math
CFLAGS += -fsingle-precision-constant

# Platform defines
CFLAGS += -DARM
CFLAGS += -DUSE_NANOHUB_FLOAT_RUNTIME
CFLAGS += -DARM_MATH_CM4
CFLAGS += -D__FPU_PRESENT

# Miscellaneous
CFLAGS += -fno-strict-aliasing
CFLAGS += -fvisibility=hidden
CFLAGS += -fno-unwind-tables
CFLAGS += -fstack-reuse=all
CFLAGS += -ffunction-sections
CFLAGS += -fdata-sections

# Linker Configuration #########################################################

LD := $(PREFIX)g++

LDFLAGS := -T $(NANOHUB_DIR)/os/platform/$(PLATFORM)/lkr/app.lkr
LDFLAGS += -nostartfiles
LDFLAGS += -nostdlib
LDFLAGS += -Wl,--gc-sections
LDFLAGS += -Wl,-Map,$(OUT)/$(BIN).map
LDFLAGS += -Wl,--cref
ifeq ($(BIN_MODE),static)
LDFLAGS += -Bstatic
LDFLAGS += -Wl,--emit-relocs
else
LDFLAGS += -Bdynamic
LDFLAGS += -Wl,--no-undefined
LDFLAGS += -Wl,--no-allow-shlib-undefined
endif

# Build Rules ##################################################################

AS_SRCS := $(filter %.S, $(SRCS))
C_SRCS := $(filter %.c, $(SRCS))
CXX_SRCS := $(filter %.cc, $(SRCS))
CPP_SRCS := $(filter %.cpp, $(SRCS))

OBJS := $(patsubst %.S, $(OUT)/%.o, $(AS_SRCS))
OBJS += $(patsubst %.c, $(OUT)/%.o, $(C_SRCS))
OBJS += $(patsubst %.cc, $(OUT)/%.o, $(CXX_SRCS))
OBJS += $(patsubst %.cpp, $(OUT)/%.o, $(CPP_SRCS))

UNSIGNED_BIN := $(BIN).unsigned.napp

NANOHUB_KEY_PATH := $(NANOHUB_DIR)/os/platform/$(PLATFORM)/misc

.PHONY: all clean sync
all: $(OUT)/$(BIN).S $(OUT)/$(BIN).napp $(IMAGE_TARGET_OUT)

$(OUT)/$(BIN).napp : $(OUT)/$(UNSIGNED_BIN) $(NANOAPP_SIGN)
	@mkdir -p $(dir $@)
	$(NANOAPP_SIGN) -e $(NANOHUB_KEY_PATH)/debug.privkey \
		-m $(NANOHUB_KEY_PATH)/debug.pubkey -s $< $@
ifdef IMAGE_TARGET_OUT
$(IMAGE_TARGET_OUT): $(OUT)/$(BIN).napp
	@mkdir -p $(dir $@)
	cp $< $(IMAGE_TARGET_OUT)
endif

ifeq ($(APP_VERSION),)
APP_VERSION := 0
endif

ifeq ($(BIN_MODE),static)
$(OUT)/$(UNSIGNED_BIN) : $(OUT)/$(BIN).elf $(NANOAPP_POSTPROCESS)
	@mkdir -p $(dir $@)
	$(NANOAPP_POSTPROCESS) -s -a $(APP_ID) -e $(APP_VERSION) -v $(BIN_POSTPROCESS_ARGS) $< $@
else
$(OUT)/$(UNSIGNED_BIN) : $(OUT)/$(BIN).bin $(NANOAPP_POSTPROCESS)
	@mkdir -p $(dir $@)
	$(NANOAPP_POSTPROCESS) -a $(APP_ID) -e $(APP_VERSION) -v $(BIN_POSTPROCESS_ARGS) $< $@

$(OUT)/$(BIN).bin : $(OUT)/$(BIN).elf
	@mkdir -p $(dir $@)
	$(OBJCOPY) -j.relocs -j.flash -j.data -j.dynsym -O binary $< $@
endif

$(OUT)/$(BIN).S : $(OUT)/$(BIN).elf
	@mkdir -p $(dir $@)
	$(OBJDUMP) $< -DS > $@

$(OUT)/$(BIN).elf : $(OBJS)
	@mkdir -p $(dir $@)
	$(LD) $(CFLAGS) $(CXX_FLAGS) $(LDFLAGS) $(OBJS) $(STATIC_LIBS) -o $@

$(OUT)/%.o : %.S
	@mkdir -p $(dir $@)
	$(AS) $(AS_FLAGS) $(CFLAGS) -DSRC_FILENAME=\"$(notdir $<)\" -c $< -o $@

$(OUT)/%.o : %.c
	@mkdir -p $(dir $@)
	$(CC) $(C_CFLAGS) $(CFLAGS) -DSRC_FILENAME=\"$(notdir $<)\" -c $< -o $@

$(OUT)/%.o : %.cc
	@mkdir -p $(dir $@)
	$(CXX) $(CXX_CFLAGS) $(CFLAGS) -DSRC_FILENAME=\"$(notdir $<)\" -c $< -o $@

$(OUT)/%.o : %.cpp
	@mkdir -p $(dir $@)
	$(CXX) $(CXX_CFLAGS) $(CFLAGS) -DSRC_FILENAME=\"$(notdir $<)\" -c $< -o $@

# Automatic dependency resolution ##############################################

DEPS_AS = $(OUT)/deps_as
DEPS_C = $(OUT)/deps_c
DEPS_CXX = $(OUT)/deps_cxx

$(DEPS_AS) : $(AS_SRCS)
	@mkdir -p $(dir $@)
	$(AS) $(AS_CFLAGS) $(CFLAGS) -MM $^ > $@

$(DEPS_C) : $(C_SRCS)
	@mkdir -p $(dir $@)
	$(CC) $(C_CFLAGS) $(CFLAGS) -MM $^ > $@

$(DEPS_CXX) : $(CXX_SRCS) $(CPP_SRCS)
	@mkdir -p $(dir $@)
	$(CXX) $(CXX_CFLAGS) $(CFLAGS) -MM $^ > $@

NOAUTODEPTARGETS = clean

ifeq ($(words $(findstring $(MAKECMDGOALS), $(NOAUTODEPTARGETS))), 0)

ifneq ($(AS_SRCS), )
-include $(DEPS_AS)
endif

ifneq ($(C_SRCS), )
-include $(DEPS_C)
endif

ifneq ($(CXX_SRCS)$(CPP_SRCS),)
-include $(DEPS_CXX)
endif

endif

$(NANOAPP_POSTPROCESS): $(wildcard $(dir $(NANOAPP_POSTPROCESS))/*.c* $(dir $(NANOAPP_POSTPROCESS))/*.h)
	echo DEPS [$@]: $^
	make -C $(dir $@)

$(NANOAPP_SIGN): $(wildcard $(dir $(NANOAPP_SIGN))/*.c* $(dir $(NANOAPP_SIGN))/*.h)
	echo DEPS [$@]: $^
	make -C $(dir $@)

# Clean targets ################################################################

clean :
	rm -rf $(OUT)

sync:   $(OUT)/$(BIN).napp
	adb push $< /vendor/firmware/$(BIN).napp
