ifeq ($(strip $(BOARD_USES_ALSA_AUDIO)),true)

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_ARM_MODE := arm

AUDIO_PLATFORM := $(TARGET_BOARD_PLATFORM)
ifneq ($(filter msm8960,$(TARGET_BOARD_PLATFORM)),)
  LOCAL_CFLAGS += -DMAX_TARGET_SPECIFIC_CHANNEL_CNT="2"
endif
ifneq ($(filter msm8974 msm8226 msm8084 msm8992 msm8994 msm8996 msm8998,$(TARGET_BOARD_PLATFORM)),)
  # B-family platform uses msm8974 code base
  AUDIO_PLATFORM = msm8974
ifneq ($(filter msm8974,$(TARGET_BOARD_PLATFORM)),)
  LOCAL_CFLAGS := -DPLATFORM_MSM8974
  LOCAL_CFLAGS += -DMAX_TARGET_SPECIFIC_CHANNEL_CNT="2"
endif
ifneq ($(filter msm8226,$(TARGET_BOARD_PLATFORM)),)
  LOCAL_CFLAGS := -DPLATFORM_MSM8x26
  LOCAL_CFLAGS += -DMAX_TARGET_SPECIFIC_CHANNEL_CNT="2"
endif
ifneq ($(filter msm8084,$(TARGET_BOARD_PLATFORM)),)
  LOCAL_CFLAGS := -DPLATFORM_MSM8084
  LOCAL_CFLAGS += -DMAX_TARGET_SPECIFIC_CHANNEL_CNT="2"
endif
ifneq ($(filter msm8992,$(TARGET_BOARD_PLATFORM)),)
  LOCAL_CFLAGS := -DPLATFORM_MSM8994
  LOCAL_CFLAGS += -DMAX_TARGET_SPECIFIC_CHANNEL_CNT="4"
  LOCAL_CFLAGS += -DKPI_OPTIMIZE_ENABLED
endif
ifneq ($(filter msm8994,$(TARGET_BOARD_PLATFORM)),)
  LOCAL_CFLAGS := -DPLATFORM_MSM8994
  LOCAL_CFLAGS += -DMAX_TARGET_SPECIFIC_CHANNEL_CNT="4"
  LOCAL_CFLAGS += -DKPI_OPTIMIZE_ENABLED
endif
ifneq ($(filter msm8996,$(TARGET_BOARD_PLATFORM)),)
  LOCAL_CFLAGS := -DPLATFORM_MSM8996
  LOCAL_CFLAGS += -DMAX_TARGET_SPECIFIC_CHANNEL_CNT="4"
  LOCAL_CFLAGS += -DKPI_OPTIMIZE_ENABLED
  MULTIPLE_HW_VARIANTS_ENABLED := true
endif
ifneq ($(filter msm8998,$(TARGET_BOARD_PLATFORM)),)
  LOCAL_CFLAGS := -DPLATFORM_MSM8998
  LOCAL_CFLAGS += -DMAX_TARGET_SPECIFIC_CHANNEL_CNT="4"
  LOCAL_CFLAGS += -DKPI_OPTIMIZE_ENABLED
  MULTIPLE_HW_VARIANTS_ENABLED := true
endif
endif

ifneq ($(filter msm8916 msm8909 msm8952,$(TARGET_BOARD_PLATFORM)),)
  AUDIO_PLATFORM = msm8916
  LOCAL_CFLAGS := -DPLATFORM_MSM8916
ifneq ($(filter msm8909,$(TARGET_BOARD_PLATFORM)),)
  LOCAL_CFLAGS := -DPLATFORM_MSM8909
endif
  LOCAL_CFLAGS += -DMAX_TARGET_SPECIFIC_CHANNEL_CNT="2"
  LOCAL_CFLAGS += -DKPI_OPTIMIZE_ENABLED
  MULTIPLE_HW_VARIANTS_ENABLED := true
endif

LOCAL_SRC_FILES := \
	audio_hw.c \
	voice.c \
	platform_info.c \
	audio_extn/ext_speaker.c \
	audio_extn/audio_extn.c \
	audio_extn/utils.c \
	$(AUDIO_PLATFORM)/platform.c \
        acdb.c

ifdef MULTIPLE_HW_VARIANTS_ENABLED
  LOCAL_CFLAGS += -DHW_VARIANTS_ENABLED
  LOCAL_SRC_FILES +=  $(AUDIO_PLATFORM)/hw_info.c
endif

ifeq ($(strip $(AUDIO_FEATURE_ENABLED_USB_TUNNEL)),true)
    LOCAL_CFLAGS += -DUSB_TUNNEL_ENABLED
    LOCAL_SRC_FILES += audio_extn/usb.c
