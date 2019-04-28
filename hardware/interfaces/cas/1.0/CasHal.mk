#
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


########################################################################
# Included by frameworks/base for MediaCas. Hidl HAL can't be linked as
# Java lib from frameworks because it has dependency on frameworks itself.
#

intermediates := $(TARGET_OUT_COMMON_GEN)/JAVA_LIBRARIES/android.hardware.cas-V1.0-java_intermediates

HIDL := $(HOST_OUT_EXECUTABLES)/hidl-gen$(HOST_EXECUTABLE_SUFFIX)
HIDL_PATH := system/libhidl/transport/base/1.0

#
# Build types.hal (DebugInfo)
#
GEN := $(intermediates)/android/hidl/base/V1_0/DebugInfo.java
$(GEN): $(HIDL)
$(GEN): PRIVATE_HIDL := $(HIDL)
$(GEN): PRIVATE_DEPS := $(HIDL_PATH)/types.hal
$(GEN): PRIVATE_OUTPUT_DIR := $(intermediates)
$(GEN): PRIVATE_CUSTOM_TOOL = \
        $(PRIVATE_HIDL) -o $(PRIVATE_OUTPUT_DIR) \
        -Ljava \
        -randroid.hidl:system/libhidl/transport \
        android.hidl.base@1.0::types.DebugInfo

$(GEN): $(HIDL_PATH)/types.hal
	$(transform-generated-source)
LOCAL_GENERATED_SOURCES += $(GEN)

#
# Build IBase.hal
#
GEN := $(intermediates)/android/hidl/base/V1_0/IBase.java
$(GEN): $(HIDL)
$(GEN): PRIVATE_HIDL := $(HIDL)
$(GEN): PRIVATE_DEPS := $(HIDL_PATH)/IBase.hal
$(GEN): PRIVATE_DEPS += $(HIDL_PATH)/types.hal
$(GEN): $(HIDL_PATH)/types.hal
$(GEN): PRIVATE_OUTPUT_DIR := $(intermediates)
$(GEN): PRIVATE_CUSTOM_TOOL = \
        $(PRIVATE_HIDL) -o $(PRIVATE_OUTPUT_DIR) \
        -Ljava \
        -randroid.hidl:system/libhidl/transport \
        android.hidl.base@1.0::IBase

$(GEN): $(HIDL_PATH)/IBase.hal
	$(transform-generated-source)
LOCAL_GENERATED_SOURCES += $(GEN)

HIDL_PATH := hardware/interfaces/cas/1.0

#
# Build types.hal (HidlCasPluginDescriptor)
#
GEN := $(intermediates)/android/hardware/cas/V1_0/HidlCasPluginDescriptor.java
$(GEN): $(HIDL)
$(GEN): PRIVATE_HIDL := $(HIDL)
$(GEN): PRIVATE_DEPS := $(HIDL_PATH)/types.hal
$(GEN): PRIVATE_OUTPUT_DIR := $(intermediates)
$(GEN): PRIVATE_CUSTOM_TOOL = \
        $(PRIVATE_HIDL) -o $(PRIVATE_OUTPUT_DIR) \
        -Ljava \
        -randroid.hardware:hardware/interfaces \
        -randroid.hidl:system/libhidl/transport \
        android.hardware.cas@1.0::types.HidlCasPluginDescriptor

$(GEN): $(HIDL_PATH)/types.hal
	$(transform-generated-source)
LOCAL_GENERATED_SOURCES += $(GEN)

#
# Build types.hal (Status)
#
GEN := $(intermediates)/android/hardware/cas/V1_0/Status.java
$(GEN): $(HIDL)
$(GEN): PRIVATE_HIDL := $(HIDL)
$(GEN): PRIVATE_DEPS := $(HIDL_PATH)/types.hal
$(GEN): PRIVATE_OUTPUT_DIR := $(intermediates)
$(GEN): PRIVATE_CUSTOM_TOOL = \
        $(PRIVATE_HIDL) -o $(PRIVATE_OUTPUT_DIR) \
        -Ljava \
        -randroid.hardware:hardware/interfaces \
        -randroid.hidl:system/libhidl/transport \
        android.hardware.cas@1.0::types.Status

$(GEN): $(HIDL_PATH)/types.hal
	$(transform-generated-source)
LOCAL_GENERATED_SOURCES += $(GEN)

