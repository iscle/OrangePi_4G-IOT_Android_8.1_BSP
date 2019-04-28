#############################################
# ManagedProvisioning Robolectric test target. #
#############################################
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

# Include the testing libraries (JUnit4 + Robolectric libs).
LOCAL_STATIC_JAVA_LIBRARIES := \
    platform-system-robolectric

LOCAL_JAVA_LIBRARIES := \
    junit \
    platform-robolectric-prebuilt \
    telephony-common

LOCAL_INSTRUMENTATION_FOR := ManagedProvisioning
LOCAL_MODULE := ManagedProvisioningRoboTests

LOCAL_MODULE_TAGS := optional

include $(BUILD_STATIC_JAVA_LIBRARY)

#############################################################
# Settings runner target to run the previous target. #
#############################################################
include $(CLEAR_VARS)

LOCAL_MODULE := RunManagedProvisioningRoboTests

LOCAL_SDK_VERSION := current

LOCAL_STATIC_JAVA_LIBRARIES := \
    ManagedProvisioningRoboTests

LOCAL_TEST_PACKAGE := ManagedProvisioning

include prebuilts/misc/common/robolectric/run_robotests.mk