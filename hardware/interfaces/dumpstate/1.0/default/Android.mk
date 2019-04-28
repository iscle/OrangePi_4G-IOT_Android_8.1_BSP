LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := android.hardware.dumpstate@1.0-service
LOCAL_INIT_RC := android.hardware.dumpstate@1.0-service.rc
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_PROPRIETARY_MODULE := true
LOCAL_SRC_FILES := \
    DumpstateDevice.cpp \
    service.cpp

LOCAL_SHARED_LIBRARIES := \
    android.hardware.dumpstate@1.0 \
    libbase \
    libcutils \
    libdumpstateutil \
    libhidlbase \
    libhidltransport \
    liblog \
    libutils

include $(BUILD_EXECUTABLE)
