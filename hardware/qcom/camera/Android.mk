# TODO:  Find a better way to separate build configs for ADP vs non-ADP devices
ifneq ($(TARGET_BOARD_AUTO),true)
  ifneq ($(strip $(USE_CAMERA_STUB)),true)
    ifneq ($(BUILD_TINY_ANDROID),true)
      ifneq ($(USE_VR_CAMERA_HAL), true)
        ifneq ($(filter msm8998,$(TARGET_BOARD_PLATFORM)),)
          include $(call all-makefiles-under,$(call my-dir)/msm8998)
        endif
      endif
    endif
  endif
endif
