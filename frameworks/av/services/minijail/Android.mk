LOCAL_PATH := $(call my-dir)

# Small library for media.extractor and media.codec sandboxing.
include $(CLEAR_VARS)
LOCAL_MODULE := libavservices_minijail
LOCAL_SRC_FILES := minijail.cpp
LOCAL_SHARED_LIBRARIES := libbase libminijail
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)
include $(BUILD_SHARED_LIBRARY)

# Small library for media.extractor and media.codec sandboxing.
include $(CLEAR_VARS)
LOCAL_MODULE := libavservices_minijail_vendor
LOCAL_VENDOR_MODULE := true
LOCAL_SRC_FILES := minijail.cpp
LOCAL_SHARED_LIBRARIES := libbase libminijail_vendor
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)
include $(BUILD_SHARED_LIBRARY)

# Unit tests.
include $(CLEAR_VARS)
LOCAL_MODULE := libavservices_minijail_unittest
LOCAL_SRC_FILES := minijail.cpp av_services_minijail_unittest.cpp
LOCAL_SHARED_LIBRARIES := libbase libminijail
include $(BUILD_NATIVE_TEST)
