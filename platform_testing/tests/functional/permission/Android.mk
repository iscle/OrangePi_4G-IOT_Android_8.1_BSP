LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_SDK_VERSION := current

LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_JAVA_LIBRARIES := legacy-android-test
LOCAL_STATIC_JAVA_LIBRARIES := \
    ub-uiautomator \
    launcher-helper-lib \
    permission-helper \
    package-helper \
    junit

LOCAL_PACKAGE_NAME := PermissionFunctionalTests
LOCAL_CERTIFICATE := platform

LOCAL_COMPATIBILITY_SUITE := device-tests

include $(BUILD_PACKAGE)
