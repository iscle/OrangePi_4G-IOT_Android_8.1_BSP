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
################################################################################
#
# NanoApp C/C++ Makefile Utils
#
################################################################################

SRCS += $(NANOHUB_DIR)/app/chre/common/chre_app.c
SRCS += $(NANOHUB_DIR)/app/chre/common/chre_app_syscalls.c
CFLAGS += -I$(NANOHUB_DIR)/../inc

include $(NANOHUB_DIR)/firmware_conf.mk

CFLAGS += $(COMMON_FLAGS)

BIN_POSTPROCESS_ARGS := -f 0x10

include $(NANOHUB_DIR)/app/app.mk
