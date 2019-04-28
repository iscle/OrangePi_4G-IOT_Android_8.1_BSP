LOCAL_PATH:= $(call my-dir)

# Multichannel downmix effect library
include $(CLEAR_VARS)

LOCAL_VENDOR_MODULE := true
LOCAL_SRC_FILES:= \
	EffectDownmix.c

LOCAL_SHARED_LIBRARIES := \
	libcutils liblog

LOCAL_MODULE:= libdownmix

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE_RELATIVE_PATH := soundfx

LOCAL_C_INCLUDES := \
	$(call include-path-for, audio-effects) \
	$(call include-path-for, audio-utils)

#-DBUILD_FLOAT
LOCAL_CFLAGS += -fvisibility=hidden
LOCAL_CFLAGS += -Wall -Werror

LOCAL_HEADER_LIBRARIES += libhardware_headers
include $(BUILD_SHARED_LIBRARY)
