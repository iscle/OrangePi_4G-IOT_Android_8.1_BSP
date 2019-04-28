LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := MediaLogService.cpp IMediaLogService.cpp

LOCAL_SHARED_LIBRARIES := libbinder libutils liblog libnbaio libaudioutils

LOCAL_MULTILIB := $(AUDIOSERVER_MULTILIB)

LOCAL_MODULE:= libmedialogservice

LOCAL_C_INCLUDES := $(call include-path-for, audio-utils)

LOCAL_CFLAGS := -Werror -Wall

include $(BUILD_SHARED_LIBRARY)
