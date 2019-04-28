LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE                  := libdrmutils
LOCAL_VENDOR_MODULE           := true
LOCAL_MODULE_TAGS             := optional
LOCAL_C_INCLUDES              := external/libdrm \
                                 $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr/include
LOCAL_SHARED_LIBRARIES        := libdrm libdl
LOCAL_CFLAGS                  := -DLOG_TAG=\"DRMUTILS\" -Wall -std=c++11 -Werror -fno-operator-names
LOCAL_CLANG                   := true
LOCAL_ADDITIONAL_DEPENDENCIES := $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr
LOCAL_SRC_FILES               := drm_master.cpp drm_res_mgr.cpp drm_lib_loader.cpp
LOCAL_COPY_HEADERS_TO         := qcom/display
LOCAL_COPY_HEADERS            := drm_master.h drm_res_mgr.h drm_lib_loader.h drm_logger.h drm_interface.h

include $(BUILD_SHARED_LIBRARY)
