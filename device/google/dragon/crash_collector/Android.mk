#
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

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    crash_dispatcher.cc \

LOCAL_CPP_EXTENSION := cc

LOCAL_CPPFLAGS := \
    -W \
    -Wall \
    -Wextra \
    -Wunused \
    -Werror \
    -Wno-unused-parameter \

LOCAL_SHARED_LIBRARIES := \
    libbase \
    liblog \

LOCAL_MODULE := crash_dispatcher

include $(BUILD_EXECUTABLE)


include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    crash_collector.cc \
    coredump_writer.cc \

LOCAL_CPP_EXTENSION := cc

LOCAL_CPPFLAGS := \
    -W \
    -Wall \
    -Wextra \
    -Wunused \
    -Werror \
    -Wno-unused-parameter \

LOCAL_SHARED_LIBRARIES := \
    libbase \
    libcutils \
    liblog \
    libutils \

LOCAL_STATIC_LIBRARIES := \
    breakpad_client \

LOCAL_MODULE := crash_collector
LOCAL_MODULE_STEM_32 := crash_collector32
LOCAL_MODULE_STEM_64 := crash_collector64
LOCAL_MULTILIB := both

include $(BUILD_EXECUTABLE)


include $(CLEAR_VARS)

LOCAL_MODULE := crash-report-provider
LOCAL_SRC_FILES := $(call all-java-files-under, src)

include $(BUILD_STATIC_JAVA_LIBRARY)


include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_STATIC_JAVA_LIBRARIES := crash-report-provider
LOCAL_PACKAGE_NAME := CrashReportProvider
LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)
