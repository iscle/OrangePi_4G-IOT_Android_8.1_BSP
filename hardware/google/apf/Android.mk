# Copyright 2016 The Android Open Source Project

LOCAL_PATH:= $(call my-dir)

APF_CFLAGS := -DAPF_FRAME_HEADER_SIZE=14 \
    -Wall \
    -Werror

include $(CLEAR_VARS)

LOCAL_INCLUDES += $(LOCAL_PATH)

LOCAL_CFLAGS += $(APF_CFLAGS)

LOCAL_SRC_FILES += apf_interpreter.c

LOCAL_MODULE:= libapf

include $(BUILD_STATIC_LIBRARY)


include $(CLEAR_VARS)

LOCAL_CFLAGS += $(APF_CFLAGS)
LOCAL_SRC_FILES += apf_disassembler.c
LOCAL_MODULE := apf_disassembler
LOCAL_MODULE_TAGS := debug

include $(BUILD_HOST_EXECUTABLE)

include $(CLEAR_VARS)

LOCAL_CFLAGS += $(APF_CFLAGS)
LOCAL_SRC_FILES += apf_run.c apf_interpreter.c
LOCAL_MODULE := apf_run
LOCAL_MODULE_TAGS := debug

include $(BUILD_HOST_EXECUTABLE)
