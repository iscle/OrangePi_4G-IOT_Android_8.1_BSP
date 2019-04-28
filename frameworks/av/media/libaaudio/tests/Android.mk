LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-utils) \
    frameworks/av/media/libaaudio/include \
    frameworks/av/media/libaaudio/src
LOCAL_SRC_FILES:= test_marshalling.cpp
LOCAL_SHARED_LIBRARIES := libaaudio libbinder libcutils libutils
LOCAL_MODULE := test_aaudio_marshalling
include $(BUILD_NATIVE_TEST)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-utils) \
    frameworks/av/media/libaaudio/include \
    frameworks/av/media/libaaudio/src
LOCAL_SRC_FILES:= test_block_adapter.cpp
LOCAL_SHARED_LIBRARIES := libaaudio
LOCAL_MODULE := test_block_adapter
include $(BUILD_NATIVE_TEST)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-utils) \
    frameworks/av/media/libaaudio/include \
    frameworks/av/media/libaaudio/src \
    frameworks/av/media/libaaudio/examples
LOCAL_SRC_FILES:= test_timestamps.cpp
LOCAL_SHARED_LIBRARIES := libaaudio
LOCAL_MODULE := test_timestamps
include $(BUILD_NATIVE_TEST)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-utils) \
    frameworks/av/media/libaaudio/include \
    frameworks/av/media/libaaudio/src
LOCAL_SRC_FILES:= test_linear_ramp.cpp
LOCAL_SHARED_LIBRARIES := libaaudio
LOCAL_MODULE := test_linear_ramp
include $(BUILD_NATIVE_TEST)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-utils) \
    frameworks/av/media/libaaudio/include \
    frameworks/av/media/libaaudio/src
LOCAL_SRC_FILES:= test_open_params.cpp
LOCAL_SHARED_LIBRARIES := libaaudio libbinder libcutils libutils
LOCAL_MODULE := test_open_params
include $(BUILD_NATIVE_TEST)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-utils) \
    frameworks/av/media/libaaudio/include \
    frameworks/av/media/libaaudio/src
LOCAL_SRC_FILES:= test_no_close.cpp
LOCAL_SHARED_LIBRARIES := libaaudio libbinder libcutils libutils
LOCAL_MODULE := test_no_close
include $(BUILD_NATIVE_TEST)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-utils) \
    frameworks/av/media/libaaudio/include \
    frameworks/av/media/libaaudio/src
LOCAL_SRC_FILES:= test_recovery.cpp
LOCAL_SHARED_LIBRARIES := libaaudio libbinder libcutils libutils
LOCAL_MODULE := test_aaudio_recovery
include $(BUILD_NATIVE_TEST)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-utils) \
    frameworks/av/media/libaaudio/include \
    frameworks/av/media/libaaudio/src
LOCAL_SRC_FILES:= test_n_streams.cpp
LOCAL_SHARED_LIBRARIES := libaaudio libbinder libcutils libutils
LOCAL_MODULE := test_n_streams
include $(BUILD_NATIVE_TEST)
