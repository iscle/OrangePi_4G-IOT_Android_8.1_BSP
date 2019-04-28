LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests


# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-test \
    mockito-target \
    tv-test-common \


LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/../common/res

LOCAL_PACKAGE_NAME := TVUnitTests

LOCAL_INSTRUMENTATION_FOR := LiveTv

LOCAL_SDK_VERSION := system_current
LOCAL_MIN_SDK_VERSION := 23  # M

include $(BUILD_PACKAGE)

