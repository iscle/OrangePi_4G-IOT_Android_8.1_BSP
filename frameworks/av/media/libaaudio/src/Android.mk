LOCAL_PATH:= $(call my-dir)

# ======================= STATIC LIBRARY ==========================
# This is being built because it make AAudio testing very easy with a complete executable.
# TODO Remove this target later, when not needed.
include $(CLEAR_VARS)

LOCAL_MODULE := libaaudio
LOCAL_MODULE_TAGS := optional

LIBAAUDIO_DIR := $(TOP)/frameworks/av/media/libaaudio
LIBAAUDIO_SRC_DIR := $(LIBAAUDIO_DIR)/src

LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-utils) \
    frameworks/native/include \
    system/core/base/include \
    frameworks/native/media/libaaudio/include/include \
    frameworks/av/media/libaaudio/include \
    frameworks/native/include \
    frameworks/av/media/libaudioclient/include \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/binding \
    $(LOCAL_PATH)/client \
    $(LOCAL_PATH)/core \
    $(LOCAL_PATH)/fifo \
    $(LOCAL_PATH)/legacy \
    $(LOCAL_PATH)/utility

# If you add a file here then also add it below in the SHARED target
LOCAL_SRC_FILES = \
    core/AudioStream.cpp \
    core/AudioStreamBuilder.cpp \
    core/AAudioAudio.cpp \
    core/AAudioStreamParameters.cpp \
    legacy/AudioStreamLegacy.cpp \
    legacy/AudioStreamRecord.cpp \
    legacy/AudioStreamTrack.cpp \
    utility/AAudioUtilities.cpp \
    utility/FixedBlockAdapter.cpp \
    utility/FixedBlockReader.cpp \
    utility/FixedBlockWriter.cpp \
    utility/LinearRamp.cpp \
    fifo/FifoBuffer.cpp \
    fifo/FifoControllerBase.cpp \
    client/AudioEndpoint.cpp \
    client/AudioStreamInternal.cpp \
    client/AudioStreamInternalCapture.cpp \
    client/AudioStreamInternalPlay.cpp \
    client/IsochronousClockModel.cpp \
    binding/AudioEndpointParcelable.cpp \
    binding/AAudioBinderClient.cpp \
    binding/AAudioStreamRequest.cpp \
    binding/AAudioStreamConfiguration.cpp \
    binding/IAAudioClient.cpp \
    binding/IAAudioService.cpp \
    binding/RingBufferParcelable.cpp \
    binding/SharedMemoryParcelable.cpp \
    binding/SharedRegionParcelable.cpp

LOCAL_CFLAGS += -Wno-unused-parameter -Wall -Werror

# By default, all symbols are hidden.
# LOCAL_CFLAGS += -fvisibility=hidden
# AAUDIO_API is used to explicitly export a function or a variable as a visible symbol.
LOCAL_CFLAGS += -DAAUDIO_API='__attribute__((visibility("default")))'

include $(BUILD_STATIC_LIBRARY)

# ======================= SHARED LIBRARY ==========================
include $(CLEAR_VARS)

LOCAL_MODULE := libaaudio
LOCAL_MODULE_TAGS := optional

LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-utils) \
    frameworks/native/include \
    system/core/base/include \
    frameworks/native/media/libaaudio/include/include \
    frameworks/av/media/libaaudio/include \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/binding \
    $(LOCAL_PATH)/client \
    $(LOCAL_PATH)/core \
    $(LOCAL_PATH)/fifo \
    $(LOCAL_PATH)/legacy \
    $(LOCAL_PATH)/utility

LOCAL_SRC_FILES = core/AudioStream.cpp \
    core/AudioStreamBuilder.cpp \
    core/AAudioAudio.cpp \
    core/AAudioStreamParameters.cpp \
    legacy/AudioStreamLegacy.cpp \
    legacy/AudioStreamRecord.cpp \
    legacy/AudioStreamTrack.cpp \
    utility/AAudioUtilities.cpp \
    utility/FixedBlockAdapter.cpp \
    utility/FixedBlockReader.cpp \
    utility/FixedBlockWriter.cpp \
    utility/LinearRamp.cpp \
    fifo/FifoBuffer.cpp \
    fifo/FifoControllerBase.cpp \
    client/AudioEndpoint.cpp \
    client/AudioStreamInternal.cpp \
    client/AudioStreamInternalCapture.cpp \
    client/AudioStreamInternalPlay.cpp \
    client/IsochronousClockModel.cpp \
    binding/AudioEndpointParcelable.cpp \
    binding/AAudioBinderClient.cpp \
    binding/AAudioStreamRequest.cpp \
    binding/AAudioStreamConfiguration.cpp \
    binding/IAAudioClient.cpp \
    binding/IAAudioService.cpp \
    binding/RingBufferParcelable.cpp \
    binding/SharedMemoryParcelable.cpp \
    binding/SharedRegionParcelable.cpp

LOCAL_CFLAGS += -Wno-unused-parameter -Wall -Werror

# By default, all symbols are hidden.
# LOCAL_CFLAGS += -fvisibility=hidden
# AAUDIO_API is used to explicitly export a function or a variable as a visible symbol.
LOCAL_CFLAGS += -DAAUDIO_API='__attribute__((visibility("default")))'

LOCAL_SHARED_LIBRARIES := libaudioclient liblog libcutils libutils libbinder libaudiomanager

include $(BUILD_SHARED_LIBRARY)
