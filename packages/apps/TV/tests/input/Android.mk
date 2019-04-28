LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := TVTestInput
LOCAL_MODULE_TAGS := optional
LOCAL_PROGUARD_ENABLED := disabled
# Overlay view related functionality requires system APIs.
LOCAL_SDK_VERSION := system_current
LOCAL_MIN_SDK_VERSION := 23  # M

LOCAL_STATIC_JAVA_LIBRARIES := \
    tv-test-common \
    tv-common

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/../common/res $(LOCAL_PATH)/res
LOCAL_AAPT_FLAGS := --auto-add-overlay \
    --extra-packages com.android.tv.testing

include $(BUILD_PACKAGE)

ifneq ($(filter TV,$(TARGET_BUILD_APPS)),)
  $(call dist-for-goals,apps_only,$(LOCAL_BUILT_MODULE):$(LOCAL_PACKAGE_NAME).apk)
endif

