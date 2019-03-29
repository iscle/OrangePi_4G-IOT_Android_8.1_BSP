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

include $(CLEAR_VARS)

# JPEG stub#####################################################################

ifneq ($(TARGET_BUILD_PDK),true)

include $(CLEAR_VARS)
LOCAL_VENDOR_MODULE := true

jpeg_module_relative_path := hw
jpeg_cflags := -fno-short-enums -DQEMU_HARDWARE
jpeg_cflags += -Wno-unused-parameter
jpeg_clang_flags += -Wno-c++11-narrowing
jpeg_shared_libraries := \
    libcutils \
    libexif \
    libjpeg \
    liblog \

jpeg_c_includes := external/libjpeg-turbo \
                   external/libexif \
                   frameworks/native/include
jpeg_src := \
    Compressor.cpp \
    JpegStub.cpp \


# goldfish build ###############################################################

LOCAL_MODULE_RELATIVE_PATH := ${jpeg_module_relative_path}
LOCAL_CFLAGS += ${jpeg_cflags}
LOCAL_CLANG_CFLAGS += ${jpeg_clangflags}

LOCAL_SHARED_LIBRARIES := ${jpeg_shared_libraries}
LOCAL_C_INCLUDES += ${jpeg_c_includes}
LOCAL_SRC_FILES := ${jpeg_src}

LOCAL_MODULE := camera.goldfish.jpeg

include $(BUILD_SHARED_LIBRARY)

# ranchu build #################################################################

include ${CLEAR_VARS}

LOCAL_VENDOR_MODULE := true
LOCAL_MODULE := camera.ranchu.jpeg

LOCAL_MODULE_RELATIVE_PATH := ${jpeg_module_relative_path}
LOCAL_CFLAGS += ${jpeg_cflags}
LOCAL_CLANG_CFLAGS += ${jpeg_clangflags}

LOCAL_SHARED_LIBRARIES := ${jpeg_shared_libraries}
LOCAL_C_INCLUDES += ${jpeg_c_includes}
LOCAL_SRC_FILES := ${jpeg_src}

include $(BUILD_SHARED_LIBRARY)

endif # !PDK
