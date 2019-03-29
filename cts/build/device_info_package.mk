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

#
# Builds a package which can be instrumented to retrieve information about the device under test.
#

DEVICE_INFO_PACKAGE := com.android.compatibility.common.deviceinfo
DEVICE_INFO_INSTRUMENT := android.support.test.runner.AndroidJUnitRunner
DEVICE_INFO_PERMISSIONS += android.permission.WRITE_EXTERNAL_STORAGE
DEVICE_INFO_ACTIVITIES += \
  $(DEVICE_INFO_PACKAGE).ConfigurationDeviceInfo \
  $(DEVICE_INFO_PACKAGE).CpuDeviceInfo \
  $(DEVICE_INFO_PACKAGE).FeatureDeviceInfo \
  $(DEVICE_INFO_PACKAGE).GenericDeviceInfo \
  $(DEVICE_INFO_PACKAGE).GlesStubActivity \
  $(DEVICE_INFO_PACKAGE).GraphicsDeviceInfo \
  $(DEVICE_INFO_PACKAGE).LocaleDeviceInfo \
  $(DEVICE_INFO_PACKAGE).MediaDeviceInfo \
  $(DEVICE_INFO_PACKAGE).MemoryDeviceInfo \
  $(DEVICE_INFO_PACKAGE).PackageDeviceInfo \
  $(DEVICE_INFO_PACKAGE).PropertyDeviceInfo \
  $(DEVICE_INFO_PACKAGE).ScreenDeviceInfo \
  $(DEVICE_INFO_PACKAGE).StorageDeviceInfo \
  $(DEVICE_INFO_PACKAGE).UserDeviceInfo \
  $(DEVICE_INFO_PACKAGE).VintfDeviceInfo

ifeq ($(DEVICE_INFO_MIN_SDK),)
DEVICE_INFO_MIN_SDK := 8
endif

ifeq ($(DEVICE_INFO_TARGET_SDK),)
DEVICE_INFO_TARGET_SDK := 8
endif

# Add the base device info
LOCAL_STATIC_JAVA_LIBRARIES += compatibility-device-info compatibility-device-util

# Generator of APK manifests.
MANIFEST_GENERATOR_JAR := $(HOST_OUT_JAVA_LIBRARIES)/compatibility-manifest-generator.jar
MANIFEST_GENERATOR := $(JAVA) -jar $(MANIFEST_GENERATOR_JAR)

# Generate the manifest
manifest_xml := $(call intermediates-dir-for,APPS,$(LOCAL_PACKAGE_NAME))/AndroidManifest.xml
$(manifest_xml): PRIVATE_INFO_PERMISSIONS := $(foreach permission, $(DEVICE_INFO_PERMISSIONS),-r $(permission))
$(manifest_xml): PRIVATE_INFO_ACTIVITIES := $(foreach activity,$(DEVICE_INFO_ACTIVITIES),-a $(activity))
$(manifest_xml): PRIVATE_PACKAGE := $(DEVICE_INFO_PACKAGE)
$(manifest_xml): PRIVATE_INSTRUMENT := $(DEVICE_INFO_INSTRUMENT)
$(manifest_xml): PRIVATE_MIN_SDK := $(DEVICE_INFO_MIN_SDK)
$(manifest_xml): PRIVATE_TARGET_SDK := $(DEVICE_INFO_TARGET_SDK)

# Regenerate manifest.xml if the generator jar, */cts-device-info/Android.mk, or this file is changed.
$(manifest_xml): $(MANIFEST_GENERATOR_JAR) $(LOCAL_PATH)/Android.mk cts/build/device_info_package.mk
	$(hide) echo Generating manifest for $(PRIVATE_NAME)
	$(hide) mkdir -p $(dir $@)
	$(hide) $(MANIFEST_GENERATOR) \
						$(PRIVATE_INFO_PERMISSIONS) \
						$(PRIVATE_INFO_ACTIVITIES) \
						-p $(PRIVATE_PACKAGE) \
						-i $(PRIVATE_INSTRUMENT) \
						-s $(PRIVATE_MIN_SDK) \
						-t $(PRIVATE_TARGET_SDK) \
						-o $@

# Reset variables
DEVICE_INFO_MIN_SDK :=
DEVICE_INFO_TARGET_SDK :=
DEVICE_INFO_PACKAGE :=
DEVICE_INFO_INSTRUMENT :=
DEVICE_INFO_PERMISSIONS :=
DEVICE_INFO_ACTIVITIES :=

LOCAL_FULL_MANIFEST_FILE := $(manifest_xml)
# Disable by default
LOCAL_DEX_PREOPT := false
LOCAL_PROGUARD_ENABLED := disabled

# Don't include this package in any target
LOCAL_MODULE_TAGS := optional
# And when built explicitly put it in the data partition
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_SDK_VERSION := current

include $(BUILD_CTS_SUPPORT_PACKAGE)
