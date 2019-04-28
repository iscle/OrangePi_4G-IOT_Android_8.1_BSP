LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(LOCAL_PATH)/../../../common.mk

LOCAL_MODULE                  := libsdmutils
LOCAL_VENDOR_MODULE           := true
LOCAL_MODULE_TAGS             := optional
LOCAL_C_INCLUDES              := $(common_includes)
LOCAL_HEADER_LIBRARIES        := display_headers
LOCAL_CFLAGS                  := -DLOG_TAG=\"SDM\" $(common_flags)
LOCAL_SRC_FILES               := debug.cpp \
                                 rect.cpp \
                                 sys.cpp \
                                 formats.cpp \
                                 utils.cpp

include $(BUILD_SHARED_LIBRARY)

SDM_HEADER_PATH := ../../include
include $(CLEAR_VARS)
LOCAL_VENDOR_MODULE           := true
LOCAL_COPY_HEADERS_TO         := $(common_header_export_path)/sdm/utils
LOCAL_COPY_HEADERS             = $(SDM_HEADER_PATH)/utils/constants.h \
                                 $(SDM_HEADER_PATH)/utils/debug.h \
                                 $(SDM_HEADER_PATH)/utils/formats.h \
                                 $(SDM_HEADER_PATH)/utils/locker.h \
                                 $(SDM_HEADER_PATH)/utils/rect.h \
                                 $(SDM_HEADER_PATH)/utils/sys.h \
                                 $(SDM_HEADER_PATH)/utils/sync_task.h \
                                 $(SDM_HEADER_PATH)/utils/utils.h \
                                 $(SDM_HEADER_PATH)/utils/factory.h

include $(BUILD_COPY_HEADERS)
