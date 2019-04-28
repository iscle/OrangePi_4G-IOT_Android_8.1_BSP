# Copyright (C) 2017 The Android Open Source Project
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

# Targets for creating the real signed versions of the time zone data app.

LOCAL_PATH := $(call my-dir)

OEM_APP_PATH := $(LOCAL_PATH)

# Target to build the "real" time zone data app.
include $(CLEAR_VARS)
LOCAL_ASSET_DIR := system/timezone/output_data/distro
TIME_ZONE_DATA_APP_SUFFIX :=

# OEM-INSTRUCTION: OEMs should come up with a suitable versioning strategy.
TIME_ZONE_DATA_APP_VERSION_CODE := 10
TIME_ZONE_DATA_APP_VERSION_NAME := 10

include $(OEM_APP_PATH)/build_oem_data_app.mk

include $(call all-makefiles-under,$(LOCAL_PATH))
