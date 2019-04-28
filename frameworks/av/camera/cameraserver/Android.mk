# Copyright 2015 The Android Open Source Project
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

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	main_cameraserver.cpp

LOCAL_SHARED_LIBRARIES := \
	libcameraservice \
	liblog \
	libutils \
	libui \
	libgui \
	libbinder \
	libhidltransport \
	android.hardware.camera.common@1.0 \
	android.hardware.camera.provider@2.4 \
	android.hardware.camera.device@1.0 \
	android.hardware.camera.device@3.2

LOCAL_MODULE:= cameraserver
LOCAL_32_BIT_ONLY := true

LOCAL_CFLAGS += -Wall -Wextra -Werror -Wno-unused-parameter

LOCAL_INIT_RC := cameraserver.rc

include $(BUILD_EXECUTABLE)
