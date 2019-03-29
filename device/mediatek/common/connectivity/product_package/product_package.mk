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


# Copyright (C) 2008 The Android Open Source Project
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

# WMT Configuration
BUILD_PATCH  := false
BUILD_WMT_CFG_L2   := false
BUILD_WMT_CFG_L1   := false
BUILD_MT6620 := false
BUILD_MT6628 := false
BUILD_MT6630 := false
BUILD_MT6632 := false
BUILD_MT7668 := false
BUILD_ROM_V1 :=false
BUILD_ROM_V2 :=false
BUILD_ROM_V2_LM :=false
BUILD_ROM_V3 :=false
BUILD_ROM_V4 :=false
BUILD_ROM_V4_BE :=false

ifeq ($(strip $(MTK_COMBO_SUPPORT)), yes)

LOCAL_PATH := $(call my-dir)

cfg_folder := vendor/mediatek/proprietary/hardware/connectivity/combo_tool/cfg_folder
init_folder := device/mediatek/common/connectivity/init

ENABLE_SP = false
ifeq ($(ENABLE_SP), false)
    patch_folder := vendor/mediatek/proprietary/hardware/connectivity/firmware/rom_patch
else
SP_PATH := vendor/mediatek/proprietary/hardware/connectivity/firmware/rom_patch_sp*
    $(warning [combo_tool] enable sp $(wildcard $(SP_PATH)))
    patch_folder := $(wildcard $(SP_PATH))
endif

BUILD_PATCH := true

ifneq ($(filter MT6620E3,$(MTK_COMBO_CHIP)),)
    BUILD_MT6620 := true
    BUILD_WMT_CFG_L1 := true
endif

ifneq ($(filter MT6620,$(MTK_COMBO_CHIP)),)
    BUILD_MT6620 := true
    BUILD_WMT_CFG_L1 := true
endif

ifneq ($(filter MT6628,$(MTK_COMBO_CHIP)),)
    BUILD_MT6628 := true
    BUILD_WMT_CFG_L1 := true
endif

ifneq ($(filter MT6630,$(MTK_COMBO_CHIP)),)
    BUILD_MT6630 := true
    BUILD_WMT_CFG_L1 := true
endif

ifneq ($(filter MT6632,$(MTK_COMBO_CHIP)),)
    BUILD_MT6632 := true
    BUILD_WMT_CFG_L1 := true
endif

ifneq ($(filter MT7668,$(MTK_COMBO_CHIP)),)
    BUILD_MT7668 := true
endif

