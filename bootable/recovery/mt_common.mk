#
# Copyright (C) 2014 MediaTek Inc.
# Modification based on code covered by the mentioned copyright
# and/or permission notice(s).
#
MEDIATEK_RECOVERY_PATH := vendor/mediatek/proprietary/bootable/recovery

##########################################
# Feature option
##########################################

ifeq ($(MTK_GPT_SCHEME_SUPPORT), yes)
    WITH_GPT_SCHEME := true
else
    WITH_GPT_SCHEME := false
endif

##########################################
# Static library - UBIFS_SUPPORT
##########################################

ifeq ($(TARGET_USERIMAGES_USE_UBIFS),true)
include $(CLEAR_VARS)
LOCAL_SRC_FILES := roots.cpp \
                   mt_roots.cpp

LOCAL_MODULE := ubiutils

LOCAL_C_INCLUDES += system/extras/ext4_utils \
                    $(MEDIATEK_RECOVERY_PATH) \
                    system/core/fs_mgr/include \
                    system/core/fs_mgr \
                    $(MEDIATEK_RECOVERY_PATH)/utils/include

LOCAL_STATIC_LIBRARIES += libz ubi_ota_update

LOCAL_CFLAGS += -DUBIFS_SUPPORT

#add for fat merge
ifeq ($(MTK_MLC_NAND_SUPPORT),yes)
LOCAL_CFLAGS += -DBOARD_UBIFS_FAT_MERGE_VOLUME_SIZE=$(BOARD_UBIFS_FAT_MERGE_VOLUME_SIZE)
LOCAL_CFLAGS += -DBOARD_UBIFS_IPOH_VOLUME_SIZE=$(BOARD_UBIFS_IPOH_VOLUME_SIZE)
endif

LOCAL_MODULE_TAGS := eng

include $(BUILD_STATIC_LIBRARY)
endif

include $(CLEAR_VARS)
LOCAL_C_INCLUDES += \
    $(MEDIATEK_RECOVERY_PATH)/utils/include \
    external/selinux/libselinux/include \
    system/core/fs_mgr \
    system/core/fs_mgr/include \
    system/core/fs_mgr/include_fstab \
    system/core/libziparchive/include \
    bionic/libc

LOCAL_SRC_FILES := \
    ../../$(MEDIATEK_RECOVERY_PATH)/utils/mt_gpt.cpp \
    ../../$(MEDIATEK_RECOVERY_PATH)/utils/mt_pmt.cpp \
    ../../$(MEDIATEK_RECOVERY_PATH)/utils/mt_partition.cpp

LOCAL_MODULE := libpartition
include $(BUILD_STATIC_LIBRARY)
