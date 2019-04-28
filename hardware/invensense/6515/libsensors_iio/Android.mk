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
# Modified 2011 by InvenSense, Inc

LOCAL_PATH := $(call my-dir)

# InvenSense fragment of the HAL
include $(CLEAR_VARS)

LOCAL_MODULE := libinvensense_hal
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_OWNER := invensense

LOCAL_CFLAGS := -DLOG_TAG=\"Sensors\" -Werror -Wall

# ANDROID version check
$(info YD>>PLATFORM_VERSION=$(PLATFORM_VERSION))
MAJOR_VERSION :=$(shell echo $(PLATFORM_VERSION) | cut -f1 -d.)
MINOR_VERSION :=$(shell echo $(PLATFORM_VERSION) | cut -f2 -d.)
VERSION_KK :=$(shell test $(MAJOR_VERSION) -eq 4 -a $(MINOR_VERSION) -gt 3 && echo true)
VERSION_L  :=$(shell test $(MAJOR_VERSION) -eq 5 -a $(MINOR_VERSION) -eq 0 && echo true)

#
# Invensense uses the OS version to determine whether to include batch support,
# but implemented it in a way that requires modifying the code each time we move
# to a newer OS version.  I will fix this problem in a subsequent change, but for now,
# hardcode to saying we're ANDROID_L so we can isolate this checkin to being
# only changes coming from Invensense.
#
# Setting ANDROID_L to true is perfectly safe even on ANDROID_M because the code
# just requires "ANDROID_L or newer"
#
VERSION_L :=true

$(info YD>>ANDRIOD VERSION=$(MAJOR_VERSION).$(MINOR_VERSION))
$(info YD>>VERSION_L=$(VERSION_L), VERSION_KK=$(VERSION_KK))
#ANDROID version check END

ifeq ($(VERSION_KK),true)
LOCAL_CFLAGS += -DANDROID_KITKAT
else
LOCAL_CFLAGS += -DANDROID_LOLLIPOP
endif

ifneq (,$(filter $(TARGET_BUILD_VARIANT),eng userdebug user))
ifneq ($(COMPILE_INVENSENSE_COMPASS_CAL),0)
LOCAL_CFLAGS += -DINVENSENSE_COMPASS_CAL
endif
ifeq ($(COMPILE_THIRD_PARTY_ACCEL),1)
LOCAL_CFLAGS += -DTHIRD_PARTY_ACCEL
endif
else # release builds, default
LOCAL_CFLAGS += -DINVENSENSE_COMPASS_CAL
endif

LOCAL_SRC_FILES += SensorBase.cpp
LOCAL_SRC_FILES += MPLSensor.cpp
LOCAL_SRC_FILES += MPLSupport.cpp
LOCAL_SRC_FILES += InputEventReader.cpp
LOCAL_SRC_FILES += PressureSensor.IIO.secondary.cpp

ifneq (,$(filter $(TARGET_BUILD_VARIANT),eng userdebug user))
ifeq ($(COMPILE_INVENSENSE_COMPASS_CAL),0)
LOCAL_SRC_FILES += AkmSensor.cpp
LOCAL_SRC_FILES += CompassSensor.AKM.cpp
else ifeq ($(COMPILE_INVENSENSE_SENSOR_ON_PRIMARY_BUS), 1)
LOCAL_SRC_FILES += CompassSensor.IIO.primary.cpp
LOCAL_CFLAGS += -DSENSOR_ON_PRIMARY_BUS
else
LOCAL_SRC_FILES += CompassSensor.IIO.9150.cpp
endif
else # release builds, default
LOCAL_SRC_FILES += CompassSensor.IIO.9150.cpp
endif # eng, userdebug & user builds

LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/mllite
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/mllite/linux
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/driver/include
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/driver/include/linux

LOCAL_SHARED_LIBRARIES := liblog
LOCAL_SHARED_LIBRARIES += libcutils
LOCAL_SHARED_LIBRARIES += libutils
LOCAL_SHARED_LIBRARIES += libdl
LOCAL_SHARED_LIBRARIES += libmllite

# Additions for SysPed
LOCAL_SHARED_LIBRARIES += libmplmpu
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/mpl
LOCAL_CPPFLAGS += -DLINUX=1

LOCAL_SHARED_LIBRARIES += libmllite
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/mllite
LOCAL_CPPFLAGS += -DLINUX=1

include $(BUILD_SHARED_LIBRARY)

