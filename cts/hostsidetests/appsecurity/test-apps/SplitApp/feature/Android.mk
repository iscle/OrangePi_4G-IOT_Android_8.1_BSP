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

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_PACKAGE_NAME := CtsSplitAppFeature
LOCAL_PACKAGE_SPLITS := v7

LOCAL_ASSET_DIR := $(LOCAL_PATH)/assets

LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/cts-testkey1
LOCAL_AAPT_FLAGS := --version-code 100 --version-name OneHundred --replace-version

LOCAL_MODULE_TAGS := tests

# tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

featureOf := CtsSplitApp
featureOfApk := $(call intermediates-dir-for,APPS,$(featureOf))/package.apk
localRStamp := $(call intermediates-dir-for,APPS,$(LOCAL_PACKAGE_NAME),,COMMON)/src/R.stamp
$(localRStamp): $(featureOfApk)

LOCAL_AAPT_FLAGS += --feature-of $(featureOfApk)

include $(BUILD_CTS_SUPPORT_PACKAGE)
