LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=                       \
        GenericSource.cpp               \
        HTTPLiveSource.cpp              \
        NuPlayer.cpp                    \
        NuPlayerCCDecoder.cpp           \
        NuPlayerDecoder.cpp             \
        NuPlayerDecoderBase.cpp         \
        NuPlayerDecoderPassThrough.cpp  \
        NuPlayerDriver.cpp              \
        NuPlayerDrm.cpp                 \
        NuPlayerRenderer.cpp            \
        NuPlayerStreamListener.cpp      \
        RTSPSource.cpp                  \
        StreamingSource.cpp             \

LOCAL_C_INCLUDES := \
	frameworks/av/media/libstagefright                     \
	frameworks/av/media/libstagefright/httplive            \
	frameworks/av/media/libstagefright/include             \
	frameworks/av/media/libstagefright/mpeg2ts             \
	frameworks/av/media/libstagefright/rtsp                \
	frameworks/av/media/libstagefright/timedtext           \
	frameworks/av/media/libmediaplayerservice              \
	frameworks/native/include/media/openmax

ifeq ($(strip $(MTK_DRM_APP)),yes)
    LOCAL_CFLAGS += -DMTK_DRM_APP
    LOCAL_C_INCLUDES += \
        frameworks/av/media/libmtkavenhancements
    LOCAL_SHARED_LIBRARIES += \
        libfw_drmutils
endif
LOCAL_CFLAGS += -Werror -Wall

# enable experiments only in userdebug and eng builds
ifneq (,$(filter userdebug eng,$(TARGET_BUILD_VARIANT)))
LOCAL_CFLAGS += -DENABLE_STAGEFRIGHT_EXPERIMENTS
endif

# for debug
ifeq ($(strip $(TARGET_BUILD_VARIANT)),eng)
    LOCAL_CFLAGS += -DALOGV_DEBUG
    LOCAL_CFLAGS += -DMMLOG_DEBUG
    LOCAL_CFLAGS += -DCONFIG_MT_ENG_BUILD
endif

# for ape
ifeq ($(strip $(MTK_AUDIO_APE_SUPPORT)), yes)
LOCAL_CFLAGS += -DMTK_AUDIO_APE_SUPPORT
endif

#for 24bit high resolution audio feature
ifeq ($(strip $(MTK_HIGH_RESOLUTION_AUDIO_SUPPORT)), yes)
LOCAL_CFLAGS += -DMTK_HIGH_RESOLUTION_AUDIO_SUPPORT
endif

LOCAL_SHARED_LIBRARIES :=       \
    libbinder                   \
    libui                       \
    libgui                      \
    libmedia                    \
    libmediadrm                 \

LOCAL_MODULE:= libstagefright_nuplayer

LOCAL_MODULE_TAGS := eng

LOCAL_SANITIZE := cfi
LOCAL_SANITIZE_DIAG := cfi

include $(BUILD_STATIC_LIBRARY)
