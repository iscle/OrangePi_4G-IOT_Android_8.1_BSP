#############################################
# Car Settings Robolectric test target. #
#############################################
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

# Include the testing libraries (JUnit4 + Robolectric libs).
LOCAL_STATIC_JAVA_LIBRARIES := \
    platform-system-robolectric \
    truth-prebuilt

LOCAL_JAVA_LIBRARIES := \
    junit \
    platform-robolectric-prebuilt \
    sdk_vcurrent

LOCAL_INSTRUMENTATION_FOR := CarSettings
LOCAL_MODULE := CarSettingsRoboTests

LOCAL_MODULE_TAGS := optional

include $(BUILD_STATIC_JAVA_LIBRARY)

#############################################################
# Car Settings runner target to run the previous target. #
#############################################################
include $(CLEAR_VARS)

LOCAL_MODULE := RunCarSettingsRoboTests

LOCAL_SDK_VERSION := current

LOCAL_STATIC_JAVA_LIBRARIES := \
    CarSettingsRoboTests

LOCAL_TEST_PACKAGE := CarSettings

include prebuilts/misc/common/robolectric/run_robotests.mk