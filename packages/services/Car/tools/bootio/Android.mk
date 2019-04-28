#
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
#

LOCAL_PATH := $(call my-dir)

bootio_lib_src_files := \
        protos.proto \
        bootio_collector.cpp \

bootio_src_files := \
        bootio.cpp \

bootio_shared_libs := \
        libbase \
        libcutils \
        liblog \
        libprotobuf-cpp-lite \

bootio_cflags := \
        -Wextra \

define bootio_proto_include
$(call local-generated-sources-dir)/proto/$(LOCAL_PATH)
endef

# bootio static library
# -----------------------------------------------------------------------------

include $(CLEAR_VARS)

LOCAL_MODULE := libbootio
LOCAL_MODULE_CLASS := SHARED_LIBRARIES

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/.. \
    $(call bootio_proto_include) \

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)
LOCAL_CFLAGS := $(bootio_cflags)
LOCAL_SHARED_LIBRARIES := $(bootio_shared_libs)
LOCAL_PROTOC_OPTIMIZE_TYPE := lite
LOCAL_SRC_FILES := $(bootio_lib_src_files)
# Clang is required because of C++14
LOCAL_CLANG := true

include $(BUILD_SHARED_LIBRARY)


# bootio binary
# -----------------------------------------------------------------------------

include $(CLEAR_VARS)

LOCAL_MODULE := bootio
LOCAL_CFLAGS := $(bootio_cflags)
LOCAL_SHARED_LIBRARIES := \
    $(bootio_shared_libs) \
    libbootio \

LOCAL_INIT_RC := bootio.rc
LOCAL_SRC_FILES := $(bootio_src_files)
# Clang is required because of C++14
LOCAL_CLANG := true

include $(BUILD_EXECUTABLE)
