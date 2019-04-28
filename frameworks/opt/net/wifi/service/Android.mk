# Copyright (C) 2011 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

ifneq ($(TARGET_BUILD_PDK), true)

# Make the JNI part
# ============================================================
include $(CLEAR_VARS)

LOCAL_CFLAGS += -Wall -Werror -Wextra -Wno-unused-parameter -Wno-unused-function \
                -Wunused-variable -Winit-self -Wwrite-strings -Wshadow

LOCAL_C_INCLUDES += \
	$(JNI_H_INCLUDE) \

LOCAL_SHARED_LIBRARIES += \
	liblog \
	libnativehelper \
	libcutils \
	libutils \
	libdl

LOCAL_SRC_FILES := \
	jni/com_android_server_wifi_WifiNative.cpp \
	jni/jni_helper.cpp

LOCAL_MODULE := libwifi-service

include $(BUILD_SHARED_LIBRARY)

# Build the java code
# ============================================================

wificond_aidl_path := system/connectivity/wificond/aidl
wificond_aidl_rel_path := ../../../../../$(wificond_aidl_path)

include $(CLEAR_VARS)

LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/java $(wificond_aidl_path)
LOCAL_SRC_FILES := $(call all-java-files-under, java) \
	$(call all-Iaidl-files-under, java) \
	$(call all-Iaidl-files-under, $(wificond_aidl_rel_path)) \
	$(call all-logtags-files-under, java)

LOCAL_JAVA_LIBRARIES := \
	android.hidl.manager-V1.0-java \
	bouncycastle \
	conscrypt \
	jsr305 \
	services \
	mediatek-framework \
	mediatek-common

LOCAL_STATIC_JAVA_LIBRARIES := \
	android.hardware.wifi-V1.0-java \
	android.hardware.wifi-V1.1-java \
	android.hardware.wifi.supplicant-V1.0-java \
	vendor.mediatek.hardware.wifi.hostapd-V1.0-java \
	vendor.mediatek.hardware.wifi.supplicant-V1.1-java-static
LOCAL_REQUIRED_MODULES := services
LOCAL_MODULE_TAGS :=
LOCAL_MODULE := wifi-service
LOCAL_INIT_RC := wifi-events.rc

LOCAL_DEX_PREOPT_APP_IMAGE := false
LOCAL_DEX_PREOPT_GENERATE_PROFILE := true
LOCAL_DEX_PREOPT_PROFILE_CLASS_LISTING := frameworks/base/services/art-profile

ifeq ($(EMMA_INSTRUMENT_FRAMEWORK),true)
LOCAL_EMMA_INSTRUMENT := true
endif

LOCAL_JACK_COVERAGE_INCLUDE_FILTER := com.android.server.wifi.*

include $(BUILD_JAVA_LIBRARY)

endif  # !TARGET_BUILD_PDK
