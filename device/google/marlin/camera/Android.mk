# TODO:  Find a better way to separate build configs for ADP vs non-ADP devices
ifneq ($(TARGET_BOARD_AUTO),true)
  ifneq ($(strip $(USE_CAMERA_STUB)),true)
    ifneq ($(BUILD_TINY_ANDROID),true)
      ifneq ($(USE_VR_CAMERA_HAL), true)
        ifneq ($(filter msm8996,$(TARGET_BOARD_PLATFORM)),)
          include $(addsuffix /Android.mk, $(addprefix $(call my-dir)/, mm-image-codec QCamera2))
        endif
      endif
    endif
  endif
endif
