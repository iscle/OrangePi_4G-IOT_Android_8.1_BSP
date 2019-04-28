LOCAL_PATH := $(call my-dir)
# TODO:  Find a better way to separate build configs for ADP vs non-ADP devices
ifneq ($(BOARD_IS_AUTOMOTIVE),true)
  ifneq ($(filter msm8x27 msm8226,$(TARGET_BOARD_PLATFORM)),)
    include $(call all-named-subdir-makefiles,msm8960)
  else ifneq ($(filter msm8994,$(TARGET_BOARD_PLATFORM)),)
    include $(call all-named-subdir-makefiles,msm8992)
  else ifneq ($(wildcard $(LOCAL_PATH)/$(TARGET_BOARD_PLATFORM)),)
    include $(call all-named-subdir-makefiles,$(TARGET_BOARD_PLATFORM))
  endif
endif
