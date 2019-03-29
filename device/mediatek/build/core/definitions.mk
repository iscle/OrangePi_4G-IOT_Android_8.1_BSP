#  Copyright Statement:
#
#  This software/firmware and related documentation ("MediaTek Software") are
#  protected under relevant copyright laws. The information contained herein
#  is confidential and proprietary to MediaTek Inc. and/or its licensors.
#  Without the prior written permission of MediaTek inc. and/or its licensors,
#  any reproduction, modification, use or disclosure of MediaTek Software,
#  and information contained herein, in whole or in part, shall be strictly prohibited.
#
#  MediaTek Inc. (C) 2016. All rights reserved.
#
#  BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
#  THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
#  RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
#  AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
#  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
#  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
#  NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
#  SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
#  SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
#  THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
#  THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
#  CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
#  SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
#  STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
#  CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
#  AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
#  OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
#  MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
#
#  The following software/firmware and/or related documentation ("MediaTek Software")
#  have been modified by MediaTek Inc. All revisions are subject to any receiver's
#  applicable license agreements with MediaTek Inc.


##
## Mediatek build system definitions.
##

LOCAL_PATH := $(call my-dir)
MTK_SHARED_LIBRARY := $(LOCAL_PATH)/shared_library.mk
MTK_STATIC_LIBRARY := $(LOCAL_PATH)/static_library.mk
MTK_EXECUTABLE := $(LOCAL_PATH)/executable.mk
MTK_PREBUILT := $(LOCAL_PATH)/prebuilt.mk


ifndef JACK_HOME
ifndef JACK_CLIENT_SETTING
ifneq ($(jack_server_disabled),true)
ifneq ($(MTKCONF),)
# MTK_INTERNAL
USE_MTK_JACK_OVERRIDE ?= yes
endif
endif
endif
endif

# Use MTK jack env override
ifeq ($(USE_MTK_JACK_OVERRIDE),yes)
MTK_JACK_ENV := $(OUT_DIR)/mtk-jack-env.log
#export MTK_JACK_BASE := /tmp
define call-jack
 JACK_VERSION=$(PRIVATE_JACK_VERSION) eval `cat $(MTK_JACK_ENV)` $(JACK) $(DEFAULT_JACK_EXTRA_ARGS)
endef
endif

#Add sign commands for out-of-tree kernel modules
#$(1): $(KERNEL_OUT)/scripts/sign-file
#$(2): cert file
#$(3): cert file
define sign-kernel-module
 if [ -f $(1) ]; \
      then python vendor/mediatek/proprietary/scripts/kernel_tool/rm_ko_sig.py $@ \
      && $(1) sha256 $(2) $(3) $@; \
 fi
endef
