LOCAL_PATH:= $(call my-dir)

# Build the Sample Embms Download frontend
include $(CLEAR_VARS)

LOCAL_STATIC_JAVA_LIBRARIES := \
        android-support-v7-recyclerview \
        android-support-v4

src_dirs := src
res_dirs := res

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))

LOCAL_PACKAGE_NAME := EmbmsTestDownloadApp

LOCAL_CERTIFICATE := platform
LOCAL_MODULE_TAGS := tests

include $(BUILD_PACKAGE)
