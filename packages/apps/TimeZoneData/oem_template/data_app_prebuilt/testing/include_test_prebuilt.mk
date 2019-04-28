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
#
# An .mk include file that contains the boilerplate needed to include test,
# OEM-specific Time Zone Data app prebuilts.
#
# Users should set:
#   TIME_ZONE_DATA_APP_SUFFIX - the suffix to apply to the package name.
#       Should contain things like _test1 for test .apk files.
#

PREBUILT_PATH := $(LOCAL_PATH)/..

# Turn off pre-opting. We want these to be installable.
LOCAL_DEX_PREOPT := false

# OEM-INSTRUCTION: Change this to match your OEM-specific test suite.
# If a value is here the .apk will automatically be included in the associated
# test suite build, e.g. if there is an oem-specific tradefed suite called "OTS",
# then put ots here.
# Required for the xTS TimeZoneUpdateHostTest to pass because it needs correctly
# signed OEM-specific apks available to work.
LOCAL_COMPATIBILITY_SUITE :=

include $(PREBUILT_PATH)/include_app_prebuilt.mk

