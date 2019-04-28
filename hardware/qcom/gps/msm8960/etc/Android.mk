
LOCAL_PATH := $(call my-dir)

ifeq ($(filter flo deb,$(TARGET_DEVICE)),)
include $(CLEAR_VARS)

LOCAL_MODULE := gps.conf
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH  := $(TARGET_OUT_ETC)/
LOCAL_SRC_FILES := gps.conf

include $(BUILD_PREBUILT)
endif
