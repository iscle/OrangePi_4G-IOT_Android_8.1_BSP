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

include $(NANO_BUILD)/common_config.mk

LOCAL_CFLAGS_cortexm4 +=                        \
    -fpic                                       \
    -mno-pic-data-is-text-relative              \
    -msingle-pic-base                           \
    -mpic-register=r9                           \

LOCAL_NANO_MODULE_TYPE := APP
LOCAL_OBJCOPY_SECT_cortexm4 := .flash .data .relocs .dynsym
LOCAL_FORCE_STATIC_EXECUTABLE := false

LOCAL_CFLAGS +=                                 \
    -DAPP_ID=$(LOCAL_NANO_APP_ID)               \
    -DAPP_VERSION=$(LOCAL_NANO_APP_VERSION)     \
    -D__NANOHUB__                               \

# Optimization/debug
LOCAL_CFLAGS += \
    -Os \
    -g \
