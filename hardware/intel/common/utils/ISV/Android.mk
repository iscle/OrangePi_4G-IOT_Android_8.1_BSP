ifeq ($(TARGET_HAS_ISV),true)

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    omx/isv_omxcore.cpp \
    omx/isv_omxcomponent.cpp \
    base/isv_bufmanager.cpp \
    base/isv_processor.cpp \
    base/isv_worker.cpp \
    profile/isv_profile.cpp

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libisv_omx_core
LOCAL_32_BIT_ONLY := true

LOCAL_SHARED_LIBRARIES := \
    libutils \
    liblog \
    libcutils \
    libdl \
    libhardware \
    libexpat \
    libva \
    libva-android \
    libmrm_omx_adaptor \
    libmedia \
    libsync \

LOCAL_HEADER_LIBRARIES := \
    libsystem_headers \
    libnativebase_headers

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include \
    system/core/libsync/include \
    $(call include-path-for, frameworks-openmax) \
    $(TARGET_OUT_HEADERS)/libmedia_utils_vpp \
    $(TARGET_OUT_HEADERS)/display \
    $(TARGET_OUT_HEADERS)/khronos/openmax \
    $(TARGET_OUT_HEADERS)/libva \
    $(TARGET_OUT_HEADERS)/pvr/hal \
    $(TARGET_OUT_HEADERS)/media_resource_manager/ \
    $(call include-path-for, frameworks-native)/media/openmax

ifeq ($(USE_MEDIASDK),true)
    LOCAL_CFLAGS += -DUSE_MEDIASDK
endif

ifeq ($(TARGET_VPP_USE_GEN),true)
    LOCAL_CFLAGS += -DTARGET_VPP_USE_GEN
endif

LOCAL_CFLAGS += -Werror

# TODO: Fix this:
LOCAL_CFLAGS += -Wno-error=unused-variable \
                -Wno-error=unused-but-set-variable

include $(BUILD_SHARED_LIBRARY)

endif
