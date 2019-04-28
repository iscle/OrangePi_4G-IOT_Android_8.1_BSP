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

ifneq ($(TARGET_BUILD_PDK), true)

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# To avoid build errors, build empty package for non-platform builds
# (for example, projected). See b/30064991
ifeq (,$(TARGET_BUILD_APPS))
  LOCAL_PACKAGE_NAME := CarSettings

  LOCAL_SRC_FILES := $(call all-java-files-under, src)

  LOCAL_STATIC_ANDROID_LIBRARIES := \
      android-support-v7-recyclerview \
      android-support-v7-appcompat \
      android-support-v7-preference \
      android-support-v14-preference \
      android-support-design

  LOCAL_RESOURCE_DIR := \
      $(LOCAL_PATH)/res \
      frameworks/support/v7/preference/res \
      frameworks/support/v14/preference/res \
      frameworks/support/design/res

  include packages/apps/Car/libs/car-stream-ui-lib/car-stream-ui-lib.mk

  LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.preference
  LOCAL_AAPT_FLAGS += --extra-packages android.support.v14.preference
  LOCAL_AAPT_FLAGS += --extra-packages android.support.design

  LOCAL_CERTIFICATE := platform

  LOCAL_MODULE_TAGS := optional

  LOCAL_PROGUARD_ENABLED := disabled

  LOCAL_PRIVILEGED_MODULE := true

  LOCAL_DEX_PREOPT := false

  LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4 \
                                 jsr305

  include packages/apps/Car/libs/car-stream-ui-lib/car-stream-ui-lib.mk
  include packages/apps/Car/libs/car-apps-common/car-apps-common.mk
  include packages/services/Car/car-support-lib/car-support.mk
  include frameworks/base/packages/SettingsLib/common.mk

  include $(BUILD_PACKAGE)
endif

# Use the following include to make our test apk.
ifeq (,$(ONE_SHOT_MAKEFILE))
include $(call all-makefiles-under,$(LOCAL_PATH))
endif

endif
