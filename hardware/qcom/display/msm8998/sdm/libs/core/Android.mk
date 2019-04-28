LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(LOCAL_PATH)/../../../common.mk

LOCAL_MODULE                  := libsdmcore
LOCAL_VENDOR_MODULE           := true
LOCAL_MODULE_TAGS             := optional
LOCAL_C_INCLUDES              := $(common_includes) $(kernel_includes)
LOCAL_HEADER_LIBRARIES        := display_headers
LOCAL_CFLAGS                  := -Wno-unused-parameter -DLOG_TAG=\"SDM\" \
                                 $(common_flags)
ifeq ($(use_hwc2),false)
  LOCAL_CFLAGS += -DUSE_SPECULATIVE_FENCES
endif
LOCAL_HW_INTF_PATH_1          := fb
LOCAL_SHARED_LIBRARIES        := libdl libsdmutils

ifneq ($(TARGET_IS_HEADLESS), true)
    LOCAL_CFLAGS              += -isystem external/libdrm
    LOCAL_SHARED_LIBRARIES    += libdrm libdrmutils
    LOCAL_HW_INTF_PATH_2      := drm
endif

ifeq ($(TARGET_USES_DRM_PP),true)
    LOCAL_CFLAGS              += -DPP_DRM_ENABLE
endif

LOCAL_ADDITIONAL_DEPENDENCIES := $(common_deps) $(kernel_deps)
LOCAL_SRC_FILES               := core_interface.cpp \
                                 core_impl.cpp \
                                 display_base.cpp \
                                 display_primary.cpp \
                                 display_hdmi.cpp \
                                 display_virtual.cpp \
                                 comp_manager.cpp \
                                 strategy.cpp \
                                 resource_default.cpp \
                                 dump_impl.cpp \
                                 color_manager.cpp \
                                 hw_events_interface.cpp \
                                 hw_info_interface.cpp \
                                 hw_interface.cpp \
                                 $(LOCAL_HW_INTF_PATH_1)/hw_info.cpp \
                                 $(LOCAL_HW_INTF_PATH_1)/hw_device.cpp \
                                 $(LOCAL_HW_INTF_PATH_1)/hw_primary.cpp \
                                 $(LOCAL_HW_INTF_PATH_1)/hw_hdmi.cpp \
                                 $(LOCAL_HW_INTF_PATH_1)/hw_virtual.cpp \
                                 $(LOCAL_HW_INTF_PATH_1)/hw_color_manager.cpp \
                                 $(LOCAL_HW_INTF_PATH_1)/hw_scale.cpp \
                                 $(LOCAL_HW_INTF_PATH_1)/hw_events.cpp

ifneq ($(TARGET_IS_HEADLESS), true)
    LOCAL_SRC_FILES           += $(LOCAL_HW_INTF_PATH_2)/hw_info_drm.cpp \
                                 $(LOCAL_HW_INTF_PATH_2)/hw_device_drm.cpp \
                                 $(LOCAL_HW_INTF_PATH_2)/hw_events_drm.cpp \
                                 $(LOCAL_HW_INTF_PATH_2)/hw_color_manager_drm.cpp
endif

include $(BUILD_SHARED_LIBRARY)

SDM_HEADER_PATH := ../../include
include $(CLEAR_VARS)
LOCAL_VENDOR_MODULE           := true
LOCAL_COPY_HEADERS_TO         := $(common_header_export_path)/sdm/core
LOCAL_COPY_HEADERS             = $(SDM_HEADER_PATH)/core/buffer_allocator.h \
                                 $(SDM_HEADER_PATH)/core/buffer_sync_handler.h \
                                 $(SDM_HEADER_PATH)/core/core_interface.h \
                                 $(SDM_HEADER_PATH)/core/debug_interface.h \
                                 $(SDM_HEADER_PATH)/core/display_interface.h \
                                 $(SDM_HEADER_PATH)/core/dump_interface.h \
                                 $(SDM_HEADER_PATH)/core/layer_buffer.h \
                                 $(SDM_HEADER_PATH)/core/layer_stack.h \
                                 $(SDM_HEADER_PATH)/core/sdm_types.h \
                                 $(SDM_HEADER_PATH)/core/socket_handler.h
include $(BUILD_COPY_HEADERS)

include $(CLEAR_VARS)
LOCAL_VENDOR_MODULE           := true
LOCAL_COPY_HEADERS_TO         := $(common_header_export_path)/sdm/private
LOCAL_COPY_HEADERS             = $(SDM_HEADER_PATH)/private/color_interface.h \
                                 $(SDM_HEADER_PATH)/private/color_params.h \
                                 $(SDM_HEADER_PATH)/private/extension_interface.h \
                                 $(SDM_HEADER_PATH)/private/hw_info_types.h \
                                 $(SDM_HEADER_PATH)/private/partial_update_interface.h \
                                 $(SDM_HEADER_PATH)/private/resource_interface.h \
                                 $(SDM_HEADER_PATH)/private/strategy_interface.h \
                                 $(SDM_HEADER_PATH)/private/dpps_control_interface.h
include $(BUILD_COPY_HEADERS)