# Build a temporary HAL that links the InvenSense .so
include $(CLEAR_VARS)
ifeq ($(filter eng, userdebug, user, $(TARGET_BUILD_VARIANT)),)
ifneq ($(filter manta full_grouper tilapia, $(TARGET_PRODUCT)),)
LOCAL_MODULE := sensors.full_grouper
LOCAL_MODULE_OWNER := invensense
else
ifneq ($(filter aosp_hammerhead, $(TARGET_PRODUCT)),)
LOCAL_MODULE := sensors.hammerhead
LOCAL_MODULE_OWNER := invensense
else
ifneq ($(filter aosp_flounder, $(TARGET_PRODUCT)),)
LOCAL_MODULE := sensors.flounder
LOCAL_MODULE_OWNER := invensense
endif
endif
ifneq ($(filter dory guppy, $(TARGET_DEVICE)),)
LOCAL_MODULE := sensors.invensense
LOCAL_MODULE_OWNER := invensense
endif
endif
else    # eng, user, & userdebug builds
LOCAL_MODULE := sensors.invensense
endif   # eng, user & userdebug builds
$(info YD>>LOCAL_MODULE=$(LOCAL_MODULE))

ifdef TARGET_2ND_ARCH
LOCAL_MODULE_RELATIVE_PATH := hw
else
LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw
endif

LOCAL_SHARED_LIBRARIES += libmplmpu
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/mllite
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/mllite/linux
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/mpl
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/driver/include
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/driver/include/linux

LOCAL_MODULE_TAGS := optional
LOCAL_CFLAGS := -DLOG_TAG=\"Sensors\" -Werror -Wall

ifeq ($(VERSION_KK),true)
LOCAL_CFLAGS += -DANDROID_KITKAT
else
LOCAL_CFLAGS += -DANDROID_LOLLIPOP
endif

ifneq (,$(filter $(TARGET_BUILD_VARIANT),eng userdebug user))
ifneq ($(COMPILE_INVENSENSE_COMPASS_CAL),0)
LOCAL_CFLAGS += -DINVENSENSE_COMPASS_CAL
endif
ifeq ($(COMPILE_THIRD_PARTY_ACCEL),1)
LOCAL_CFLAGS += -DTHIRD_PARTY_ACCEL
endif
ifeq ($(COMPILE_INVENSENSE_SENSOR_ON_PRIMARY_BUS), 1)
LOCAL_SRC_FILES += CompassSensor.IIO.primary.cpp
LOCAL_CFLAGS += -DSENSOR_ON_PRIMARY_BUS
else
LOCAL_SRC_FILES += CompassSensor.IIO.9150.cpp
endif
else # release builds, default
LOCAL_SRC_FILES += CompassSensor.IIO.9150.cpp
endif # eng, userdebug & user

ifeq (,$(filter $(TARGET_BUILD_VARIANT),eng userdebug user))
ifneq ($(filter manta grouper tilapia, $(TARGET_DEVICE)),)
# it's already defined in some other Makefile for production builds
#LOCAL_SRC_FILES := sensors_mpl.cpp
else
LOCAL_SRC_FILES := sensors_mpl.cpp
endif
else    # eng, userdebug & user builds
LOCAL_SRC_FILES := sensors_mpl.cpp
endif   # eng, userdebug & user builds

LOCAL_SHARED_LIBRARIES := libinvensense_hal
LOCAL_SHARED_LIBRARIES += libcutils
LOCAL_SHARED_LIBRARIES += libutils
LOCAL_SHARED_LIBRARIES += libdl
LOCAL_SHARED_LIBRARIES += liblog
LOCAL_SHARED_LIBRARIES += libmllite
LOCAL_SHARED_LIBRARIES += libhardware_legacy
$(info YD>>LOCAL_MODULE=$(LOCAL_MODULE), LOCAL_SRC_FILES=$(LOCAL_SRC_FILES), LOCAL_SHARED_LIBRARIES=$(LOCAL_SHARED_LIBRARIES))
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libmplmpu
LOCAL_SRC_FILES := libmplmpu.so
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_OWNER := invensense
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_PATH := $(TARGET_OUT)/lib
OVERRIDE_BUILT_MODULE_PATH := $(TARGET_OUT_INTERMEDIATE_LIBRARIES)
LOCAL_STRIP_MODULE := true
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := libmllite
LOCAL_SRC_FILES := libmllite.so
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_OWNER := invensense
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_PATH := $(TARGET_OUT)/lib
OVERRIDE_BUILT_MODULE_PATH := $(TARGET_OUT_INTERMEDIATE_LIBRARIES)
LOCAL_STRIP_MODULE := true
include $(BUILD_PREBUILT)

