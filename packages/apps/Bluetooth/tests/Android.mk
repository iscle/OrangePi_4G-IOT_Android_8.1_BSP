LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests
LOCAL_CERTIFICATE := platform

LOCAL_JAVA_LIBRARIES := \
    javax.obex android.test.runner \
    telephony-common \
    libprotobuf-java-micro

LOCAL_STATIC_JAVA_LIBRARIES :=  \
    com.android.emailcommon \
    android-support-test \
    mockito-target \
    legacy-android-test

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)
# LOCAL_SRC_FILES := src/com/android/bluetooth/tests/BluetoothMapContentTest.java

LOCAL_PACKAGE_NAME := BluetoothProfileTests

LOCAL_INSTRUMENTATION_FOR := Bluetooth

include $(BUILD_PACKAGE)
