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

SPIRIT_SRCS := \
	builder.cpp\
	entity.cpp\
	instructions.cpp\
	module.cpp\
	pass.cpp\
	pass_queue.cpp\
	transformer.cpp\
	visitor.cpp\
	word_stream.cpp\
	word_stream_impl.cpp

LIBNAME = libspirit

NDK_PREBUILTS := $(TOP)/prebuilts/ndk/r13
SPIRV_TOOLS_PATH := $(NDK_PREBUILTS)/sources/third_party/shaderc/third_party/spirv-tools
SPIRV_HEADERS_PATH := $(SPIRV_TOOLS_PATH)/external/spirv-headers
SPIRV_CORE_GRAMMAR := $(SPIRV_HEADERS_PATH)/include/spirv/1.1/spirv.core.grammar.json
GENERATOR := $(LOCAL_PATH)/generate.py

# Don't build for unbundled branches
ifeq (,$(TARGET_BUILD_APPS))

#=====================================================================
# Host shared library libspirit.so
#=====================================================================

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(SPIRIT_SRCS)

LOCAL_MODULE := $(LIBNAME)
LOCAL_MULTILIB := first
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_IS_HOST_MODULE := true

PATH_TO_GENERATED := $(local-generated-sources-dir)
GEN := \
	instructions_generated.h\
	types_generated.h\
	opcodes_generated.h\
	instruction_dispatches_generated.h\
	enum_dispatches_generated.h\
	type_inst_dispatches_generated.h\
	const_inst_dispatches_generated.h\
	factory_methods_generated.h

FULL_GEN := $(addprefix $(PATH_TO_GENERATED)/,$(GEN))
$(FULL_GEN): $(SPIRV_CORE_GRAMMAR) $(GENERATOR)
	$(GENERATOR) $< --$(notdir $(@:%_generated.h=%)) $@

LOCAL_GENERATED_SOURCES := $(FULL_GEN)

LOCAL_C_INCLUDES += $(PATH_TO_GENERATED)

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH) $(PATH_TO_GENERATED)

include $(BUILD_HOST_SHARED_LIBRARY)

#=====================================================================
# Tests for host module word_stream
#=====================================================================

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
  word_stream.cpp \
  word_stream_impl.cpp \
  word_stream_test.cpp

LOCAL_STATIC_LIBRARIES := libgtest_host

LOCAL_MODULE := word_stream_test
LOCAL_MULTILIB := first
LOCAL_MODULE_TAGS := tests
LOCAL_MODULE_CLASS := NATIVE_TESTS
LOCAL_IS_HOST_MODULE := true

PATH_TO_GENERATED := $(local-generated-sources-dir)
GEN := \
	enum_dispatches_generated.h\
	types_generated.h

FULL_GEN := $(addprefix $(PATH_TO_GENERATED)/,$(GEN))
$(FULL_GEN): $(SPIRV_CORE_GRAMMAR) $(GENERATOR)
	$(GENERATOR) $< --$(notdir $(@:%_generated.h=%)) $@

LOCAL_GENERATED_SOURCES := $(FULL_GEN)

LOCAL_C_INCLUDES += $(PATH_TO_GENERATED)

include $(BUILD_HOST_NATIVE_TEST)

#=====================================================================
# Tests for host module instructions
#=====================================================================

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
  entity.cpp \
  instructions.cpp \
  instructions_test.cpp \
  visitor.cpp \
  word_stream.cpp \
  word_stream_impl.cpp

LOCAL_STATIC_LIBRARIES := libgtest_host

LOCAL_MODULE := instructions_test
LOCAL_MULTILIB := first
LOCAL_MODULE_TAGS := tests
LOCAL_MODULE_CLASS := NATIVE_TESTS
LOCAL_IS_HOST_MODULE := true

PATH_TO_GENERATED := $(local-generated-sources-dir)
GEN := \
	enum_dispatches_generated.h\
	instruction_dispatches_generated.h\
	instructions_generated.h\
	types_generated.h\
	opcodes_generated.h

FULL_GEN := $(addprefix $(PATH_TO_GENERATED)/,$(GEN))
$(FULL_GEN): $(SPIRV_CORE_GRAMMAR) $(GENERATOR)
	$(GENERATOR) $< --$(notdir $(@:%_generated.h=%)) $@

LOCAL_GENERATED_SOURCES := $(FULL_GEN)

LOCAL_C_INCLUDES += $(PATH_TO_GENERATED)

include $(BUILD_HOST_NATIVE_TEST)

#=====================================================================
# Tests for host module pass queue
#=====================================================================

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
  pass.cpp \
  pass_queue.cpp \
  pass_queue_test.cpp \

LOCAL_STATIC_LIBRARIES := libgtest_host

LOCAL_SHARED_LIBRARIES := $(LIBNAME)

LOCAL_MODULE := pass_queue_test
LOCAL_MULTILIB := first
LOCAL_MODULE_TAGS := tests
LOCAL_MODULE_CLASS := NATIVE_TESTS
LOCAL_IS_HOST_MODULE := true

include $(BUILD_HOST_NATIVE_TEST)

#=====================================================================
# Tests for host shared library
#=====================================================================

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
  builder_test.cpp \
  module_test.cpp \
  transformer_test.cpp \

LOCAL_STATIC_LIBRARIES := libgtest_host

LOCAL_SHARED_LIBRARIES := $(LIBNAME)

LOCAL_MODULE := $(LIBNAME)_test
LOCAL_MULTILIB := first
LOCAL_MODULE_TAGS := tests
LOCAL_MODULE_CLASS := NATIVE_TESTS
LOCAL_IS_HOST_MODULE := true

include $(BUILD_HOST_NATIVE_TEST)

#=====================================================================
# Device shared library libspirit.so
#=====================================================================

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(SPIRIT_SRCS)

LOCAL_C_INCLUDES := $(SPIRIT_INCLUDES)

LOCAL_MODULE := $(LIBNAME)
LOCAL_MODULE_CLASS := SHARED_LIBRARIES

LOCAL_SHARED_LIBRARIES +=

PATH_TO_GENERATED := $(local-generated-sources-dir)

GEN := \
	instructions_generated.h\
	types_generated.h\
	opcodes_generated.h\
	instruction_dispatches_generated.h\
	enum_dispatches_generated.h\
	type_inst_dispatches_generated.h\
	const_inst_dispatches_generated.h\
	factory_methods_generated.h

FULL_GEN := $(addprefix $(PATH_TO_GENERATED)/,$(GEN))
$(FULL_GEN): $(SPIRV_CORE_GRAMMAR) $(GENERATOR)
	$(GENERATOR) $< --$(notdir $(@:%_generated.h=%)) $@

LOCAL_GENERATED_SOURCES := $(FULL_GEN)

LOCAL_C_INCLUDES += $(PATH_TO_GENERATED)

LOCAL_CFLAGS := -Wno-error=non-virtual-dtor

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH) $(PATH_TO_GENERATED)

include $(BUILD_SHARED_LIBRARY)

endif # Don't build in unbundled branches
