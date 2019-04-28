# TODO:  Find a better way to separate build configs for ADP vs non-ADP devices
ifneq ($(BOARD_IS_AUTOMOTIVE),true)
  ifneq ($(BOARD_VENDOR_QCOM_GPS_LOC_API_HARDWARE),)
    LOCAL_PATH := $(call my-dir)
    ifeq ($(BOARD_VENDOR_QCOM_LOC_PDK_FEATURE_SET),true)

      ifneq ($(filter apq8064,$(TARGET_BOARD_PLATFORM)),)
        #For apq8064 use msm8960
        include $(call all-named-subdir-makefiles,msm8960)
      else ifneq ($(filter msm8992,$(TARGET_BOARD_PLATFORM)),)
        #For msm8992 use msm8994
        include $(call all-named-subdir-makefiles,msm8994)
      else ifneq ($(filter msm8960 msm8084 msm8994 msm8996 msm8998 sdm845,$(TARGET_BOARD_PLATFORM)),)
        #For these, use their platform name as the subdirectory
        include $(call all-named-subdir-makefiles,$(TARGET_BOARD_PLATFORM))
      else ifeq ($(filter msm8916,$(TARGET_BOARD_PLATFORM)),)
        #For all other targets besides msm8916
        GPS_DIRS=core utils loc_api platform_lib_abstractions etc
        include $(call all-named-subdir-makefiles,$(GPS_DIRS))
      endif #TARGET_BOARD_PLATFORM

    else
      ifneq ($(filter sdm845,$(TARGET_BOARD_PLATFORM)),)
        include $(call all-named-subdir-makefiles,$(TARGET_BOARD_PLATFORM))
      else ifneq ($(filter msm8909 msm8226 ,$(TARGET_BOARD_PLATFORM)),)
        #For msm8909 target
        GPS_DIRS=msm8909/core msm8909/utils msm8909/loc_api msm8909/etc
        include $(call all-named-subdir-makefiles,$(GPS_DIRS))
      else ifeq ($(filter msm8916 ,$(TARGET_BOARD_PLATFORM)),)
        GPS_DIRS=core utils loc_api platform_lib_abstractions etc
        include $(call all-named-subdir-makefiles,$(GPS_DIRS))
      endif
    endif #BOARD_VENDOR_QCOM_LOC_PDK_FEATURE_SET

  endif #BOARD_VENDOR_QCOM_GPS_LOC_API_HARDWARE
endif
