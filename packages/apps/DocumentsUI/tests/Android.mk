LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# unittests
LOCAL_MODULE_TAGS := tests
LOCAL_SRC_FILES := $(call all-java-files-under, common) \
    $(call all-java-files-under, unit) \
    $(call all-java-files-under, functional)

# For testing ZIP files. Include testing ZIP files as uncompresseed raw
# resources.
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
LOCAL_AAPT_FLAGS += -0 .zip

LOCAL_JAVA_LIBRARIES := android.test.runner
LOCAL_STATIC_JAVA_LIBRARIES := \
    mockito-target \
    ub-uiautomator \
    espresso-core \
    guava \
    legacy-android-test
LOCAL_JARJAR_RULES := $(LOCAL_PATH)/jarjar-rules.txt
LOCAL_PACKAGE_NAME := DocumentsUITests
LOCAL_COMPATIBILITY_SUITE := device-tests
LOCAL_INSTRUMENTATION_FOR := DocumentsUI
LOCAL_CERTIFICATE := platform
LOCAL_COMPATIBILITY_SUITE := device-tests

include $(BUILD_PACKAGE)