#
# Build ICas.hal
#
GEN := $(intermediates)/android/hardware/cas/V1_0/ICas.java
$(GEN): $(HIDL)
$(GEN): PRIVATE_HIDL := $(HIDL)
$(GEN): PRIVATE_DEPS := $(HIDL_PATH)/ICas.hal
$(GEN): PRIVATE_DEPS += $(HIDL_PATH)/types.hal
$(GEN): $(HIDL_PATH)/types.hal
$(GEN): PRIVATE_OUTPUT_DIR := $(intermediates)
$(GEN): PRIVATE_CUSTOM_TOOL = \
        $(PRIVATE_HIDL) -o $(PRIVATE_OUTPUT_DIR) \
        -Ljava \
        -randroid.hardware:hardware/interfaces \
        -randroid.hidl:system/libhidl/transport \
        android.hardware.cas@1.0::ICas

$(GEN): $(HIDL_PATH)/ICas.hal
	$(transform-generated-source)
LOCAL_GENERATED_SOURCES += $(GEN)

#
# Build ICasListener.hal
#
GEN := $(intermediates)/android/hardware/cas/V1_0/ICasListener.java
$(GEN): $(HIDL)
$(GEN): PRIVATE_HIDL := $(HIDL)
$(GEN): PRIVATE_DEPS := $(HIDL_PATH)/ICasListener.hal
$(GEN): PRIVATE_OUTPUT_DIR := $(intermediates)
$(GEN): PRIVATE_CUSTOM_TOOL = \
        $(PRIVATE_HIDL) -o $(PRIVATE_OUTPUT_DIR) \
        -Ljava \
        -randroid.hardware:hardware/interfaces \
        -randroid.hidl:system/libhidl/transport \
        android.hardware.cas@1.0::ICasListener

$(GEN): $(HIDL_PATH)/ICasListener.hal
	$(transform-generated-source)
LOCAL_GENERATED_SOURCES += $(GEN)

#
# Build IDescramblerBase.hal
#
GEN := $(intermediates)/android/hardware/cas/V1_0/IDescramblerBase.java
$(GEN): $(HIDL)
$(GEN): PRIVATE_HIDL := $(HIDL)
$(GEN): PRIVATE_DEPS := $(HIDL_PATH)/IDescramblerBase.hal
$(GEN): PRIVATE_DEPS += $(HIDL_PATH)/types.hal
$(GEN): $(HIDL_PATH)/types.hal
$(GEN): PRIVATE_OUTPUT_DIR := $(intermediates)
$(GEN): PRIVATE_CUSTOM_TOOL = \
        $(PRIVATE_HIDL) -o $(PRIVATE_OUTPUT_DIR) \
        -Ljava \
        -randroid.hardware:hardware/interfaces \
        -randroid.hidl:system/libhidl/transport \
        android.hardware.cas@1.0::IDescramblerBase

$(GEN): $(HIDL_PATH)/IDescramblerBase.hal
	$(transform-generated-source)
LOCAL_GENERATED_SOURCES += $(GEN)

#
# Build IMediaCasService.hal
#
GEN := $(intermediates)/android/hardware/cas/V1_0/IMediaCasService.java
$(GEN): $(HIDL)
$(GEN): PRIVATE_HIDL := $(HIDL)
$(GEN): PRIVATE_DEPS := $(HIDL_PATH)/IMediaCasService.hal
$(GEN): PRIVATE_DEPS += $(HIDL_PATH)/ICas.hal
$(GEN): $(HIDL_PATH)/ICas.hal
$(GEN): PRIVATE_DEPS += $(HIDL_PATH)/ICasListener.hal
$(GEN): $(HIDL_PATH)/ICasListener.hal
$(GEN): PRIVATE_DEPS += $(HIDL_PATH)/IDescramblerBase.hal
$(GEN): $(HIDL_PATH)/IDescramblerBase.hal
$(GEN): PRIVATE_DEPS += $(HIDL_PATH)/types.hal
$(GEN): $(HIDL_PATH)/types.hal
$(GEN): PRIVATE_OUTPUT_DIR := $(intermediates)
$(GEN): PRIVATE_CUSTOM_TOOL = \
        $(PRIVATE_HIDL) -o $(PRIVATE_OUTPUT_DIR) \
        -Ljava \
        -randroid.hardware:hardware/interfaces \
        -randroid.hidl:system/libhidl/transport \
        android.hardware.cas@1.0::IMediaCasService

$(GEN): $(HIDL_PATH)/IMediaCasService.hal
	$(transform-generated-source)
LOCAL_GENERATED_SOURCES += $(GEN)

