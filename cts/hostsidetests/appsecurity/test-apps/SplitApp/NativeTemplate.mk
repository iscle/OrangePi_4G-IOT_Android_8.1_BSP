#
# Copyright (C) 2014 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := CtsSplitApp_ARCHARCH

LOCAL_JAVA_RESOURCE_DIRS := raw

LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/cts-testkey1
LOCAL_AAPT_FLAGS := --version-code 100 --replace-version

include $(BUILD_PACKAGE)
