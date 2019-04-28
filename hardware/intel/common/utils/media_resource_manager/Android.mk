
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
MEDIA_RESOURCE_MANAGER_ROOT := $(LOCAL_PATH)

include $(CLEAR_VARS)
include $(call all-makefiles-under,$(LOCAL_PATH))
