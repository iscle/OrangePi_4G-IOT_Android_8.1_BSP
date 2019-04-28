LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# Include all common java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_MODULE := tv-common
LOCAL_MODULE_CLASS := STATIC_JAVA_LIBRARIES
LOCAL_MODULE_TAGS := optional
LOCAL_SDK_VERSION := system_current

LOCAL_RESOURCE_DIR := \
    $(TOP)/prebuilts/sdk/current/support/compat/res \
    $(TOP)/prebuilts/sdk/current/support/v7/recyclerview/res \
    $(TOP)/prebuilts/sdk/current/support/v17/leanback/res \
    $(LOCAL_PATH)/res \

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-annotations \
    android-support-compat \
    android-support-core-ui \
    android-support-v7-recyclerview \
    android-support-v17-leanback \

LOCAL_AAPT_FLAGS := --auto-add-overlay \
    --extra-packages android.support.compat \
    --extra-packages android.support.v7.recyclerview \
    --extra-packages android.support.v17.leanback \


include $(LOCAL_PATH)/buildconfig.mk

include $(BUILD_STATIC_JAVA_LIBRARY)
