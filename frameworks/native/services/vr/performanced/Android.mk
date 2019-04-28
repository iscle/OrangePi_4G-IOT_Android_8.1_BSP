# Copyright (C) 2016 The Android Open Source Project
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

sourceFiles := \
	cpu_set.cpp \
	main.cpp \
	performance_service.cpp \
	task.cpp

staticLibraries := \
	libperformance \
	libpdx_default_transport \
	libvr_manager

sharedLibraries := \
	libbinder \
	libbase \
	libcutils \
	liblog \
	libutils

include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(sourceFiles)
LOCAL_CFLAGS := -DLOG_TAG=\"performanced\"
LOCAL_CFLAGS += -DTRACE=0
LOCAL_STATIC_LIBRARIES := $(staticLibraries)
LOCAL_SHARED_LIBRARIES := $(sharedLibraries)
LOCAL_MODULE := performanced
LOCAL_INIT_RC := performanced.rc
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := performance_service_tests.cpp
LOCAL_STATIC_LIBRARIES := $(staticLibraries) libgtest_main
LOCAL_SHARED_LIBRARIES := $(sharedLibraries)
LOCAL_MODULE := performance_service_tests
LOCAL_MODULE_TAGS := optional
include $(BUILD_NATIVE_TEST)
