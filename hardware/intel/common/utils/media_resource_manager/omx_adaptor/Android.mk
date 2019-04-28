LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    OMX_adaptor.cpp

LOCAL_SHARED_LIBRARIES := \
    liblog \
    libutils \
    libcutils \
    libexpat \
    libdl \
    libmrm_arbitrator \

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/include

LOCAL_C_INCLUDES := \
    $(TARGET_OUT_HEADERS)/khronos/openmax \
    $(call include-path-for, frameworks-native)/media/openmax \
    $(LOCAL_PATH)/../arbitrator \


LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libmrm_omx_adaptor

LOCAL_CFLAGS += -Werror

include $(BUILD_SHARED_LIBRARY)
