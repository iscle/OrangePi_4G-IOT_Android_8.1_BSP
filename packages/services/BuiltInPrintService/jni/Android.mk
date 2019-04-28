# Copyright (C) 2016 The Android Open Source Project
# Copyright (C) 2016 Mopria Alliance, Inc.
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

INCLUDE_DIR    := include
LIB_DIR        := lib
PLUGINS_DIR    := plugins
IPP_HELPER_DIR := ipphelper

LOCAL_SDK_VERSION := current

LOCAL_CFLAGS += \
      -DINCLUDE_PDF=1 -Werror -Wextra -Wno-unused-parameter \
      -Wno-sign-compare -Wno-missing-field-initializers \
      -Wno-implicit-function-declaration -Wno-format -Wno-missing-braces

PLUGINS_SRCS := \
      $(PLUGINS_DIR)/lib_pclm.c $(PLUGINS_DIR)/lib_pwg.c \
      $(PLUGINS_DIR)/genPCLm/src/genPCLm.cpp \
      $(PLUGINS_DIR)/genPCLm/src/genJPEGStrips.cpp \
      $(PLUGINS_DIR)/pdf_render.c $(PLUGINS_DIR)/plugin_pcl.c \
      $(PLUGINS_DIR)/plugin_pdf.c $(PLUGINS_DIR)/pclm_wrapper_api.cpp \
      $(PLUGINS_DIR)/wprint_image.c $(PLUGINS_DIR)/wprint_image_platform.c \
      $(PLUGINS_DIR)/wprint_mupdf.c $(PLUGINS_DIR)/wprint_scaler.c

LIB_SRCS := \
      $(LIB_DIR)/lib_wprint.c $(LIB_DIR)/plugin_db.c \
      $(LIB_DIR)/printable_area.c $(LIB_DIR)/printer.c \
      $(LIB_DIR)/wprint_msgq.c $(LIB_DIR)/wprintJNI.c

IPP_HELPER_SRCS := \
      $(IPP_HELPER_DIR)/ipp_print.c $(IPP_HELPER_DIR)/ipphelper.c \
      $(IPP_HELPER_DIR)/ippstatus_capabilities.c \
      $(IPP_HELPER_DIR)/ippstatus_monitor.c

LOCAL_SRC_FILES:= \
      $(LIB_SRCS) $(IPP_HELPER_SRCS) $(PLUGINS_SRCS)

LOCAL_C_INCLUDES += \
      $(LOCAL_PATH)/$(INCLUDE_DIR) $(LOCAL_PATH)/$(PLUGINS_DIR)/genPCLm/inc \
      $(LOCAL_PATH)/$(IPP_HELPER_DIR)
LOCAL_STATIC_LIBRARIES := libjpeg_static_ndk
LOCAL_SHARED_LIBRARIES := libcups liblog libz
LOCAL_MODULE := libwfds
LOCAL_MODULE_TAGS := optional
include $(BUILD_SHARED_LIBRARY)
