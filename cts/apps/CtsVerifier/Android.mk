#
# Copyright (C) 2010 The Android Open Source Project
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

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_MULTILIB := both

LOCAL_SRC_FILES := $(call all-java-files-under, src) $(call all-Iaidl-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := android-ex-camera2 \
                               compatibility-common-util-devicesidelib \
                               cts-sensors-tests \
                               cts-location-tests \
                               ctstestrunner \
                               apache-commons-math \
                               androidplot \
                               ctsverifier-opencv \
                               core-tests-support \
                               android-support-v4  \
                               mockito-target-minus-junit4 \
                               mockwebserver \
                               compatibility-device-util \
                               platform-test-annotations

LOCAL_JAVA_LIBRARIES := legacy-android-test

LOCAL_PACKAGE_NAME := CtsVerifier

LOCAL_JNI_SHARED_LIBRARIES := libctsverifier_jni \
		libaudioloopback_jni \
		libnativehelper_compat_libc++

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_SDK_VERSION := test_current

LOCAL_DEX_PREOPT := false
-include cts/error_prone_rules_tests.mk
include $(BUILD_PACKAGE)

# Build CTS verifier framework as a libary.

include $(CLEAR_VARS)

define java-files-in
$(sort $(patsubst ./%,%, \
  $(shell cd $(LOCAL_PATH) ; \
          find -L $(1) -maxdepth 1 -name *.java -and -not -name ".*") \
 ))
endef

LOCAL_MODULE := cts-verifier-framework
LOCAL_AAPT_FLAGS := --auto-add-overlay --extra-packages android.support.v4
LOCAL_SDK_VERSION := current
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
LOCAL_SRC_FILES := \
    $(call java-files-in, src/com/android/cts/verifier) \
    $(call all-Iaidl-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4 \
                               compatibility-common-util-devicesidelib \
                               compatibility-device-util \

include $(BUILD_STATIC_JAVA_LIBRARY)

# opencv library
include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
        ctsverifier-opencv:libs/opencv3-android.jar

include $(BUILD_MULTI_PREBUILT)

pre-installed-apps := \
    CtsEmptyDeviceAdmin \
    CtsPermissionApp \
    NotificationBot

other-required-apps := \
    CtsVerifierUSBCompanion \
    CtsVpnFirewallAppApi23 \
    CtsVpnFirewallAppApi24 \
    CtsVpnFirewallAppNotAlwaysOn

apps-to-include := \
    $(pre-installed-apps) \
    $(other-required-apps)

define apk-location-for
    $(call intermediates-dir-for,APPS,$(1))/package.apk
endef

# Builds and launches CTS Verifier on a device.
.PHONY: cts-verifier
cts-verifier: CtsVerifier adb $(pre-installed-apps)
	adb install -r $(PRODUCT_OUT)/data/app/CtsVerifier/CtsVerifier.apk \
		$(foreach app,$(pre-installed-apps), \
		    && adb install -r $(call apk-location-for,$(app))) \
		&& adb shell "am start -n com.android.cts.verifier/.CtsVerifierActivity"

#
# Creates a "cts-verifier" directory that will contain:
#
# 1. Out directory with a "android-cts-verifier" containing the CTS Verifier
#    and other binaries it needs.
#
# 2. Zipped version of the android-cts-verifier directory to be included with
#    the build distribution.
#
cts-dir := $(HOST_OUT)/cts-verifier
verifier-dir-name := android-cts-verifier
verifier-dir := $(cts-dir)/$(verifier-dir-name)
verifier-zip-name := $(verifier-dir-name).zip
verifier-zip := $(cts-dir)/$(verifier-zip-name)

# turned off sensor power tests in initial L release
#$(PRODUCT_OUT)/data/app/CtsVerifier.apk $(verifier-zip): $(verifier-dir)/power/execute_power_tests.py
#$(PRODUCT_OUT)/data/app/CtsVerifier.apk $(verifier-zip): $(verifier-dir)/power/power_monitors/monsoon.py

# Copy the necessary host-side scripts to include in the zip file:
#$(verifier-dir)/power/power_monitors/monsoon.py: cts/apps/CtsVerifier/assets/scripts/power_monitors/monsoon.py | $(ACP)
#	$(hide) mkdir -p $(verifier-dir)/power/power_monitors
#	$(hide) $(ACP) -fp cts/apps/CtsVerifier/assets/scripts/power_monitors/*.py $(verifier-dir)/power/power_monitors/.
#
#$(verifier-dir)/power/execute_power_tests.py: cts/apps/CtsVerifier/assets/scripts/execute_power_tests.py | $(ACP)
#	$(hide) mkdir -p $(verifier-dir)/power
#	$(hide) $(ACP) -fp cts/apps/CtsVerifier/assets/scripts/execute_power_tests.py $@

cts : $(verifier-zip)
$(verifier-zip) : $(HOST_OUT)/CameraITS
$(verifier-zip) : $(foreach app,$(apps-to-include),$(call apk-location-for,$(app)))
$(verifier-zip) : $(call intermediates-dir-for,APPS,CtsVerifier)/package.apk | $(ACP)
		$(hide) mkdir -p $(verifier-dir)
		$(hide) $(ACP) -fp $< $(verifier-dir)/CtsVerifier.apk
		$(foreach app,$(apps-to-include), \
		    $(ACP) -fp $(call apk-location-for,$(app)) $(verifier-dir)/$(app).apk;)
		$(hide) $(ACP) -fpr $(HOST_OUT)/CameraITS $(verifier-dir)
		$(hide) cd $(cts-dir) && zip -rq $(verifier-dir-name) $(verifier-dir-name)

ifneq ($(filter cts, $(MAKECMDGOALS)),)
  $(call dist-for-goals, cts, $(verifier-zip):$(verifier-zip-name))
endif

include $(call all-makefiles-under,$(LOCAL_PATH))
