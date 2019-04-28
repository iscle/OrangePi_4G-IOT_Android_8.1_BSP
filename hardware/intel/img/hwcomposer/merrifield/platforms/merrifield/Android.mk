# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)

# HAL module implemenation, not prelinked and stored in
# hw/<OVERLAY_HARDWARE_MODULE_ID>.<ro.product.board>.so
include $(CLEAR_VARS)

LOCAL_PRELINK_MODULE := false
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_SHARED_LIBRARIES := liblog libcutils libdrm \
                          libwsbm libutils libhardware \
                          libva libva-tpi libva-android libsync

LOCAL_HEADER_LIBRARIES := libsystem_headers

LOCAL_SRC_FILES := \
    ../../common/base/Drm.cpp \
    ../../common/base/HwcLayer.cpp \
    ../../common/base/HwcLayerList.cpp \
    ../../common/base/Hwcomposer.cpp \
    ../../common/base/HwcModule.cpp \
    ../../common/base/DisplayAnalyzer.cpp \
    ../../common/base/VsyncManager.cpp \
    ../../common/buffers/BufferCache.cpp \
    ../../common/buffers/GraphicBuffer.cpp \
    ../../common/buffers/BufferManager.cpp \
    ../../common/devices/PhysicalDevice.cpp \
    ../../common/devices/PrimaryDevice.cpp \
    ../../common/devices/ExternalDevice.cpp \
    ../../common/devices/VirtualDevice.cpp \
    ../../common/observers/UeventObserver.cpp \
    ../../common/observers/VsyncEventObserver.cpp \
    ../../common/observers/SoftVsyncObserver.cpp \
    ../../common/observers/MultiDisplayObserver.cpp \
    ../../common/planes/DisplayPlane.cpp \
    ../../common/planes/DisplayPlaneManager.cpp \
    ../../common/utils/Dump.cpp


LOCAL_SRC_FILES += \
    ../../ips/common/BlankControl.cpp \
    ../../ips/common/HdcpControl.cpp \
    ../../ips/common/DrmControl.cpp \
    ../../ips/common/VsyncControl.cpp \
    ../../ips/common/PrepareListener.cpp \
    ../../ips/common/OverlayPlaneBase.cpp \
    ../../ips/common/SpritePlaneBase.cpp \
    ../../ips/common/PixelFormat.cpp \
    ../../ips/common/PlaneCapabilities.cpp \
    ../../ips/common/GrallocBufferBase.cpp \
    ../../ips/common/GrallocBufferMapperBase.cpp \
    ../../ips/common/TTMBufferMapper.cpp \
    ../../ips/common/DrmConfig.cpp \
    ../../ips/common/VideoPayloadManager.cpp \
    ../../ips/common/Wsbm.cpp \
    ../../ips/common/WsbmWrapper.c \
    ../../ips/common/RotationBufferProvider.cpp

LOCAL_SRC_FILES += \
    ../../ips/tangier/TngGrallocBuffer.cpp \
    ../../ips/tangier/TngGrallocBufferMapper.cpp \
    ../../ips/tangier/TngOverlayPlane.cpp \
    ../../ips/tangier/TngPrimaryPlane.cpp \
    ../../ips/tangier/TngSpritePlane.cpp \
    ../../ips/tangier/TngDisplayQuery.cpp \
    ../../ips/tangier/TngPlaneManager.cpp \
    ../../ips/tangier/TngDisplayContext.cpp \
    ../../ips/tangier/TngCursorPlane.cpp


LOCAL_SRC_FILES += \
    PlatfBufferManager.cpp \
    PlatFactory.cpp

LOCAL_C_INCLUDES := $(addprefix $(LOCAL_PATH)/../../../, $(SGX_INCLUDES)) \
    $(call include-path-for, frameworks-native)/media/openmax \
    $(TARGET_OUT_HEADERS)/khronos/openmax \
    $(call include-path-for, opengl) \
    $(call include-path-for, libhardware_legacy)/hardware_legacy \
    prebuilts/intel/vendor/intel/hardware/prebuilts/$(REF_DEVICE_NAME)/rgx \
    prebuilts/intel/vendor/intel/hardware/prebuilts/$(REF_DEVICE_NAME)/rgx/include \
    vendor/intel/hardware/PRIVATE/widi/libhwcwidi/ \
    system/core \
    system/core/libsync/include \
    $(TARGET_OUT_HEADERS)/drm \
    $(TARGET_OUT_HEADERS)/libdrm \
    $(TARGET_OUT_HEADERS)/libdrm/shared-core \
    $(TARGET_OUT_HEADERS)/libwsbm/wsbm \
    $(TARGET_OUT_HEADERS)/libttm \
    $(TARGET_OUT_HEADERS)/libva

LOCAL_C_INCLUDES += $(LOCAL_PATH) \
    $(LOCAL_PATH)/../../include \
    $(LOCAL_PATH)/../../include/pvr/hal \
    $(LOCAL_PATH)/../../common/base \
    $(LOCAL_PATH)/../../common/buffers \
    $(LOCAL_PATH)/../../common/devices \
    $(LOCAL_PATH)/../../common/observers \
    $(LOCAL_PATH)/../../common/planes \
    $(LOCAL_PATH)/../../common/utils \
    $(LOCAL_PATH)/../../ips/ \
    $(LOCAL_PATH)/


LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := hwcomposer.$(TARGET_BOARD_PLATFORM)
LOCAL_CFLAGS += -DLINUX

ifeq ($(BOARD_PANEL_IS_180_ROTATED), true)
    $(warning  "Panel rotates 180")
    LOCAL_CFLAGS += -DENABLE_ROTATION_180
endif
ifeq ($(INTEL_WIDI), true)
   LOCAL_SHARED_LIBRARIES += libhwcwidi libbinder
   LOCAL_CFLAGS += -DINTEL_WIDI
endif

ifeq ($(TARGET_HAS_MULTIPLE_DISPLAY),true)
   LOCAL_SHARED_LIBRARIES += libmultidisplay libbinder
   LOCAL_CFLAGS += -DTARGET_HAS_MULTIPLE_DISPLAY
endif

LOCAL_COPY_HEADERS := \
    ../../include/pvr/hal/img_gralloc.h \
    ../../include/pvr/hal/img_gralloc1.h \
    ../../include/pvr/hal/img_gralloc_common_public.h \
    ../../include/pvr/hal/hal_public.h
LOCAL_COPY_HEADERS_TO := pvr/hal

ifneq ($(TARGET_BUILD_VARIANT),user)
   LOCAL_CFLAGS += -DHWC_TRACE_FPS
endif

include $(BUILD_SHARED_LIBRARY)

