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

# Rules to generate a tests zip file that included test modules
# based on the configuration for continuous metric testing.

LOCAL_PATH := $(call my-dir)
include $(LOCAL_PATH)/tests/native_metric_test_list.mk
-include $(wildcard vendor/*/build/tasks/tests/native_metric_test_list.mk)

my_modules := \
    $(native_metric_tests)

my_package_name := continuous_native_metric_tests

include $(BUILD_SYSTEM)/tasks/tools/package-modules.mk

.PHONY: continuous_native_metric_tests
continuous_native_metric_tests : $(my_package_zip)

name := $(TARGET_PRODUCT)-continuous_native_metric_tests-$(FILE_NAME_TAG)
$(call dist-for-goals, continuous_native_metric_tests, $(my_package_zip):$(name).zip)

# Also build this when you run "make tests".
tests: continuous_native_metric_tests
