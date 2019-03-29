LOCAL_PATH := $(call my-dir)
ifneq ($(MTK_PLATFORM),)
MTK_PLATFORM_DIR := $(shell echo $(MTK_PLATFORM) | tr '[A-Z]' '[a-z]')
ifneq ("$(wildcard $(LOCAL_PATH)/$(MTK_PLATFORM_DIR)/Android.mk)","")
include $(LOCAL_PATH)/$(MTK_PLATFORM_DIR)/Android.mk
endif
endif