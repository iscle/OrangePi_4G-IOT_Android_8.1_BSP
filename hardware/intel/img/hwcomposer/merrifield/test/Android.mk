# Build the unit tests,
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := nv12_ved_test

LOCAL_MODULE_TAGS := tests

LOCAL_SRC_FILES := \
    nv12_ved_test.cpp \

LOCAL_SHARED_LIBRARIES := \
	libEGL \
	libGLESv2 \
	libbinder \
	libcutils \
	libgui \
	libui \
	libutils \

LOCAL_C_INCLUDES := \
    $(call include-path-for, gtest) \

# Build the binary to $(TARGET_OUT_DATA_NATIVE_TESTS)/$(LOCAL_MODULE)
# to integrate with auto-test framework.
include $(BUILD_EXECUTABLE)
