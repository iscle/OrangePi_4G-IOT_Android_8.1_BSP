LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    AudioPolicyManagerCustomImpl.cpp \
    AudioPolicyServiceCustomImpl.cpp


LOCAL_C_INCLUDES := \
    $(TOPDIR)frameworks/av/services/audioflinger \
    $(call include-path-for, audio-utils) \
    $(TOPDIR)frameworks/av/services/audiopolicy/common/include \
    $(TOPDIR)frameworks/av/services/audiopolicy/engine/interface \
    $(TOPDIR)frameworks/av/services/audiopolicy/utilities \
    $(TOPDIR)frameworks/av/services/audiopolicy/service \
    $(TOPDIR)frameworks/av/services/audiopolicy/managerdefault \
    $(TOPDIR)frameworks/av/services/audiopolicy

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    libutils \
    liblog \
    libbinder \
    libaudioclient \
    libhardware_legacy \
    libserviceutility \
    libaudiopolicymanagerdefault \
    libmedia_helper

LOCAL_STATIC_LIBRARIES := \
    libaudiopolicycomponents

LOCAL_MULTILIB := $(AUDIOSERVER_MULTILIB)

LOCAL_MODULE:= libaudiopolicycustomextensions

LOCAL_CFLAGS += -Wall -Werror

ifeq ($(MTK_AUDIO),yes)
LOCAL_CFLAGS += -DMTK_AUDIO
LOCAL_CFLAGS += -DMTK_AUDIO_DEBUG

ifeq ($(strip $(TARGET_BUILD_VARIANT)),eng)
  LOCAL_CFLAGS += -DCONFIG_MT_ENG_BUILD
else ifeq ($(strip $(TARGET_BUILD_VARIANT)),userdebug)
  LOCAL_CFLAGS += -DCONFIG_MT_USERDEBUG_BUILD
endif

ifeq ($(strip $(MTK_AUDIO_TUNING_TOOL_VERSION)),V2.2)
    LOCAL_CFLAGS += -DMTK_AUDIO_GAIN_TABLE
else
    LOCAL_CFLAGS += -DMTK_AUDIO_GAIN_NVRAM
endif

ifeq ($(MTK_TTY_SUPPORT),yes)
    LOCAL_CFLAGS += -DMTK_TTY_SUPPORT
endif

ifeq ($(MTK_FM_SUPPORT),yes)
    LOCAL_CFLAGS += -DMTK_FM_SUPPORT
endif

ifeq ($(MTK_HIFIAUDIO_SUPPORT),yes)
    LOCAL_CFLAGS += -DMTK_HIFIAUDIO_SUPPORT
endif

ifneq ($(MTK_USB_PHONECALL),)
  ifneq ($(strip $(MTK_USB_PHONECALL)),NONE)
    LOCAL_CFLAGS += -DMTK_USB_PHONECALL
  endif
endif

LOCAL_CFLAGS += -DMTK_LOW_LATENCY

ifeq ($(MTK_BESLOUDNESS_SUPPORT),yes)
    LOCAL_CFLAGS += -DMTK_BESLOUDNESS_SUPPORT
endif

LOCAL_SHARED_LIBRARIES += \
     libaudiotoolkit

endif#MTK_AUDIO

ifdef MTK_PATH_SOURCE
LOCAL_C_INCLUDES += \
    $(MTK_PATH_SOURCE)/hardware/audio/common/include \
    $(MTK_PATH_SOURCE)/hardware/audio/common/V3/include \
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
