LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE := android.hardware.gatekeeper@1.0-impl

LOCAL_SRC_FILES := \
    Gatekeeper.cpp \

LOCAL_SHARED_LIBRARIES := \
    android.hardware.gatekeeper@1.0 \
    libhardware \
    libhidlbase \
    libhidltransport \
    libutils \
    liblog \

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE := android.hardware.gatekeeper@1.0-service
LOCAL_INIT_RC := android.hardware.gatekeeper@1.0-service.rc

LOCAL_SRC_FILES := \
    service.cpp    \

LOCAL_SHARED_LIBRARIES := \
    android.hardware.gatekeeper@1.0 \
    libhardware \
    libhidlbase \
    libhidltransport \
    libutils \
    liblog \

include $(BUILD_EXECUTABLE)
