LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := power.$(TARGET_DEVICE)
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_SRC_FILES := power.c
LOCAL_CFLAGS := -Werror
LOCAL_SHARED_LIBRARIES := liblog
LOCAL_MODULE_TAGS := optional

# power.c uses of GNU old-style field designator extension
LOCAL_CLANG_CFLAGS += -Wno-gnu-designator

include $(BUILD_SHARED_LIBRARY)
