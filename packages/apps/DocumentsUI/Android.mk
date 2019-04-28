LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_PRIVILEGED_MODULE := true

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES += guava

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_STATIC_ANDROID_LIBRARIES := \
        android-support-v4 \
        android-support-v7-appcompat \
        android-support-v13 \
        android-support-design \
        android-support-transition \
        android-support-v7-recyclerview

LOCAL_USE_AAPT2 := true

LOCAL_JACK_FLAGS := \
  -D jack.optimization.inner-class.accessors=true

# Only enable asserts on userdebug/eng builds
ifneq (,$(filter userdebug eng, $(TARGET_BUILD_VARIANT)))
LOCAL_JACK_FLAGS += -D jack.assert.policy=always
endif

LOCAL_PACKAGE_NAME := DocumentsUI
LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)
include $(call all-makefiles-under, $(LOCAL_PATH))