endif

ifeq ($(strip $(AUDIO_FEATURE_ENABLED_USB_SIDETONE_VOLUME)),true)
    LOCAL_CFLAGS += -DUSB_SIDETONE_VOLUME
endif

LOCAL_SHARED_LIBRARIES := \
	libaudioutils \
	liblog \
	libcutils \
	libtinyalsa \
	libtinycompress \
	libaudioroute \
	libdl \
	libexpat

LOCAL_C_INCLUDES += \
	external/tinyalsa/include \
	external/tinycompress/include \
	$(call include-path-for, audio-route) \
	$(call include-path-for, audio-effects) \
	$(LOCAL_PATH)/$(AUDIO_PLATFORM) \
	$(LOCAL_PATH)/audio_extn \
	$(LOCAL_PATH)/voice_extn \
	external/expat/lib

ifeq ($(strip $(AUDIO_FEATURE_ENABLED_SMART_PA_TFA_98XX)),true)
    LOCAL_SHARED_LIBRARIES += libexTfa98xx
    LOCAL_CFLAGS += -DSMART_PA_TFA_98XX_SUPPORTED
    LOCAL_SRC_FILES += audio_extn/tfa_98xx.c
endif

ifeq ($(strip $(AUDIO_FEATURE_ENABLED_MULTI_VOICE_SESSIONS)),true)
    LOCAL_CFLAGS += -DMULTI_VOICE_SESSION_ENABLED
    LOCAL_SRC_FILES += voice_extn/voice_extn.c
endif

ifeq ($(strip $(AUDIO_FEATURE_ENABLED_HFP)),true)
    LOCAL_CFLAGS += -DHFP_ENABLED
    LOCAL_SRC_FILES += audio_extn/hfp.c
endif

ifeq ($(strip $(AUDIO_FEATURE_SUPPORTED_EXTERNAL_BT)),true)
    LOCAL_CFLAGS += -DEXTERNAL_BT_SUPPORTED
endif

ifeq ($(strip $(AUDIO_FEATURE_NO_AUDIO_OUT)),true)
    LOCAL_CFLAGS += -DNO_AUDIO_OUT
endif

ifeq ($(strip $(BOARD_SUPPORTS_SOUND_TRIGGER)),true)
    LOCAL_CFLAGS += -DSOUND_TRIGGER_ENABLED
    LOCAL_CFLAGS += -DSOUND_TRIGGER_PLATFORM_NAME=$(TARGET_BOARD_PLATFORM)
    LOCAL_C_INCLUDES += $(TARGET_OUT_HEADERS)/mm-audio/sound_trigger
    LOCAL_SRC_FILES += audio_extn/soundtrigger.c
ifneq ($(filter msm8996,$(TARGET_BOARD_PLATFORM)),)
LOCAL_HEADER_LIBRARIES := sound_trigger.primary_headers
endif

endif

ifeq ($(strip $(AUDIO_FEATURE_ENABLED_SPKR_PROTECTION)),true)
    LOCAL_CFLAGS += -DSPKR_PROT_ENABLED
    LOCAL_SRC_FILES += audio_extn/spkr_protection.c
endif

ifeq ($(strip $(AUDIO_FEATURE_ENABLED_DSM_FEEDBACK)),true)
    LOCAL_CFLAGS += -DDSM_FEEDBACK_ENABLED
    LOCAL_SRC_FILES += audio_extn/dsm_feedback.c
endif

ifneq ($(filter msm8992 msm8994 msm8996 msm8998,$(TARGET_BOARD_PLATFORM)),)
  # push codec/mad calibration to HW dep node
  # applicable to msm8992/8994 or newer platforms
  LOCAL_CFLAGS += -DHWDEP_CAL_ENABLED
  LOCAL_SRC_FILES += audio_extn/hwdep_cal.c
endif

ifeq ($(strip $(AUDIO_FEATURE_ENABLED_SND_MONITOR)), true)
    LOCAL_CFLAGS += -DSND_MONITOR_ENABLED
    LOCAL_SRC_FILES += audio_extn/sndmonitor.c
endif

LOCAL_HEADER_LIBRARIES += libhardware_headers

LOCAL_MODULE := audio.primary.$(TARGET_BOARD_PLATFORM)

LOCAL_MODULE_RELATIVE_PATH := hw

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE_OWNER := qcom

LOCAL_PROPRIETARY_MODULE := true

LOCAL_CFLAGS += -Werror

include $(BUILD_SHARED_LIBRARY)

endif
