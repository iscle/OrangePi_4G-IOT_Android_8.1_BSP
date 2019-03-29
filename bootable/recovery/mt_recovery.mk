#
# Copyright (C) 2014 MediaTek Inc.
# Modification based on code covered by the mentioned copyright
# and/or permission notice(s).
#

MEDIATEK_RECOVERY_PATH := vendor/mediatek/proprietary/bootable/recovery

LOCAL_SRC_FILES += \
    mt_roots.cpp \
    mt_recovery.cpp \
    mt_install.cpp \
    ../../$(MEDIATEK_RECOVERY_PATH)/utils/mt_check_partition.cpp


ifeq ($(MNTL_SUPPORT), yes)
LOCAL_CFLAGS += -DMNTL_SUPPORT
endif


ifeq ($(MTK_GMO_ROM_OPTIMIZE),true)
LOCAL_CFLAGS += -DMTK_GMO_ROM_OPTIMIZE
endif

ifeq ($(TARGET_USERIMAGES_USE_UBIFS),true)
LOCAL_CFLAGS += -DUBIFS_SUPPORT

LOCAL_STATIC_LIBRARIES += ubi_ota_update

endif

#add for fat merge
ifeq ($(MTK_MLC_NAND_SUPPORT),yes)
LOCAL_CFLAGS += -DBOARD_UBIFS_FAT_MERGE_VOLUME_SIZE=$(BOARD_UBIFS_FAT_MERGE_VOLUME_SIZE)
LOCAL_CFLAGS += -DBOARD_UBIFS_IPOH_VOLUME_SIZE=$(BOARD_UBIFS_IPOH_VOLUME_SIZE)
endif


LOCAL_C_INCLUDES += kernel \
        $(MEDIATEK_RECOVERY_PATH) \
        $(MEDIATEK_RECOVERY_PATH)/fota/include \
        $(MEDIATEK_RECOVERY_PATH)/utils/include \
        external/libselinux/include \
        external/selinux/libsepol/include \
        system/core/fs_mgr/include \
        system/core/fs_mgr \
        bionic/libc
