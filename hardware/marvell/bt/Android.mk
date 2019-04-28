ifneq ($(BOARD_HAVE_BLUETOOTH_MRVL),)
include $(call all-named-subdir-makefiles,libbt-vendor)
endif
