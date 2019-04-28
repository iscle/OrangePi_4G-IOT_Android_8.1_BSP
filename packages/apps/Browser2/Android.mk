LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SDK_VERSION := current

LOCAL_SRC_FILES := \
        $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := junit

LOCAL_JAVA_LIBRARIES := legacy-android-test

LOCAL_PACKAGE_NAME := Browser2

include $(BUILD_PACKAGE)
