LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := android.hardware.tests.extension.light@2.0-service
LOCAL_INIT_RC := android.hardware.tests.extension.light@2.0-service.rc
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_SRC_FILES := \
    Light.cpp \
    service.cpp

LOCAL_SHARED_LIBRARIES := \
    libhidlbase \
    libhidltransport \
    libutils \
    android.hardware.light@2.0 \
    android.hardware.tests.extension.light@2.0 \

include $(BUILD_EXECUTABLE)
