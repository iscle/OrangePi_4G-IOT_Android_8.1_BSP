#
# Copyright (C) 2014 MediaTek Inc.
# Modification based on code covered by the mentioned copyright
# and/or permission notice(s).
#

MEDIATEK_RECOVERY_PATH := vendor/mediatek/proprietary/bootable/recovery

LOCAL_SRC_FILES += \
         mt_install.cpp

ifeq ($(TARGET_USERIMAGES_USE_UBIFS),true)
LOCAL_CFLAGS += -DUBIFS_SUPPORT
LOCAL_STATIC_LIBRARIES += ubiutils
endif

ifeq ($(strip $(MNTL_SUPPORT)),yes)
LOCAL_CFLAGS += -DMNTL_SUPPORT
endif


LOCAL_C_INCLUDES += \
     $(MEDIATEK_RECOVERY_PATH) \
     $(MEDIATEK_RECOVERY_PATH)/utils/include \
     $(LOCAL_PATH)/include \
     system/core/libsparse


LOCAL_C_INCLUDES += \
         $(MEDIATEK_RECOVERY_PATH) \
         $(MEDIATEK_RECOVERY_PATH)/utils/include \

LOCAL_STATIC_LIBRARIES += libpartition libbase liblog libfs_mgr libcutils
