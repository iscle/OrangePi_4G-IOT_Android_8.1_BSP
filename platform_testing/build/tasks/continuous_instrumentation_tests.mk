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
# based on the configuration.

LOCAL_PATH := $(call my-dir)
include $(LOCAL_PATH)/tests/instrumentation_test_list.mk
-include $(wildcard vendor/*/build/tasks/tests/instrumentation_test_list.mk)

my_modules := \
    $(instrumentation_tests)

my_package_name := continuous_instrumentation_tests

include $(BUILD_SYSTEM)/tasks/tools/package-modules.mk

.PHONY: continuous_instrumentation_tests
continuous_instrumentation_tests : $(my_package_zip)

name := $(TARGET_PRODUCT)-continuous_instrumentation_tests-$(FILE_NAME_TAG)
$(call dist-for-goals, continuous_instrumentation_tests, $(my_package_zip):$(name).zip)

# Also build this when you run "make tests".
tests: continuous_instrumentation_tests

# Include test em files in emma metadata
ifeq ($(EMMA_INSTRUMENT_STATIC),true)
    $(EMMA_META_ZIP) : continuous_instrumentation_tests
endif

# Rules to generate an API-coverage report based on the above tests

# Coverage report output location
coverage_out := $(call intermediates-dir-for,PACKAGING,continuous_instrumentation_tests_coverage)
coverage_report := $(coverage_out)/api_coverage.html

# Framework API descriptions
api_text := frameworks/base/api/system-current.txt
api_xml := $(coverage_out)/api.xml
$(api_xml) : $(api_text) $(APICHECK)
	$(hide) echo "Converting API file to XML: $@"
	$(hide) mkdir -p $(dir $@)
	$(hide) $(APICHECK_COMMAND) -convert2xml $< $@

# CTS API coverage tool
api_coverage_exe := $(HOST_OUT_EXECUTABLES)/cts-api-coverage
dexdeps_exe := $(HOST_OUT_EXECUTABLES)/dexdeps

# APKs to measure for coverage
test_apks := $(call intermediates-dir-for,PACKAGING,continuous_instrumentation_tests)/DATA/app/*

# Rule to generate the coverage report
api_coverage_dep := continuous_instrumentation_tests $(api_coverage_exe) $(dexdeps_exe) $(api_xml)
$(coverage_report): PRIVATE_API_COVERAGE_EXE := $(api_coverage_exe)
$(coverage_report): PRIVATE_DEXDEPS_EXE := $(dexdeps_exe)
$(coverage_report): PRIVATE_API_XML := $(api_xml)
$(coverage_report): PRIVATE_REPORT_TITLE := "APCT API Coverage Report"
$(coverage_report): PRIVATE_TEST_APKS := $(test_apks)
$(coverage_report): $(api_coverage_dep) | $(ACP)
	$(hide) mkdir -p $(dir $@)
	$(hide) $(PRIVATE_API_COVERAGE_EXE) -d $(PRIVATE_DEXDEPS_EXE) \
		-a $(PRIVATE_API_XML) -t $(PRIVATE_REPORT_TITLE) -f html -o $@ $(PRIVATE_TEST_APKS)
	@ echo $(PRIVATE_REPORT_TITLE): file://$(ANDROID_BUILD_TOP)/$@

.PHONY: continuous_instrumentation_tests_api_coverage
continuous_instrumentation_tests_api_coverage : $(coverage_report)

# Include the coverage report in the dist folder
$(call dist-for-goals, continuous_instrumentation_tests_api_coverage, \
	$(coverage_report):$(name)-api_coverage.html)

# Also build this when you run "make tests".
# This allow us to not change the build server config.
tests : continuous_instrumentation_tests_api_coverage

# Reset temp vars
coverage_out :=
coverage_report :=
api_text :=
api_xml :=
api_coverage_exe :=
dexdeps_exe :=
test_apks :=
api_coverage_dep :=
