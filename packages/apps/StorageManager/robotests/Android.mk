#############################################
# Add app-specific Robolectric test target. #
#############################################
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

# Include the testing libraries (JUnit4 + Robolectric libs).
LOCAL_STATIC_JAVA_LIBRARIES := \
    mockito-robolectric-prebuilt \
    truth-prebuilt

LOCAL_JAVA_LIBRARIES := \
    junit \
    platform-robolectric-prebuilt

# TODO: Remove the use of LOCAL_INSTRUMENTATION_FOR and use a different build flag.
LOCAL_INSTRUMENTATION_FOR := StorageManager
LOCAL_MODULE := StorageManagerRoboTests

LOCAL_MODULE_TAGS := optional

include $(BUILD_STATIC_JAVA_LIBRARY)

#############################################################
# Add Robolectric runner target to run the previous target. #
#############################################################
include $(CLEAR_VARS)

LOCAL_MODULE := RunStorageManagerRoboTests

LOCAL_SDK_VERSION := current

LOCAL_STATIC_JAVA_LIBRARIES := \
    StorageManagerRoboTests

LOCAL_TEST_PACKAGE := StorageManager

include prebuilts/misc/common/robolectric/run_robotests.mk