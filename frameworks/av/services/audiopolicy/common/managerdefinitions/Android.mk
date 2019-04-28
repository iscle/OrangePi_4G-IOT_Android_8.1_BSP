LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    src/DeviceDescriptor.cpp \
    src/AudioGain.cpp \
    src/HwModule.cpp \
    src/IOProfile.cpp \
    src/AudioPort.cpp \
    src/AudioProfile.cpp \
    src/AudioRoute.cpp \
    src/AudioPolicyMix.cpp \
    src/AudioPatch.cpp \
    src/AudioInputDescriptor.cpp \
    src/AudioOutputDescriptor.cpp \
    src/AudioCollections.cpp \
    src/EffectDescriptor.cpp \
    src/SoundTriggerSession.cpp \
    src/SessionRoute.cpp \
    src/AudioSourceDescriptor.cpp \
    src/VolumeCurve.cpp \
    src/TypeConverter.cpp \
    src/AudioSession.cpp

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    libmedia \
    libutils \
    liblog \

LOCAL_EXPORT_SHARED_LIBRARY_HEADERS := libmedia

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include \
    frameworks/av/services/audiopolicy/common/include \
    frameworks/av/services/audiopolicy \
    frameworks/av/services/audiopolicy/utilities \

ifeq ($(USE_XML_AUDIO_POLICY_CONF), 1)

LOCAL_SRC_FILES += src/Serializer.cpp

LOCAL_SHARED_LIBRARIES += libicuuc libxml2

LOCAL_C_INCLUDES += \
    external/libxml2/include \
    external/icu/icu4c/source/common

else

LOCAL_SRC_FILES += \
    src/ConfigParsingUtils.cpp \
    src/StreamDescriptor.cpp \
    src/Gains.cpp

endif #ifeq ($(USE_XML_AUDIO_POLICY_CONF), 1)

LOCAL_EXPORT_C_INCLUDE_DIRS := \
    $(LOCAL_PATH)/include

LOCAL_MULTILIB := $(AUDIOSERVER_MULTILIB)

LOCAL_CFLAGS := -Wall -Werror

ifeq ($(MTK_AUDIO),yes)
LOCAL_CFLAGS += -DMTK_AUDIO
ifeq ($(strip $(MTK_AUDIO_TUNING_TOOL_VERSION)),V2.2)
    LOCAL_CFLAGS += -DMTK_AUDIO_GAIN_TABLE
else
    LOCAL_CFLAGS += -DMTK_AUDIO_GAIN_NVRAM
endif

ifeq ($(MTK_FM_SUPPORT),yes)
    LOCAL_CFLAGS += -DMTK_FM_SUPPORT
endif

LOCAL_CFLAGS += -DMTK_LOW_LATENCY

ifneq ($(MTK_USB_PHONECALL),)
  ifneq ($(strip $(MTK_USB_PHONECALL)),NONE)
    LOCAL_CFLAGS += -DMTK_USB_PHONECALL
  endif
endif

ifeq ($(MTK_BESLOUDNESS_SUPPORT),yes)
    LOCAL_CFLAGS += -DMTK_BESLOUDNESS_SUPPORT
endif

LOCAL_CFLAGS += -DMTK_AUDIO_FIX_DEFAULT_DEFECT

ifeq ($(MTK_TTY_SUPPORT),yes)
    LOCAL_CFLAGS += -DMTK_TTY_SUPPORT
endif

LOCAL_CFLAGS += -DMTK_AUDIO_DEBUG
ifeq ($(strip $(TARGET_BUILD_VARIANT)),eng)
  LOCAL_CFLAGS += -DCONFIG_MT_ENG_BUILD
endif
endif#MTK_AUDIO

ifdef MTK_PATH_SOURCE
LOCAL_C_INCLUDES += \
    $(MTK_PATH_SOURCE)/hardware/audio/common/include \
    $(MTK_PATH_SOURCE)/external/nvram/libnvram \
    $(MTK_PATH_CUSTOM)/custom \
    $(MTK_PATH_CUSTOM)/custom/audio \
    $(MTK_PATH_CUSTOM)/cgen/inc \
    $(MTK_PATH_CUSTOM)/cgen/cfgfileinc \
    $(MTK_PATH_CUSTOM)/cgen/cfgdefault \
    $(MTK_PATH_CUSTOM)/../common/cgen/inc \
    $(MTK_PATH_CUSTOM)/../common/cgen/cfgfileinc \
    $(MTK_PATH_CUSTOM)/../common/cgen/cfgdefault \
    $(MTK_PATH_CUSTOM)/hal/audioflinger/audio
endif

LOCAL_MODULE := libaudiopolicycomponents

include $(BUILD_STATIC_LIBRARY)
