# Copyright (C) 2015 The Android Open Source Project
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

# We define this in a subdir so that it won't pick up the parent's Android.xml by default.

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# current api, in XML format.
# NOTE: the output XML file is also used
# in //cts/hostsidetests/devicepolicy/AndroidTest.xml
# by com.android.cts.managedprofile.CurrentApiHelper
# ============================================================
include $(CLEAR_VARS)

LOCAL_MODULE := cts-current-api
LOCAL_MODULE_STEM := current.api
LOCAL_SRC_FILES := frameworks/base/api/current.txt

include $(LOCAL_PATH)/build_xml_api_file.mk

# current system api, in XML format.
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := cts-system-current-api
LOCAL_MODULE_STEM := system-current.api
LOCAL_SRC_FILES := frameworks/base/api/system-current.txt

include $(LOCAL_PATH)/build_xml_api_file.mk

# removed system api, in XML format.
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := cts-system-removed-api
LOCAL_MODULE_STEM := system-removed.api
LOCAL_SRC_FILES := frameworks/base/api/system-removed.txt

include $(LOCAL_PATH)/build_xml_api_file.mk

# current legacy-test api, in XML format.
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := cts-legacy-test-current-api
LOCAL_MODULE_STEM := legacy-test-current.api
LOCAL_SRC_FILES := frameworks/base/legacy-test/api/legacy-test-current.txt

include $(LOCAL_PATH)/build_xml_api_file.mk

# current android-test-mock api, in XML format.
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := cts-android-test-mock-current-api
LOCAL_MODULE_STEM := android-test-mock-current.api
LOCAL_SRC_FILES := frameworks/base/test-runner/api/android-test-mock-current.txt

include $(LOCAL_PATH)/build_xml_api_file.mk

# current android-test-runner api, in XML format.
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := cts-android-test-runner-current-api
LOCAL_MODULE_STEM := android-test-runner-current.api
LOCAL_SRC_FILES := frameworks/base/test-runner/api/android-test-runner-current.txt

include $(LOCAL_PATH)/build_xml_api_file.mk

# current apache-http-legacy api, in XML format.
# ==============================================
include $(CLEAR_VARS)
LOCAL_MODULE := cts-apache-http-legacy-current-api
LOCAL_MODULE_STEM := apache-http-legacy-current.api
LOCAL_SRC_FILES := external/apache-http/api/apache-http-legacy-current.txt

include $(LOCAL_PATH)/build_xml_api_file.mk
