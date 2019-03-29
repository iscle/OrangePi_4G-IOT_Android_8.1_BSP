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
# test NanoApp Makefile
#
################################################################################


SRCS := main.c
BIN := chre_test0
APP_ID := 476f6f676c549000
APP_VERSION := 0

# Nanohub relative path
NANOHUB_DIR := ../../..

# Device configuration #########################################################

# select device variant for this app
# if there is no path of the form $(NANOHUB_DIR)/variant/$(VARIANT), the
# VARIANT_PATH variable must be set to ANDROID_TOP-relative valid path containing VARIANT subtree

TARGET_PRODUCT ?= nucleo
VARIANT := $(TARGET_PRODUCT)

include $(NANOHUB_DIR)/app/chre/chre.mk
