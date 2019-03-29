#Android makefile for uim
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= uim.c

LOCAL_C_INCLUDES := $(LOCAL_PATH)/

LOCAL_MODULE_TAGS := eng

LOCAL_MODULE := uim
LOCAL_PROPRIETARY_MODULE := true

include $(BUILD_EXECUTABLE)

