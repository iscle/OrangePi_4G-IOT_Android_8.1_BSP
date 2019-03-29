LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_COPY_HEADERS_TO := time-services
LOCAL_COPY_HEADERS := time_genoff.h
include $(BUILD_COPY_HEADERS)
