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

# Build the Car service.

#disble build in PDK, missing aidl import breaks build
ifneq ($(TARGET_BUILD_PDK),true)

LOCAL_PATH:= $(call my-dir)

car_service_sources := $(call all-java-files-under, src)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(car_service_sources)

LOCAL_PACKAGE_NAME := CarService

# Each update should be signed by OEMs
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_JAVA_LIBRARIES += android.car
LOCAL_STATIC_JAVA_LIBRARIES += \
        android.hidl.base-V1.0-java \
        android.hardware.automotive.vehicle-V2.0-java \
        vehicle-hal-support-lib \
        car-systemtest \

include $(BUILD_PACKAGE)

#####################################################################################
# Build a static library to help mocking various car services in testing
#####################################################################################
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(car_service_sources)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_MODULE := car-service-lib-for-test

LOCAL_JAVA_LIBRARIES += android.car
LOCAL_STATIC_JAVA_LIBRARIES += \
        android.hidl.base-V1.0-java \
        android.hardware.automotive.vehicle-V2.0-java \
        vehicle-hal-support-lib \
        car-systemtest \

include $(BUILD_STATIC_JAVA_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))

endif #TARGET_BUILD_PDK
