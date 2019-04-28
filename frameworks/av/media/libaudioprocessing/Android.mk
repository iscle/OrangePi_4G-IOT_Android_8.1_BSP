LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    AudioMixer.cpp.arm \
    AudioResampler.cpp.arm \
    AudioResamplerCubic.cpp.arm \
    AudioResamplerSinc.cpp.arm \
    AudioResamplerDyn.cpp.arm \
    BufferProviders.cpp \
    RecordBufferConverter.cpp \

LOCAL_C_INCLUDES := \
    $(TOP) \
    $(call include-path-for, audio-utils) \
    $(LOCAL_PATH)/include \

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/include

LOCAL_SHARED_LIBRARIES := \
    libaudiohal \
    libaudioutils \
    libcutils \
    liblog \
    libnbaio \
    libsonic \
    libutils \

LOCAL_MODULE := libaudioprocessing

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

ifeq ($(MTK_AUDIO),yes)
    LOCAL_SRC_FILES += \
        MtkAudioResamplerDyn.cpp.arm \
        AudioUtilmtk.cpp \

    LOCAL_SHARED_LIBRARIES += \
        libmedia_helper

    LOCAL_CFLAGS += -DMTK_AUDIO
    LOCAL_CFLAGS += -DMTK_AUDIO_DEBUG
    LOCAL_CFLAGS += -DMTK_AUDIO_FIX_DEFAULT_DEFECT

ifeq ($(strip $(TARGET_BUILD_VARIANT)),eng)
    LOCAL_CFLAGS += -DCONFIG_MT_ENG_BUILD
else ifeq ($(strip $(TARGET_BUILD_VARIANT)),userdebug)
    LOCAL_CFLAGS += -DCONFIG_MT_USERDEBUG_BUILD
endif

ifeq ($(strip $(MTK_BESLOUDNESS_SUPPORT)),yes)
    ifneq ($(strip $(MTK_BESLOUDNESS_RUN_WITH_HAL)),yes)
        LOCAL_SHARED_LIBRARIES += \
            libaudiocompensationfilter \
            libaudiocomponentengine \

        LOCAL_CFLAGS += -DMTK_AUDIOMIXER_ENABLE_DRC
#        LOCAL_CFLAGS += -DMTK_AUDIOMIXER_DRC_VOIP_ONLY
    endif
    ifneq ($(strip $(MTK_AUDIO_NUMBER_OF_SPEAKER)),1)
        LOCAL_CFLAGS += -DMTK_ENABLE_STEREO_SPEAKER
    endif
endif

ifeq ($(MTK_AUDIO_BLOUD_CUSTOMPARAMETER_REV),MTK_AUDIO_BLOUD_CUSTOMPARAMETER_V5)
    LOCAL_CFLAGS += -DMTK_AUDIO_BLOUD_CUSTOMPARAMETER_V5
else
    ifeq ($(strip $(MTK_AUDIO_BLOUD_CUSTOMPARAMETER_REV)),MTK_AUDIO_BLOUD_CUSTOMPARAMETER_V4)
        LOCAL_CFLAGS += -DMTK_AUDIO_BLOUD_CUSTOMPARAMETER_V4
    endif
endif

endif
# MTK>

# uncomment to disable NEON on architectures that actually do support NEON, for benchmarking
#LOCAL_CFLAGS += -DUSE_NEON=false

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
