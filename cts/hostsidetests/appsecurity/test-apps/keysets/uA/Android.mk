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

LOCAL_PATH:= $(call my-dir)

#apks signed by cts-keyset-test-a
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := tests
LOCAL_SDK_VERSION := current
LOCAL_STATIC_JAVA_LIBRARIES := android-support-test
LOCAL_PACKAGE_NAME := CtsKeySetSigningAUpgradeA
LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/keysets/cts-keyset-test-a
LOCAL_DEX_PREOPT := false
# tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

include $(BUILD_CTS_SUPPORT_PACKAGE)

#apks signed by cts-keyset-test-b
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := tests
LOCAL_SDK_VERSION := current
LOCAL_STATIC_JAVA_LIBRARIES := android-support-test
LOCAL_PACKAGE_NAME := CtsKeySetSigningBUpgradeA
LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/keysets/cts-keyset-test-b
LOCAL_DEX_PREOPT := false
# tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

include $(BUILD_CTS_SUPPORT_PACKAGE)

#apks signed by cts-keyset-test-ec-a
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := tests
LOCAL_SDK_VERSION := current
LOCAL_STATIC_JAVA_LIBRARIES := android-support-test
LOCAL_PACKAGE_NAME := CtsKeySetSigningEcAUpgradeA
LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/keysets/cts-keyset-test-ec-a
LOCAL_DEX_PREOPT := false
# tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

include $(BUILD_CTS_SUPPORT_PACKAGE)

#apks signed by cts-keyset-test-a and cts-keyset-test-b
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := tests
LOCAL_SDK_VERSION := current
LOCAL_STATIC_JAVA_LIBRARIES := android-support-test
LOCAL_PACKAGE_NAME := CtsKeySetSigningAAndBUpgradeA
LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/keysets/cts-keyset-test-a
LOCAL_ADDITIONAL_CERTIFICATES := cts/hostsidetests/appsecurity/certs/keysets/cts-keyset-test-b
LOCAL_DEX_PREOPT := false
# tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

include $(BUILD_CTS_SUPPORT_PACKAGE)
