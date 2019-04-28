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
# An .mk include file that contains the boilerplate needed to build test,
# OEM-specific Time Zone Data apps.
#
# Users should set:
#   TIME_ZONE_DATA_APP_SUFFIX - the suffix to apply to the package name.
#       Should contain things like _test1 for test .apk files.
#   TIME_ZONE_DATA_APP_VERSION_CODE - the version code for the .apk.
#   TIME_ZONE_DATA_APP_VERSION_NAME - the version name for the .apk.
#

OEM_APP_PATH := $(LOCAL_PATH)/..
include $(OEM_APP_PATH)/build_oem_data_app.mk
