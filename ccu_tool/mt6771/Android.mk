ifneq ($(MTK_CAM_SW_VERSION),)
MTK_PLATFORM_SW_VER_DIR := $(LOCAL_PATH)/$(MTK_PLATFORM_DIR)/$(MTK_CAM_SW_VERSION)
ifneq ("$(wildcard $(MTK_PLATFORM_SW_VER_DIR)/Android.mk)","")
include $(MTK_PLATFORM_SW_VER_DIR)/Android.mk
endif
endif