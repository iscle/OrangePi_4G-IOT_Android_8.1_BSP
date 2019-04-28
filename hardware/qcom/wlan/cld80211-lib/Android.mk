LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libcld80211
LOCAL_CLANG := true
LOCAL_MODULE_TAGS := optional
LOCAL_C_INCLUDES += $(LOCAL_PATH) \
	external/libnl/include
LOCAL_SHARED_LIBRARIES := libcutils libnl liblog
LOCAL_SRC_FILES := cld80211_lib.c
LOCAL_CFLAGS += -Wall -Werror -Wno-unused-parameter
LOCAL_COPY_HEADERS_TO := cld80211-lib
LOCAL_COPY_HEADERS := cld80211_lib.h
LOCAL_PROPRIETARY_MODULE := true
include $(BUILD_SHARED_LIBRARY)

