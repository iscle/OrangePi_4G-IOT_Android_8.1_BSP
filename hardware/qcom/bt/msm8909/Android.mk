ifneq ($(filter msm8909,$(TARGET_BOARD_PLATFORM)),)
include $(call all-named-subdir-makefiles,libbt-vendor)
endif # is-vendor-board-platform
