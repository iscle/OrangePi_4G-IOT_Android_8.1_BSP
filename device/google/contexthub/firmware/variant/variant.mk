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

PLATFORM?=stm32
CPU?=cortexm4
CHIP?=stm32f411

# SRC_PATH points to "firmware"; TOP_PATH is the abs path to top of android tree
TOP_RELPATH := ../../../..
TOP_ABSPATH := $(realpath $(SRC_PATH)/$(TOP_RELPATH))
VARIANT_ABSPATH := $(TOP_ABSPATH)/$(VARIANT_PATH)
VARIANT_PATH := $(TOP_RELPATH)/$(VARIANT_PATH)

ifndef OUT
OUT:=out/nanohub/$(VARIANT)
MAKE_OUT=$(VARIANT_PATH)/$(OUT)
else
ifneq ($(filter $(TOP_ABSPATH)/out/target/product/%,$(OUT)),)
# this looks like Android OUT env var; update it
OUT:=$(OUT)/nanohub/$(VARIANT)
IMAGE_TARGET_OUT:=vendor/firmware/nanohub.full.bin
endif
MAKE_OUT:=$(OUT)
endif

ifdef IMAGE_TARGET_OUT
DIR_TMP := $(dir $(IMAGE_TARGET_OUT))
IMAGE_TARGET_OUT_ELF := $(DIR_TMP)nanohub.os.$(TARGET_PRODUCT).elf
endif

ifdef IMAGE_OUT
DIR_TMP := $(dir $(IMAGE_OUT))
IMAGE_OUT_ELF := $(DIR_TMP)nanohub.os.$(TARGET_PRODUCT).elf
endif

.PHONY: all clean sync

all:
	make -C $(SRC_PATH) -f firmware.mk VARIANT=$(VARIANT) VARIANT_PATH=$(VARIANT_PATH) OUT=$(MAKE_OUT) PLATFORM=$(PLATFORM) CPU=$(CPU) CHIP=$(CHIP) $(EXTRA_ARGS)
ifdef IMAGE_OUT
	cd $(VARIANT_ABSPATH) && \
	cp $(OUT)/full.bin $(IMAGE_OUT) && \
	cp $(OUT)/os.unchecked.elf $(IMAGE_OUT_ELF) && \
	chmod -x $(IMAGE_OUT_ELF)
endif
ifdef IMAGE_TARGET_OUT
	cd $(VARIANT_ABSPATH) && \
	mkdir -p $(dir $(TOP_ABSPATH)/$(IMAGE_TARGET_OUT)) && \
	cp $(OUT)/full.bin $(TOP_ABSPATH)/$(IMAGE_TARGET_OUT) && \
	cp $(OUT)/os.unchecked.elf $(TOP_ABSPATH)/$(IMAGE_TARGET_OUT_ELF) && \
	chmod -x $(TOP_ABSPATH)/$(IMAGE_TARGET_OUT_ELF)
endif

clean:
	rm -rf $(OUT)
ifdef IMAGE_OUT
	rm $(VARIANT_ABSPATH)/$(IMAGE_OUT)
	rm $(VARIANT_ABSPATH)/$(IMAGE_OUT_ELF)
endif
ifdef IMAGE_TARGET_OUT
	rm $(TOP_ABSPATH)/$(IMAGE_TARGET_OUT)
	rm $(TOP_ABSPATH)/$(IMAGE_TARGET_OUT_ELF)
endif

sync:
	adb push $(OUT)/full.bin /vendor/firmware/nanohub.full.bin
