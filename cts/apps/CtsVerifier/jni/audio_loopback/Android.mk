LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE      := libaudioloopback_jni

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES   := \
	sles.cpp \
	jni_sles.c \
	audio_utils/atomic.c \
	audio_utils/fifo.c \
	audio_utils/roundup.c

LOCAL_SHARED_LIBRARIES := \
	libOpenSLES \
	liblog

LOCAL_LDFLAGS := -Wl,--hash-style=sysv

LOCAL_CFLAGS := -DSTDC_HEADERS \
	-Werror -Wall

LOCAL_SDK_VERSION := 23

include $(BUILD_SHARED_LIBRARY)
