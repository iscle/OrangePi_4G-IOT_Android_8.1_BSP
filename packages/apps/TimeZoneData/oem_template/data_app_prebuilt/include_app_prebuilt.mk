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

# An .mk include file that contains the boilerplate needed to include real and
# test, OEM-specific Time Zone Data app prebuilts.
#
# Users should set:
#   TIME_ZONE_DATA_APP_SUFFIX - the suffix to apply to the package name. Can be
#       empty, or contain things like _test1 for test .apk files.
#   LOCAL_COMPATIBILITY_SUITE - if the package is to be included in xTS tests.
#

LOCAL_MODULE := TimeZoneDataPrebuilt$(TIME_ZONE_DATA_APP_SUFFIX)
LOCAL_SRC_FILES := TimeZoneData$(TIME_ZONE_DATA_APP_SUFFIX).apk
LOCAL_OVERRIDES_PACKAGES := TimeZoneData$(TIME_ZONE_DATA_APP_SUFFIX)
LOCAL_MODULE_TAGS := optional

# OEM-INSTRUCTION: Change this
LOCAL_MODULE_OWNER := oemcorp
LOCAL_PRIVILEGED_MODULE := true

LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_MODULE_CLASS := APPS
# OEM-INSTRUCTION: Change this to match your app-specific signing certificate.
LOCAL_CERTIFICATE :=

# If LOCAL_COMPATIBILITY_SUITE is set this also copies the APK to the appropriate xTS directory
# and so this .mk can be used for prod and test targets.
include $(BUILD_PREBUILT)
