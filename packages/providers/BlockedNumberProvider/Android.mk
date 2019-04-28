LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

# Only compile source java files in this apk.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES += android-common guava

LOCAL_JACK_COVERAGE_INCLUDE_FILTER := com.android.providers.blockednumber.*

LOCAL_PACKAGE_NAME := BlockedNumberProvider
LOCAL_CERTIFICATE := shared
LOCAL_PRIVILEGED_MODULE := true

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
