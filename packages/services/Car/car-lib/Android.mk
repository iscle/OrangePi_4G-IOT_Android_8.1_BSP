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
#

#disble build in PDK, missing aidl import breaks build
ifneq ($(TARGET_BUILD_PDK),true)

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := android.car
LOCAL_MODULE_TAGS := optional

ifneq ($(TARGET_USES_CAR_FUTURE_FEATURES),true)
#TODO need a tool to generate proguard rule to drop all items under @FutureFeature
#LOCAL_PROGUARD_ENABLED := custom
#LOCAL_PROGUARD_FLAG_FILES := proguard_drop_future.flags
endif

car_lib_sources := $(call all-java-files-under, src)
ifeq ($(TARGET_USES_CAR_FUTURE_FEATURES),true)
car_lib_sources += $(call all-java-files-under, src_feature_future)
else
car_lib_sources += $(call all-java-files-under, src_feature_current)
endif
car_lib_sources += $(call all-Iaidl-files-under, src)
LOCAL_AIDL_INCLUDES += system/bt/binder

LOCAL_SRC_FILES := $(car_lib_sources)

ifeq ($(EMMA_INSTRUMENT_FRAMEWORK),true)
LOCAL_EMMA_INSTRUMENT := true
endif

include $(BUILD_JAVA_LIBRARY)

ifeq ($(BOARD_IS_AUTOMOTIVE), true)
$(call dist-for-goals,dist_files,$(LOCAL_BUILT_MODULE):$(LOCAL_MODULE).jar)
endif

# API Check
# ---------------------------------------------
car_module := $(LOCAL_MODULE)
car_module_src_files := $(LOCAL_SRC_FILES)
car_module_api_dir := $(LOCAL_PATH)/api
car_module_java_libraries := framework
car_module_include_systemapi := true
car_module_java_packages := android.car*
include $(CAR_API_CHECK)

include $(CLEAR_VARS)

LOCAL_MODULE := android.car7
LOCAL_SRC_FILES := $(car_lib_sources)
LOCAL_JAVA_LANGUAGE_VERSION := 1.7
LOCAL_AIDL_INCLUDES += system/bt/binder

ifeq ($(EMMA_INSTRUMENT_FRAMEWORK),true)
LOCAL_EMMA_INSTRUMENT := true
endif

include $(BUILD_JAVA_LIBRARY)
$(call dist-for-goals,dist_files,$(full_classes_jar):$(LOCAL_MODULE).jar)

endif #TARGET_BUILD_PDK
