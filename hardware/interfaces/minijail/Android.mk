LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libhwminijail
LOCAL_PROPRIETARY_MODULE := true
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/include
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := HardwareMinijail.cpp

LOCAL_SHARED_LIBRARIES := \
    libbase \
    libminijail_vendor

include $(BUILD_SHARED_LIBRARY)
