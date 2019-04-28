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

# Targets for creating signed test versions of the time zone data app that can be used for
# manual testing and / or xts-type tests.

LOCAL_PATH := $(call my-dir)

# Paths used to find files shared with AOSP.
aosp_test_data_path := system/timezone/testing/data

# Target to build the "test 1" time zone data app.
include $(CLEAR_VARS)
LOCAL_ASSET_DIR := $(aosp_test_data_path)/test1/output_data/distro
TIME_ZONE_DATA_APP_SUFFIX := _test1

# OEM-INSTRUCTION: OEMs should come up with a suitable versioning strategy and this version should
# be guaranteed newer than the "real" app.
TIME_ZONE_DATA_APP_VERSION_CODE := 15
TIME_ZONE_DATA_APP_VERSION_NAME := test1

# When built, explicitly put it in the data partition since this is for tests, not the system image.
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

include $(LOCAL_PATH)/build_oem_test_data_app.mk


# Target to build the "test 2" time zone data app.
include $(CLEAR_VARS)
LOCAL_ASSET_DIR := $(aosp_test_data_path)/test2/output_data/distro
TIME_ZONE_DATA_APP_SUFFIX := _test2

# OEM-INSTRUCTION: OEMs should come up with a suitable versioning strategy and this version should
# be guaranteed newer than the "real" app.
TIME_ZONE_DATA_APP_VERSION_CODE := 20
TIME_ZONE_DATA_APP_VERSION_NAME := test2

# When built, explicitly put it in the data partition since this is for tests, not the system image.
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

include $(LOCAL_PATH)/build_oem_test_data_app.mk

# Tidy up variables.
aosp_test_data_path :=
