# Emulated keymaster - ranchu build##########################################
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
ifeq ($(USE_32_BIT_KEYSTORE), true)
LOCAL_MULTILIB := 32
endif
LOCAL_MODULE := keystore.ranchu
LOCAL_VENDOR_MODULE := true
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_SRC_FILES := keymaster_module.cpp \
                   trusty_keymaster_ipc.cpp \
                   trusty_keymaster_device.cpp

LOCAL_C_INCLUDES := system/security/keystore \
                    $(LOCAL_PATH)/../include
LOCAL_CFLAGS = -fvisibility=hidden -Wall -Werror
LOCAL_SHARED_LIBRARIES := libcrypto liblog libsoftkeymasterdevice libkeymaster_messages libcutils
LOCAL_HEADER_LIBRARIES := libhardware_headers
LOCAL_MODULE_TAGS := optional
include $(BUILD_SHARED_LIBRARY)
