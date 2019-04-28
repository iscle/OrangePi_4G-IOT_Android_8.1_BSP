LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	main_audioserver.cpp

LOCAL_SHARED_LIBRARIES := \
	libaaudioservice \
	libaudioflinger \
	libaudiopolicyservice \
	libbinder \
	libcutils \
	liblog \
	libmedialogservice \
	libnbaio \
	libsoundtriggerservice \
	libutils \
	libhwbinder

# TODO oboeservice is the old folder name for aaudioservice. It will be changed.
LOCAL_C_INCLUDES := \
	frameworks/av/services/audioflinger \
	frameworks/av/services/audiopolicy \
	frameworks/av/services/audiopolicy/common/managerdefinitions/include \
	frameworks/av/services/audiopolicy/common/include \
	frameworks/av/services/audiopolicy/engine/interface \
	frameworks/av/services/audiopolicy/service \
	frameworks/av/services/medialog \
	frameworks/av/services/oboeservice \
	frameworks/av/services/radio \
	frameworks/av/services/soundtrigger \
	frameworks/av/media/libaaudio/include \
	frameworks/av/media/libaaudio/src \
	frameworks/av/media/libaaudio/src/binding \
	$(call include-path-for, audio-utils) \
	external/sonic \

# If AUDIOSERVER_MULTILIB in device.mk is non-empty then it is used to control
# the LOCAL_MULTILIB for all audioserver exclusive libraries.
# This is relevant for 64 bit architectures where either or both
# 32 and 64 bit libraries may be built.
#
# AUDIOSERVER_MULTILIB may be set as follows:
#   32      to build 32 bit audioserver libraries and 32 bit audioserver.
#   64      to build 64 bit audioserver libraries and 64 bit audioserver.
#   both    to build both 32 bit and 64 bit libraries,
#           and use primary target architecture (32 or 64) for audioserver.
#   first   to build libraries and audioserver for the primary target architecture only.
#   <empty> to build both 32 and 64 bit libraries and 32 bit audioserver.

ifeq ($(strip $(AUDIOSERVER_MULTILIB)),)
LOCAL_MULTILIB := 32
else
LOCAL_MULTILIB := $(AUDIOSERVER_MULTILIB)
endif

LOCAL_MODULE := audioserver

LOCAL_INIT_RC := audioserver.rc

LOCAL_CFLAGS := -Werror -Wall

#MTK_AUDIO B
ifdef MTK_PATH_SOURCE
LOCAL_C_INCLUDES += \
        $(MTK_PATH_SOURCE)/external/audiodcremoveflt \
        $(MTK_PATH_SOURCE)/external/AudioCompensationFilter \
        $(MTK_PATH_SOURCE)/external/AudioComponentEngine \
        $(MTK_PATH_SOURCE)/hardware/audio/common/aud_drv \
        $(MTK_PATH_SOURCE)/hardware/audio/common/ \
        $(MTK_PATH_SOURCE)/hardware/audio/common/include \
        $(MTK_PATH_SOURCE)/hardware/audio/common/V3/include \
        $(MTK_PATH_CUSTOM)/cgen/inc \
        $(MTK_PATH_CUSTOM)/cgen/cfgfileinc \
        $(MTK_PATH_CUSTOM)/cgen/cfgdefault \
        $(MTK_PATH_CUSTOM)/../common/cgen/inc \
        $(MTK_PATH_CUSTOM)/../common/cgen/cfgfileinc \
        $(MTK_PATH_CUSTOM)/../common/cgen/cfgdefault \
        $(MTK_PATH_SOURCE)/external/AudioDCRemoval \
        $(MTK_PATH_SOURCE)/external/blisrc/blisrc32 \
        $(MTK_PATH_SOURCE)/external/limiter \
        $(MTK_PATH_SOURCE)/external/shifter \
        $(MTK_PATH_SOURCE)/external/bessound_HD \
        $(MTK_PATH_SOURCE)/external/bessound \
        $(MTK_PATH_SOURCE)/external/audiocustparam \

ifeq ($(MTK_AUDIO),yes)
    LOCAL_CFLAGS += -DMTK_AUDIO
    LOCAL_CFLAGS += -DMTK_AUDIO_DEBUG
endif
endif
#MTK_AUDIO E

include $(BUILD_EXECUTABLE)
