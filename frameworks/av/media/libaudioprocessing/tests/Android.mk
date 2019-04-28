# Build the unit tests for libaudioprocessing

LOCAL_PATH := $(call my-dir)

#
# resampler unit test
#
include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES := \
    libaudioutils \
    libaudioprocessing \
    libcutils \
    liblog \
    libutils \

LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-utils) \

LOCAL_SRC_FILES := \
    resampler_tests.cpp

LOCAL_MODULE := resampler_tests

LOCAL_MODULE_TAGS := tests

LOCAL_CFLAGS := -Werror -Wall

include $(BUILD_NATIVE_TEST)

#
# audio mixer test tool
#
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    test-mixer.cpp \

LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-utils) \

LOCAL_STATIC_LIBRARIES := \
    libsndfile \

LOCAL_SHARED_LIBRARIES := \
    libaudioprocessing \
    libaudioutils \
    libcutils \
    liblog \
    libutils \

LOCAL_MODULE := test-mixer

LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS := -Werror -Wall

# <MTK
ifdef MTK_PATH_SOURCE
LOCAL_C_INCLUDES += \
    $(TOPDIR)vendor/mediatek/proprietary/custom/common/cgen/inc \
    $(TOPDIR)vendor/mediatek/proprietary/custom/common/cgen/cfgfileinc \
    $(TOPDIR)vendor/mediatek/proprietary/custom/common/cgen/cfgdefault \
    $(TOPDIR)vendor/mediatek/proprietary/external/bessound_HD \
    $(TOPDIR)vendor/mediatek/proprietary/external/AudioCompensationFilter \
    $(TOPDIR)vendor/mediatek/proprietary/external/AudioComponentEngine
endif
# MTK>

include $(BUILD_EXECUTABLE)

#
# build audio resampler test tool
#
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    test-resampler.cpp \

LOCAL_C_INCLUDES := \
    $(call include-path-for, audio-utils) \

LOCAL_STATIC_LIBRARIES := \
    libsndfile \

LOCAL_SHARED_LIBRARIES := \
    libaudioprocessing \
    libaudioutils \
    libcutils \
    liblog \
    libutils \

LOCAL_MODULE := test-resampler

LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS := -Werror -Wall

include $(BUILD_EXECUTABLE)
