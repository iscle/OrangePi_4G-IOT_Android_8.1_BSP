LOCAL_PATH:= $(call my-dir)

##################################
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    service.cpp \
    Enumerator.cpp \
    HalCamera.cpp \
    VirtualCamera.cpp \


LOCAL_SHARED_LIBRARIES := \
    libcutils \
    liblog \
    libutils \
    libui \
    libhidlbase \
    libhidltransport \
    libhardware \
    android.hardware.automotive.evs@1.0 \


LOCAL_INIT_RC := android.automotive.evs.manager@1.0.rc

LOCAL_MODULE := android.automotive.evs.manager@1.0

LOCAL_MODULE_TAGS := optional
LOCAL_STRIP_MODULE := keep_symbols

LOCAL_CFLAGS += -DLOG_TAG=\"EvsManager\"
LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES
LOCAL_CFLAGS += -Wall -Werror -Wunused -Wunreachable-code

include $(BUILD_EXECUTABLE)
