LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    ServiceUtilities.cpp

# FIXME Move this library to frameworks/native
LOCAL_MODULE := libserviceutility

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    libutils \
    liblog \
    libbinder

LOCAL_CFLAGS := -Wall -Werror

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:=               \
    AudioFlinger.cpp            \
    Threads.cpp                 \
    Tracks.cpp                  \
    AudioHwDevice.cpp           \
    AudioStreamOut.cpp          \
    SpdifStreamOut.cpp          \
    Effects.cpp                 \
    PatchPanel.cpp              \
    StateQueue.cpp              \
    BufLog.cpp                  \
    TypedLogger.cpp

LOCAL_C_INCLUDES := \
    frameworks/av/services/audiopolicy \
    frameworks/av/services/medialog \
    $(call include-path-for, audio-utils)

LOCAL_SHARED_LIBRARIES := \
    libaudiohal \
    libaudioprocessing \
    libaudiospdif \
    libaudioutils \
    libcutils \
    libutils \
    liblog \
    libbinder \
    libaudioclient \
    libmedialogservice \
    libmediautils \
    libnbaio \
    libpowermanager \
    libserviceutility \
    libmediautils \
    libmemunreachable \
    libmedia_helper

LOCAL_STATIC_LIBRARIES := \
    libcpustats \

LOCAL_MULTILIB := $(AUDIOSERVER_MULTILIB)

# <MTK
ifdef MTK_PATH_SOURCE
LOCAL_C_INCLUDES += \
    $(MTK_PATH_COMMON)/cgen/inc \
    $(MTK_PATH_COMMON)/cgen/cfgfileinc \
    $(MTK_PATH_COMMON)/cgen/cfgdefault \
    $(MTK_PATH_SOURCE)/external/bessound_HD \
    $(MTK_PATH_SOURCE)/external/AudioCompensationFilter \
    $(MTK_PATH_SOURCE)/external/AudioComponentEngine
endif

ifeq ($(MTK_AUDIO),yes)
    LOCAL_CFLAGS += -DMTK_AUDIO
    LOCAL_CFLAGS += -DMTK_AUDIO_DEBUG
    LOCAL_CFLAGS += -DMTK_LOW_LATENCY
    LOCAL_CFLAGS += -DMTK_LOW_POWER
    LOCAL_CFLAGS += -DMTK_AUDIO_FIX_DEFAULT_DEFECT

ifeq ($(strip $(TARGET_BUILD_VARIANT)),eng)
    LOCAL_CFLAGS += -DCONFIG_MT_ENG_BUILD
endif

ifeq ($(MTK_HIFIAUDIO_SUPPORT),yes)
    LOCAL_CFLAGS += -DMTK_HIFIAUDIO_SUPPORT
endif
ifeq ($(strip $(MTK_BESLOUDNESS_SUPPORT)),yes)
    ifneq ($(strip $(MTK_BESLOUDNESS_RUN_WITH_HAL)),yes)
       LOCAL_SHARED_LIBRARIES += \
           libaudiocompensationfilter \
           libaudiocomponentengine

       LOCAL_CFLAGS += -DMTK_AUDIOMIXER_ENABLE_DRC
    endif
endif

endif
# MTK>

LOCAL_MODULE:= libaudioflinger

LOCAL_SRC_FILES += \
    AudioWatchdog.cpp        \
    FastCapture.cpp          \
    FastCaptureDumpState.cpp \
    FastCaptureState.cpp     \
    FastMixer.cpp            \
    FastMixerDumpState.cpp   \
    FastMixerState.cpp       \
    FastThread.cpp           \
    FastThreadDumpState.cpp  \
    FastThreadState.cpp

LOCAL_CFLAGS += -DSTATE_QUEUE_INSTANTIATIONS='"StateQueueInstantiations.cpp"'

LOCAL_CFLAGS += -fvisibility=hidden

LOCAL_CFLAGS += -Werror -Wall

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
