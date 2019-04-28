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

LOCAL_PATH:= $(call my-dir)

# A testing support library for testing time zone updates on real devices.
# OEMs can include this as a "_STATIC_" dependency and anything else needed to integrate with their
# own test suite. At runtime the LOCAL_JAVA_LIBRARIES below (or a superset) must be present.

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := time_zone_data_app_testing
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_JAVA_LIBRARIES := cts-tradefed tradefed compatibility-host-util
include $(BUILD_HOST_JAVA_LIBRARY)
