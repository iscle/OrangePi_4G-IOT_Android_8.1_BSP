# Copyright Statement:
#
# This software/firmware and related documentation ("MediaTek Software") are
# protected under relevant copyright laws. The information contained herein
# is confidential and proprietary to MediaTek Inc. and/or its licensors.
# Without the prior written permission of MediaTek inc. and/or its licensors,
# any reproduction, modification, use or disclosure of MediaTek Software,
# and information contained herein, in whole or in part, shall be strictly prohibited.
#
# MediaTek Inc. (C) 2010. All rights reserved.
#
# BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
# THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
# RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
# AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
# NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
# SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
# SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
# THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
# THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
# CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
# SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
# STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
# CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
# AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
# OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
# MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
#
# The following software/firmware and/or related documentation ("MediaTek Software")
# have been modified by MediaTek Inc. All revisions are subject to any receiver's
# applicable license agreements with MediaTek Inc.


# *************************************************************************
# Set shell align with Android build system
# *************************************************************************
SHELL        := /bin/bash
MTK_PROJECT_FOLDER := $(shell find device/* -maxdepth 1 -name $(PROJECT))
PRJ_MF := $(MTK_PROJECT_FOLDER)/ProjectConfig.mk
include $(PRJ_MF)
hide := @
PRIVATE_CUSTOM_KERNEL_DCT:= $(if $(CUSTOM_KERNEL_DCT),$(CUSTOM_KERNEL_DCT),dct)

DRVGEN_PATH  := device/mediatek/build/build/tools/drvgen
DRVGEN_OUT_PATH := device/mediatek/$(PROJECT)/custom/kernel/dct
DRVGEN_PRELOADER_PATH := bootable/bootloader/preloader/custom/$(PROJECT)/inc
DRVGEN_LK_PATH := bootable/bootloader/lk/target/$(PROJECT)/inc
DRVGEN_LK_PATH_2 := bootable/bootloader/lk/target/$(PROJECT)/include/target
DRVGEN_KERNEL_PATH := kernel-3.4/arch/arm/mach-$(PLATFORM)/$(PROJECT)/dct/$(PRIVATE_CUSTOM_KERNEL_DCT)
DRVGEN_TOOL := vendor/mediatek/proprietary/dct/DrvGen
DWS_FILE := $(DRVGEN_KERNEL_PATH)/codegen.dws
ifeq ($(wildcard $(DWS_FILE)),)
	DWS_FILE := kernel-3.4/arch/arm/mach-$(PLATFORM)/$(BASEPROJECT)/dct/$(PRIVATE_CUSTOM_KERNEL_DCT)/codegen.dws
endif

$(shell mkdir -p $(DRVGEN_OUT_PATH))
$(shell mkdir -p $(DRVGEN_PRELOADER_PATH))
$(shell mkdir -p $(DRVGEN_LK_PATH))
$(shell mkdir -p $(DRVGEN_KERNEL_PATH))

drvgen: $(DRVGEN_TOOL) $(DWS_FILE)
ifeq ($(MTK_DEPENDENCY_AUTO_CHECK), true)
	-@echo [Update] $@: $?
endif
	$(hide) $(DRVGEN_TOOL) $(DWS_FILE) $(DRVGEN_OUT_PATH) $(DRVGEN_OUT_PATH)
	$(hide) cp -f $(DRVGEN_OUT_PATH)/cust_kpd.h $(DRVGEN_PRELOADER_PATH)/cust_kpd.h
	$(hide) cp -f $(DRVGEN_OUT_PATH)/cust_kpd.h $(DRVGEN_LK_PATH)/cust_kpd.h
	$(hide) cp -f $(DRVGEN_OUT_PATH)/cust_eint.h $(DRVGEN_PRELOADER_PATH)/cust_eint.h
	$(hide) cp -f $(DRVGEN_OUT_PATH)/cust_eint.h $(DRVGEN_LK_PATH)/cust_eint.h
	$(hide) cp -f $(DRVGEN_OUT_PATH)/cust_gpio_boot.h $(DRVGEN_PRELOADER_PATH)/cust_gpio_boot.h
	$(hide) cp -f $(DRVGEN_OUT_PATH)/cust_gpio_boot.h $(DRVGEN_LK_PATH_2)/cust_gpio_boot.h
	$(hide) cp -f $(DRVGEN_OUT_PATH)/cust_gpio_usage.h $(DRVGEN_PRELOADER_PATH)/cust_gpio_usage.h
	$(hide) cp -f $(DRVGEN_OUT_PATH)/cust_gpio_usage.h $(DRVGEN_LK_PATH)/cust_gpio_usage.h
	$(hide) cp -f $(DRVGEN_OUT_PATH)/cust_power.h $(DRVGEN_LK_PATH_2)/cust_power.h
	$(hide) for i in `find $(DRVGEN_OUT_PATH) -type f`;do cp -f $$i $(DRVGEN_KERNEL_PATH); done
	$(hide) echo [OUTPUT]:
	$(hide) echo device/mediatek/$(PROJECT)/custom/kernel/dct/
	$(hide) echo $(DRVGEN_PRELOADER_PATH)/cust_kpd.h
	$(hide) echo $(DRVGEN_PRELOADER_PATH)/cust_eint.h
	$(hide) echo $(DRVGEN_PRELOADER_PATH)/cust_gpio_boot.h
	$(hide) echo $(DRVGEN_PRELOADER_PATH)/cust_gpio_usage.h
	$(hide) echo $(DRVGEN_LK_PATH)/cust_kpd.h
	$(hide) echo $(DRVGEN_LK_PATH)/cust_eint.h
	$(hide) echo $(DRVGEN_LK_PATH)/cust_gpio_usage.h
	$(hide) echo $(DRVGEN_LK_PATH_2)/cust_gpio_usage.h
	$(hide) echo $(DRVGEN_LK_PATH_2)/cust_power.h	
	$(hide) echo $(DRVGEN_KERNEL_PATH)/
