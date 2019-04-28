LOCAL_PATH := $(call my-dir)

# Component build
#######################################################################

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    src/Engine.cpp \
    src/EngineInstance.cpp \

audio_policy_engine_includes_common := \
    $(LOCAL_PATH)/include \
    frameworks/av/services/audiopolicy/engine/interface

LOCAL_CFLAGS += \
    -Wall \
    -Werror \
    -Wextra \

LOCAL_EXPORT_C_INCLUDE_DIRS := \
    $(audio_policy_engine_includes_common)

LOCAL_C_INCLUDES := \
    $(audio_policy_engine_includes_common) \
    $(TARGET_OUT_HEADERS)/hw \
    $(call include-path-for, frameworks-av) \
    $(call include-path-for, audio-utils) \
    $(call include-path-for, bionic) \
    frameworks/av/services/audiopolicy/common/include

LOCAL_MULTILIB := $(AUDIOSERVER_MULTILIB)

LOCAL_MODULE := libaudiopolicyenginedefault
LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_LIBRARIES := \
    libaudiopolicycomponents \

LOCAL_SHARED_LIBRARIES += \
    liblog \
    libcutils \
    libutils \
    libmedia_helper

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

ifeq ($(MTK_TTY_SUPPORT),yes)
    LOCAL_CFLAGS += -DMTK_TTY_SUPPORT
endif

LOCAL_CFLAGS += -DMTK_AUDIO_DEBUG
ifeq ($(strip $(TARGET_BUILD_VARIANT)),eng)
  LOCAL_CFLAGS += -DCONFIG_MT_ENG_BUILD
endif
endif#MTK_AUDIO

LOCAL_C_INCLUDES += $(TOPDIR)frameworks/av/services/audiopolicy

ifdef MTK_PATH_SOURCE
LOCAL_C_INCLUDES += \
    $(MTK_PATH_SOURCE)/hardware/audio/common/include \
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

include $(BUILD_SHARED_LIBRARY)