ifneq ($(filter CONSYS_6572,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V1 := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6582,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V1 := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6592,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V1 := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6571,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V2 := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_8127,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V2 := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6752,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V2_LM := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6755,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V2_LM := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6757,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V2_LM := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6758,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V4 := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6763,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V2_LM := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6797,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V3 := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6759,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V4 := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6775,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V4_BE := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6771,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V4_BE := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6735,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V2_LM := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6739,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V2_LM := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6570,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V2_LM := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_6580,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V2_LM := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_8163,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V2_LM := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_8167,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V2_LM := true
    BUILD_WMT_CFG_L2 := true
endif

ifneq ($(filter CONSYS_7623,$(MTK_COMBO_CHIP)),)
    BUILD_ROM_V2_LM := true
    BUILD_WMT_CFG_L2 := true
endif

##### INSTALL WMT.CFG FOR COMBO CONFIG #####
ifeq ($(filter MT7668, $(MTK_COMBO_CHIP)),)
PRODUCT_COPY_FILES += $(init_folder)/init.connectivity.rc:$(MTK_TARGET_VENDOR_RC)/init.connectivity.rc
PRODUCT_COPY_FILES += $(init_folder)/factory_init.connectivity.rc:$(MTK_TARGET_VENDOR_RC)/factory_init.connectivity.rc
PRODUCT_COPY_FILES += $(init_folder)/meta_init.connectivity.rc:$(MTK_TARGET_VENDOR_RC)/meta_init.connectivity.rc
endif

ifeq ($(BUILD_WMT_CFG_L1), true)
PRODUCT_COPY_FILES += $(cfg_folder)/WMT.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/WMT.cfg:mtk
endif

ifeq ($(BUILD_WMT_CFG_L2), true)
    ifneq ($(wildcard $(MTK_PROJECT_FOLDER)/WMT_SOC.cfg),)
        PRODUCT_COPY_FILES += $(MTK_PROJECT_FOLDER)/WMT_SOC.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/WMT_SOC.cfg:mtk
    else
        ifneq ($(wildcard device/mediatek/$(MTK_PLATFORM_DIR)/WMT_SOC.cfg),)
            PRODUCT_COPY_FILES += device/mediatek/$(MTK_PLATFORM_DIR)/WMT_SOC.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/WMT_SOC.cfg:mtk
        else
            PRODUCT_COPY_FILES += $(cfg_folder)/WMT_SOC.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/WMT_SOC.cfg:mtk
        endif
    endif
endif

ifeq ($(BUILD_MT6620), true)
ifneq ($(filter mt6620_ant_m1,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6620_ant_m1.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6620_ant_m1.cfg:mtk
endif

ifneq ($(filter mt6620_ant_m2,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6620_ant_m2.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6620_ant_m2.cfg:mtk
endif

ifneq ($(filter mt6620_ant_m3,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6620_ant_m3.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6620_ant_m3.cfg:mtk
endif

ifneq ($(filter mt6620_ant_m4,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6620_ant_m4.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6620_ant_m4.cfg:mtk
endif

ifneq ($(filter mt6620_ant_m5,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6620_ant_m5.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6620_ant_m5.cfg:mtk
endif

ifneq ($(filter mt6620_ant_m6,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6620_ant_m6.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6620_ant_m6.cfg:mtk
endif

ifneq ($(filter mt6620_ant_m7,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6620_ant_m7.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6620_ant_m7.cfg:mtk
endif
endif


ifeq ($(BUILD_MT6628), true)
ifneq ($(filter mt6628_ant_m1,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6628_ant_m1.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6628_ant_m1.cfg:mtk
endif

ifneq ($(filter mt6628_ant_m2,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6628_ant_m2.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6628_ant_m2.cfg:mtk
endif

ifneq ($(filter mt6628_ant_m3,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6628_ant_m3.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6628_ant_m3.cfg:mtk
endif

ifneq ($(filter mt6628_ant_m4,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6628_ant_m4.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6628_ant_m4.cfg:mtk
endif
endif

ifeq ($(BUILD_MT6630), true)
ifneq ($(filter mt6630_ant_m1,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6630_ant_m1.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6630_ant_m1.cfg:mtk
endif

ifneq ($(filter mt6630_ant_m2,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6630_ant_m2.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6630_ant_m2.cfg:mtk
endif

ifneq ($(filter mt6630_ant_m3,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6630_ant_m3.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6630_ant_m3.cfg:mtk
endif

ifneq ($(filter mt6630_ant_m4,$(CUSTOM_HAL_ANT)),)
    PRODUCT_COPY_FILES += $(cfg_folder)/mt6630_ant_m4.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6630_ant_m4.cfg:mtk
endif
    PRODUCT_COPY_FILES += $(patch_folder)/mt6630_patch_e3_0_hdr.bin:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6630_patch_e3_0_hdr.bin:mtk
    PRODUCT_COPY_FILES += $(patch_folder)/mt6630_patch_e3_1_hdr.bin:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6630_patch_e3_1_hdr.bin:mtk
endif

ifeq ($(BUILD_MT6632), true)
ifneq ($(filter mt6632_ant_m1,$(CUSTOM_HAL_ANT)),)
    ifeq ($(MTK_WLAN_PATH_SET), siso)
        PRODUCT_COPY_FILES += $(cfg_folder)/mt6632_ant_m1.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6632_ant_m1.cfg:mtk
    else ifeq ($(MTK_WLAN_PATH_SET), mimo)
        PRODUCT_COPY_FILES += $(cfg_folder)/mt6632_ant_m2.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6632_ant_m1.cfg:mtk
    else ifeq ($(MTK_WLAN_PATH_SET), 2g4siso_5gmimo)
        PRODUCT_COPY_FILES += $(cfg_folder)/mt6632_ant_m3.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6632_ant_m1.cfg:mtk
    else ifeq ($(MTK_WLAN_PATH_SET), 2g4mimo_5gsiso)
        PRODUCT_COPY_FILES += $(cfg_folder)/mt6632_ant_m4.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6632_ant_m1.cfg:mtk
    else
        PRODUCT_COPY_FILES += $(cfg_folder)/mt6632_ant_m1.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6632_ant_m1.cfg:mtk
    endif
endif
    PRODUCT_COPY_FILES += $(patch_folder)/mt6632_patch_e3_hdr.bin:$(TARGET_COPY_OUT_VENDOR)/firmware/mt6632_patch_e3_hdr.bin:mtk
endif

ifeq ($(BUILD_MT7668), true)
    PRODUCT_COPY_FILES += $(patch_folder)/mt7668_patch_e2_hdr.bin:$(TARGET_COPY_OUT_VENDOR)/firmware/mt7668_patch_e2_hdr.bin:mtk
endif

ifeq ($(BUILD_ROM_V2_LM), true)
    PRODUCT_COPY_FILES += $(patch_folder)/ROMv2_lm_patch_1_0_hdr.bin:$(TARGET_COPY_OUT_VENDOR)/firmware/ROMv2_lm_patch_1_0_hdr.bin:mtk
    PRODUCT_COPY_FILES += $(patch_folder)/ROMv2_lm_patch_1_1_hdr.bin:$(TARGET_COPY_OUT_VENDOR)/firmware/ROMv2_lm_patch_1_1_hdr.bin:mtk
endif

ifeq ($(BUILD_ROM_V3), true)
    PRODUCT_COPY_FILES += $(patch_folder)/ROMv3_patch_1_0_hdr.bin:$(TARGET_COPY_OUT_VENDOR)/firmware/ROMv3_patch_1_0_hdr.bin:mtk
    PRODUCT_COPY_FILES += $(patch_folder)/ROMv3_patch_1_1_hdr.bin:$(TARGET_COPY_OUT_VENDOR)/firmware/ROMv3_patch_1_1_hdr.bin:mtk
endif

ifeq ($(BUILD_ROM_V4), true)
    PRODUCT_COPY_FILES += $(patch_folder)/ROMv4_patch_1_0_hdr.bin:$(TARGET_COPY_OUT_VENDOR)/firmware/ROMv4_patch_1_0_hdr.bin:mtk
    PRODUCT_COPY_FILES += $(patch_folder)/ROMv4_patch_1_1_hdr.bin:$(TARGET_COPY_OUT_VENDOR)/firmware/ROMv4_patch_1_1_hdr.bin:mtk
endif

ifeq ($(BUILD_ROM_V4_BE), true)
    PRODUCT_COPY_FILES += $(patch_folder)/ROMv4_be_patch_1_0_hdr.bin:$(TARGET_COPY_OUT_VENDOR)/firmware/ROMv4_be_patch_1_0_hdr.bin:mtk
    PRODUCT_COPY_FILES += $(patch_folder)/ROMv4_be_patch_1_1_hdr.bin:$(TARGET_COPY_OUT_VENDOR)/firmware/ROMv4_be_patch_1_1_hdr.bin:mtk
endif

# PRODUCT_PACKAGES part

ifeq ($(filter MT7668, $(MTK_COMBO_CHIP)),)
PRODUCT_PACKAGES += wmt_launcher \
    wmt_concurrency \
    wmt_loopback \
    wmt_loader \
    stp_dump3

PRODUCT_PACKAGES += wmt_drv.ko

# Codedump Mode
# 0=no coredump
# 1=coredump available in AEE's EE DB
# 2=coredump stored by stp_dump daemon

# user load: 0
# MTK eng/userdebug load: 1
# Customer eng/userdebug load: 2

COREDUMP_MODE := 0

ifneq ($(TARGET_BUILD_VARIANT),user)
    ifneq ($(wildcard vendor/mediatek/proprietary/external/aee_config_internal/init.aee.mtk.rc),)
        COREDUMP_MODE := 1
    else
        COREDUMP_MODE := 2
    endif
endif

PRODUCT_PROPERTY_OVERRIDES += persist.mtk.wcn.coredump.mode=$(COREDUMP_MODE)

PRODUCT_PROPERTY_OVERRIDES += persist.mtk.wcn.combo.chipid=-1
PRODUCT_PROPERTY_OVERRIDES += persist.mtk.wcn.patch.version=-1
PRODUCT_PROPERTY_OVERRIDES += persist.mtk.wcn.dynamic.dump=0
PRODUCT_PROPERTY_OVERRIDES += service.wcn.driver.ready=no
PRODUCT_PROPERTY_OVERRIDES += persist.mtk.connsys.poweron.ctl=0
endif

endif

$(call inherit-product-if-exists, device/mediatek/common/connectivity/product_package/gps_product_package.mk)
$(call inherit-product-if-exists, device/mediatek/common/connectivity/product_package/fm_product_package.mk)
$(call inherit-product-if-exists, device/mediatek/common/connectivity/product_package/wlan_product_package.mk)
$(call inherit-product-if-exists, device/mediatek/common/connectivity/product_package/bluetooth_product_package.mk)
