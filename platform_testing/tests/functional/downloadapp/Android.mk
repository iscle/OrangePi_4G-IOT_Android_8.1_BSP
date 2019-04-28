LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_SDK_VERSION := current

LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_JAVA_LIBRARIES := legacy-android-test
LOCAL_STATIC_JAVA_LIBRARIES := \
    launcher-helper-lib \
    ub-uiautomator \
    android-support-test

LOCAL_PACKAGE_NAME := DownloadAppFunctionalTests
LOCAL_CERTIFICATE := platform

LOCAL_COMPATIBILITY_SUITE := device-tests

include $(BUILD_PACKAGE)
