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

TI_WILINK_FW_PATH := $(TARGET_OUT_ETC)/firmware/ti-connectivity

include $(CLEAR_VARS)
LOCAL_MODULE := TIInit_11.8.32.bts
ifeq ($(TARGET_PRODUCT),hikey960)
LOCAL_SRC_FILES := TIInit_11.8.32-pcm-960.bts
else
LOCAL_SRC_FILES := TIInit_11.8.32.bts
endif
LOCAL_MODULE_CLASS := FIRMWARE
LOCAL_MODULE_PATH := $(TI_WILINK_FW_PATH)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_OWNER := ti
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := wl18xx-fw-4.bin
LOCAL_SRC_FILES := wl18xx-fw-4.bin
LOCAL_MODULE_CLASS := FIRMWARE
LOCAL_MODULE_PATH := $(TI_WILINK_FW_PATH)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_OWNER := ti
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := wl18xx-conf.bin
ifeq ($(TARGET_PRODUCT),hikey960)
LOCAL_SRC_FILES := wl18xx-conf-wl1837mod.bin
else
LOCAL_SRC_FILES := wl18xx-conf.bin
endif
LOCAL_MODULE_CLASS := FIRMWARE
LOCAL_MODULE_PATH := $(TI_WILINK_FW_PATH)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_OWNER := ti
include $(BUILD_PREBUILT)
