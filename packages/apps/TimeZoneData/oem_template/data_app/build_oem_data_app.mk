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
# An .mk include file that contains the boilerplate needed to build real and
# test, OEM-specific Time Zone Data apps.
#
# Users should set:
#   OEM_APP_PATH - the location of the OEM directory for the app, e.g. one that
#       contains the app res/ dir.
#   TIME_ZONE_DATA_APP_SUFFIX - the suffix to apply to the package name. Can be
#       empty, or contain things like _test1 for test .apk files.
#   TIME_ZONE_DATA_APP_VERSION_CODE - the version code for the .apk.
#   TIME_ZONE_DATA_APP_VERSION_NAME - the version name for the .apk.
#   LOCAL_COMPATIBILITY_SUITE - if the package is to be included in xTS tests.
#
LOCAL_MODULE_TAGS := optional

# All src comes from an AOSP static library.
LOCAL_STATIC_JAVA_LIBRARIES := time_zone_distro_provider

# All resources come from the vendor-specific dirs.
LOCAL_RESOURCE_DIR := $(OEM_APP_PATH)/res

# Ensure the app can be unbundled by only depending on System APIs.
LOCAL_SDK_VERSION := system_current

LOCAL_FULL_MANIFEST_FILE := $(OEM_APP_PATH)/AndroidManifest.xml

LOCAL_PACKAGE_NAME := TimeZoneData$(TIME_ZONE_DATA_APP_SUFFIX)

LOCAL_AAPT_FLAGS := --version-code $(TIME_ZONE_DATA_APP_VERSION_CODE) \
                    --version-name $(TIME_ZONE_DATA_APP_VERSION_NAME)

# OEM-INSTRUCTION: Modify the name, s/oemcorp/<Your company name>/
LOCAL_MODULE_OWNER := oemcorp
LOCAL_PRIVILEGED_MODULE := true

# OEM-INSTRUCTION: Configure your own certificate.
LOCAL_CERTIFICATE :=

include $(BUILD_PACKAGE)
