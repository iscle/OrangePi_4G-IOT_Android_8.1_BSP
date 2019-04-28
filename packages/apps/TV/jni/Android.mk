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
#

LOCAL_PATH := $(call my-dir)

# --------------------------------------------------------------
include $(CLEAR_VARS)

LOCAL_MODULE := libtunertvinput_jni
LOCAL_SRC_FILES += tunertvinput_jni.cpp DvbManager.cpp
LOCAL_SDK_VERSION := 23
LOCAL_NDK_STL_VARIANT := stlport_static
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
include $(call all-makefiles-under,$(LOCAL_PATH))
