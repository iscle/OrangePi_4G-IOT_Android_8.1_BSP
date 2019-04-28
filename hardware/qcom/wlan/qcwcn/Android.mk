# adp8064 and fox box do not share wifi code
ifeq ($(filter adp8064 fox,$(TARGET_DEVICE)),)
  ifeq ($(BOARD_WLAN_DEVICE),qcwcn)
    include $(call all-subdir-makefiles)
  endif
endif
