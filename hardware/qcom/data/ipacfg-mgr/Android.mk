ifneq ($(TARGET_BOARD_AUTO),true)

    LOCAL_PATH := $(call my-dir)

    ifneq ($(filter msm8998,$(TARGET_BOARD_PLATFORM)),)
        include $(call first-makefiles-under,$(LOCAL_PATH)/msm8998)
    endif
endif
