# Copyright 2011, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)

# Include res dir from chips
chips_dir := ../../../frameworks/opt/chips/res

#Include res dir from libraries
appcompat_dir := ../../../$(SUPPORT_LIBRARY_ROOT)/v7/appcompat/res
photo_dir := ../../../frameworks/opt/photoviewer/res ../../../frameworks/opt/photoviewer/appcompat/res
gridlayout_dir := ../../../$(SUPPORT_LIBRARY_ROOT)/v7/gridlayout/res
bitmap_dir := ../../../frameworks/opt/bitmap/res
datetimepicker_dir := ../../../frameworks/opt/datetimepicker/res
res_dirs := res $(appcompat_dir) $(chips_dir) $(photo_dir) $(gridlayout_dir) $(bitmap_dir) $(datetimepicker_dir)

##################################################
# Build APK
include $(CLEAR_VARS)

src_dirs := src unified_src
LOCAL_PACKAGE_NAME := UnifiedEmail

LOCAL_STATIC_JAVA_LIBRARIES := libchips
LOCAL_STATIC_JAVA_LIBRARIES += libphotoviewer_appcompat
LOCAL_STATIC_JAVA_LIBRARIES += guava
LOCAL_STATIC_JAVA_LIBRARIES += android-common
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-appcompat
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-gridlayout
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += android-opt-bitmap
LOCAL_STATIC_JAVA_LIBRARIES += android-opt-datetimepicker
LOCAL_STATIC_JAVA_LIBRARIES += owasp-html-sanitizer

LOCAL_SDK_VERSION := current

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs)) \
        $(call all-logtags-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))
LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_AAPT_FLAGS += --extra-packages com.android.ex.chips:com.android.ex.photo:android.support.v7.appcompat:android.support.v7.gridlayout:com.android.bitmap:com.android.datetimepicker

LOCAL_PROGUARD_FLAG_FILES := proguard.flags
ifeq (eng,$(TARGET_BUILD_VARIANT))
  LOCAL_PROGUARD_FLAG_FILES += proguard-test.flags
endif

LOCAL_JACK_COVERAGE_INCLUDE_FILTER := com.android.mail.*,com.android.emailcommon.*,com.google.android.mail.*

include $(BUILD_PACKAGE)

##################################################
# Build all sub-directories

include $(call all-makefiles-under,$(LOCAL_PATH))
