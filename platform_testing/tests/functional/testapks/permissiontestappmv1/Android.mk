LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

# omit gradle 'build' dir
LOCAL_SRC_FILES := $(call all-java-files-under,src)

LOCAL_STATIC_JAVA_LIBRARIES = android-support-v4
LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/res
LOCAL_PACKAGE_NAME := PermissionTestAppMV1
LOCAL_CERTIFICATE := platform

LOCAL_COMPATIBILITY_SUITE := device-tests

include $(BUILD_PACKAGE)
