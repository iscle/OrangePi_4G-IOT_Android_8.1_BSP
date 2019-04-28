LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests
LOCAL_MODULE    := libnative-media-jni
LOCAL_SRC_FILES := native-media-jni.c
LOCAL_CFLAGS := -Werror -Wall
LOCAL_CFLAGS += -I$(call include-path-for, wilhelm)
LOCAL_CFLAGS += -UNDEBUG -Wno-unused-parameter

LOCAL_SHARED_LIBRARIES += libutils liblog libOpenMAXAL libandroid


include $(BUILD_SHARED_LIBRARY)
