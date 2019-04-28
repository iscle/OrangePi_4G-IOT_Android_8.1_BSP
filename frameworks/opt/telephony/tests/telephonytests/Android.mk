LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_SRC_FILES := $(call all-subdir-java-files)

#LOCAL_STATIC_JAVA_LIBRARIES := librilproto-java

LOCAL_JAVA_LIBRARIES := android.test.runner telephony-common ims-common services.core bouncycastle
LOCAL_STATIC_JAVA_LIBRARIES := guava \
                               frameworks-base-testutils \
                               mockito-target-minus-junit4 \
                               android-support-test \
                               platform-test-annotations \
                               legacy-android-test

LOCAL_PACKAGE_NAME := FrameworksTelephonyTests

LOCAL_COMPATIBILITY_SUITE := device-tests

include $(BUILD_PACKAGE)
