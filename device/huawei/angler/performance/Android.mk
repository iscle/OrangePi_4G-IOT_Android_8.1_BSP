LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_COPY_HEADERS_TO := power
LOCAL_COPY_HEADERS := performance.h
include $(BUILD_COPY_HEADERS)
