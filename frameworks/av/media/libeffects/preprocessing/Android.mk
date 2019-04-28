LOCAL_PATH:= $(call my-dir)

# audio preprocessing wrapper
include $(CLEAR_VARS)

LOCAL_VENDOR_MODULE := true
LOCAL_MODULE:= libaudiopreprocessing
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_RELATIVE_PATH := soundfx

LOCAL_VENDOR_MODULE := true
LOCAL_SRC_FILES:= \
    PreProcessing.cpp

LOCAL_C_INCLUDES += \
    external/webrtc \
    external/webrtc/webrtc/modules/include \
    external/webrtc/webrtc/modules/audio_processing/include \
    $(call include-path-for, audio-effects)

LOCAL_SHARED_LIBRARIES := \
    libwebrtc_audio_preprocessing \
    libspeexresampler \
    libutils \
    liblog

LOCAL_SHARED_LIBRARIES += libdl

LOCAL_CFLAGS += \
    -DWEBRTC_POSIX

LOCAL_CFLAGS += -fvisibility=hidden
LOCAL_CFLAGS += -Wall -Werror

LOCAL_HEADER_LIBRARIES += libhardware_headers
include $(BUILD_SHARED_LIBRARY)
