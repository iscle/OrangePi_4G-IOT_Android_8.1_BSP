LOCAL_PATH:= $(call my-dir)

# Build the Sample Embms Services
include $(CLEAR_VARS)

src_dirs := src
res_dirs := res

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))

LOCAL_PACKAGE_NAME := EmbmsTestService

LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
# Uncomment the following line to build the EmbmsTestService into the userdebug build.
# LOCAL_MODULE_TAGS := debug

include $(BUILD_PACKAGE)
