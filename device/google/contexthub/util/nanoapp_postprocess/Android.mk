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

# libelf is not available in the Mac build as of June 2016, but we currently
# only need to use this tool on Linux, so exclude this from non-Linux builds
ifeq ($(HOST_OS),linux)

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    postprocess_elf.c \

LOCAL_CFLAGS := -Wall -Werror -Wextra

LOCAL_STATIC_LIBRARIES := libnanohub_common

LOCAL_MODULE := nanoapp_postprocess

# libelf needed for ELF parsing support, libz required by libelf
LOCAL_STATIC_LIBRARIES += libelf libz

# Statically linking libc++ so this binary can be copied out of the tree and
# still work (needed by dependencies)
LOCAL_CXX_STL := libc++_static

LOCAL_MODULE_TAGS := optional

include $(BUILD_HOST_EXECUTABLE)

endif # linux
