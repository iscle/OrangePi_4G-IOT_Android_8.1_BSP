# Media Statistics service
#
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    main_mediametrics.cpp              \
    MetricsSummarizerCodec.cpp         \
    MetricsSummarizerExtractor.cpp     \
    MetricsSummarizerPlayer.cpp        \
    MetricsSummarizerRecorder.cpp      \
    MetricsSummarizer.cpp              \
    MediaAnalyticsService.cpp

LOCAL_SHARED_LIBRARIES := \
    libcutils                   \
    liblog                      \
    libmedia                    \
    libutils                    \
    libbinder                   \
    libdl                       \
    libgui                      \
    libmedia                    \
    libmediautils               \
    libmediametrics             \
    libstagefright_foundation   \
    libutils

LOCAL_STATIC_LIBRARIES := \
        libregistermsext

LOCAL_C_INCLUDES :=                                                 \
    $(TOP)/frameworks/av/media/libstagefright/include               \
    $(TOP)/frameworks/av/media/libstagefright/rtsp                  \
    $(TOP)/frameworks/av/media/libstagefright/wifi-display          \
    $(TOP)/frameworks/av/media/libstagefright/webm                  \
    $(TOP)/frameworks/av/include/media                              \
    $(TOP)/frameworks/av/include/camera                             \
    $(TOP)/frameworks/native/include/media/openmax                  \
    $(TOP)/frameworks/native/include/media/hardware                 \
    $(TOP)/external/tremolo/Tremolo                                 \
    libcore/include


LOCAL_MODULE:= mediametrics

LOCAL_INIT_RC := mediametrics.rc

LOCAL_CFLAGS := -Werror -Wall -Wno-error=deprecated-declarations
LOCAL_CLANG := true

#Disable mediametrics in GMO 512M
ifneq (yes,$(strip $(MTK_GMO_RAM_OPTIMIZE)))
include $(BUILD_EXECUTABLE)
endif
