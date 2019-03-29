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

M3DEBUG ?= m3debug

ifneq ($(CPU),cortexm4)
        $(error "stm32 cplatform only supports Cortex-M4 CPUs")
endif

BL_FILE = $(OUT)/bl.unchecked.bin
OS_FILE = $(OUT)/os.checked.bin

DELIVERABLES += showsizes
FLAGS += -I. -fno-unwind-tables -fstack-reuse=all -ffunction-sections -fdata-sections
FLAGS += -Wl,--gc-sections -nostartfiles
FLAGS += -nostdlib

#platform bootloader
SRCS_bl += os/platform/$(PLATFORM)/bl.c

#platform runtime
SRCS_os +=  os/platform/$(PLATFORM)/crt_$(PLATFORM).c

#platform drivers
SRCS_os += os/platform/$(PLATFORM)/platform.c \
	os/platform/$(PLATFORM)/usart.c \
	os/platform/$(PLATFORM)/gpio.c \
	os/platform/$(PLATFORM)/pwr.c \
	os/platform/$(PLATFORM)/wdt.c \
	os/platform/$(PLATFORM)/i2c.c \
	os/platform/$(PLATFORM)/exti.c \
	os/platform/$(PLATFORM)/syscfg.c \
	os/platform/$(PLATFORM)/spi.c \
	os/platform/$(PLATFORM)/rtc.c \
	os/platform/$(PLATFORM)/mpu.c \
	os/platform/$(PLATFORM)/dma.c \
	os/platform/$(PLATFORM)/crc.c \
	os/platform/$(PLATFORM)/hostIntf.c \
	os/platform/$(PLATFORM)/apInt.c \
	os/platform/$(PLATFORM)/eeData.c


#platform drivers for bootloader
SRCS_bl += os/platform/$(PLATFORM)/pwr.c os/platform/$(PLATFORM)/gpio.c

#extra deps
DEPS += $(wildcard os/platform/$(PLATFORM)/inc/plat/*.h)
DEPS += $(wildcard os/platform/$(PLATFORM)/inc/plat/cmsis/*.h)

#linker script
LKR_os = os/platform/$(PLATFORM)/lkr/$(CHIP).os.lkr
LKR_bl = os/platform/$(PLATFORM)/lkr/$(CHIP).bl.lkr
OSFLAGS_os += -Wl,-T $(LKR_os)
OSFLAGS_bl += -Wl,-T $(LKR_bl)
DEPS += $(LKR_os) $(LKR_bl)

#platform flags
PLATFORM_HAS_HARDWARE_CRC = true
FLAGS += -DPLATFORM_HW_VER=0

#platform-specific rules
OBJCOPY_PARAMS = -I elf32-littlearm -O binary

$(OUT)/bl.%.bin : $(OUT)/bl.%.elf
	$(OBJCOPY) -j .bl -j .data -j .eedata $(OBJCOPY_PARAMS) $< $@

$(OUT)/os.%.bin : $(OUT)/os.%.elf
	$(OBJCOPY) -j .data -j .text $(OBJCOPY_PARAMS) $< $@

showsizes: $(OUT)/os.unchecked.elf
	os/platform/$(PLATFORM)/misc/showsizes.sh $<

$(info Included STM32 platfrom)
