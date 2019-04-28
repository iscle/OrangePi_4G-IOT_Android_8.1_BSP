##
# Build the platform version of setup wizard library.
#

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_USE_AAPT2 := true
LOCAL_JAVA_LIBRARIES := \
    android-support-annotations
LOCAL_MANIFEST_FILE := main/AndroidManifest.xml
LOCAL_MODULE := setup-wizard-lib
LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/main/res \
    $(LOCAL_PATH)/platform/res
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := $(call all-java-files-under, main/src platform/src)

include $(BUILD_STATIC_JAVA_LIBRARY)


##
# Build gingerbread-compat library, which uses AppCompat support library to provide backwards
# compatibility back to SDK v9.
#

include $(CLEAR_VARS)

ifeq ($(TARGET_BUILD_APPS),)
# Use AAPT2 only when TARGET_BUILD_APPS is empty because AAPT2 is not compatible with the current
# setup of prebuilt support libs used in unbundled builds. b/29836407
LOCAL_USE_AAPT2 := true
endif

LOCAL_MANIFEST_FILE := main/AndroidManifest.xml
LOCAL_MODULE := setup-wizard-lib-gingerbread-compat
LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/main/res \
    $(LOCAL_PATH)/gingerbread/res \
    $(LOCAL_PATH)/recyclerview/res
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := $(call all-java-files-under, main/src gingerbread/src recyclerview/src)

ifdef LOCAL_USE_AAPT2

LOCAL_SHARED_ANDROID_LIBRARIES := \
    android-support-annotations \
    android-support-compat \
    android-support-core-ui \
    android-support-v7-appcompat \
    android-support-v7-recyclerview

else

LOCAL_AAPT_FLAGS := --auto-add-overlay \
    --extra-packages android.support.compat \
    --extra-packages android.support.v7.appcompat \
    --extra-packages android.support.v7.recyclerview

LOCAL_RESOURCE_DIR += \
    frameworks/support/compat/res \
    frameworks/support/v7/appcompat/res \
    frameworks/support/v7/recyclerview/res

LOCAL_JAVA_LIBRARIES := \
    android-support-annotations \
    android-support-compat \
    android-support-core-ui \
    android-support-v7-appcompat \
    android-support-v7-recyclerview

endif

include $(BUILD_STATIC_JAVA_LIBRARY)
