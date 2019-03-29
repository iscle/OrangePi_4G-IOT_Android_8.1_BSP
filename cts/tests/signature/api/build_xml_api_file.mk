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

# Specify the following 3 variables before including:
#
#     LOCAL_MODULE_STEM
#         the name of the file to generate, e.g. current.api
#
#     LOCAL_MODULE
#         the name of the module - must be unique
#
#     LOCAL_SRC_FILES
#         the name of the source api txt file - only one file allowed

LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_ETC)

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE) : ${LOCAL_SRC_FILES} | $(APICHECK)
	@echo "Convert API file $< -> $@"
	@mkdir -p $(dir $@)
	$(hide) $(APICHECK_COMMAND) -convert2xmlnostrip $< $@
