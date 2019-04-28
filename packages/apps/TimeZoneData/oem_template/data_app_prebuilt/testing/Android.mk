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

# Targets for including testing versions of the time zone data app needed
# by xTS tests.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
TIME_ZONE_DATA_APP_SUFFIX := _test1
include $(LOCAL_PATH)/include_test_prebuilt.mk

include $(CLEAR_VARS)
TIME_ZONE_DATA_APP_SUFFIX := _test2
include $(LOCAL_PATH)/include_test_prebuilt.mk
