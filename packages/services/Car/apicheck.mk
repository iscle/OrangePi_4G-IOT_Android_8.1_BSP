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
# Input variables
#
# $(car_module) - name of the car library module
# $(car_module_api_dir) - dir to store API files
# $(car_module_include_systemapi) - if systemApi file should be generated
# $(car_module_proguard_file) - where to output the proguard file, if empty no file will be produced
# $(car_module_java_libraries) - dependent libraries
# $(car_module_java_packages) - list of package names containing public classes
# $(car_module_src_files) - list of source files
# $(api_check_current_msg_file) - file containing error message for current API check
# $(api_check_last_msg_file) - file containing error message for last SDK API check
# ---------------------------------------------

ifeq ($(BOARD_IS_AUTOMOTIVE), true)
ifneq ($(TARGET_BUILD_PDK), true)
#
# Generate the public stub source files
# ---------------------------------------------
include $(CLEAR_VARS)

car_module_api_file := \
    $(TARGET_OUT_COMMON_INTERMEDIATES)/PACKAGING/$(car_module)_api.txt
car_module_removed_file := \
    $(TARGET_OUT_COMMON_INTERMEDIATES)/PACKAGING/$(car_module)_removed.txt

LOCAL_MODULE := $(car_module)-stubs
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(car_module_src_files)
LOCAL_JAVA_LIBRARIES := $(car_module_java_libraries) $(car_module)
LOCAL_ADDITIONAL_JAVA_DIR := \
    $(call intermediates-dir-for,$(LOCAL_MODULE_CLASS),$(car_module),,COMMON)/src
LOCAL_SDK_VERSION := $(CAR_CURRENT_SDK_VERSION)

LOCAL_DROIDDOC_STUB_OUT_DIR := $(TARGET_OUT_COMMON_INTERMEDIATES)/$(LOCAL_MODULE_CLASS)/$(LOCAL_MODULE)_intermediates/src

LOCAL_DROIDDOC_OPTIONS:= \
    -stubpackages "$(subst $(space),:,$(car_module_java_packages))" \
    -api $(car_module_api_file) \
    -removedApi $(car_module_removed_file) \
    -nodocs \
    -hide 113 \
    -hide 110
LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR := external/doclava/res/assets/templates-sdk
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_DROIDDOC)
car_stub_stamp := $(full_target)
$(car_module_api_file) : $(full_target)

ifeq ($(car_module_include_systemapi), true)
#
# Generate the system stub source files
# ---------------------------------------------
include $(CLEAR_VARS)

car_module_system_api_file := \
    $(TARGET_OUT_COMMON_INTERMEDIATES)/PACKAGING/$(car_module)_system_api.txt
car_module_system_removed_file := \
    $(TARGET_OUT_COMMON_INTERMEDIATES)/PACKAGING/$(car_module)_system_removed.txt

LOCAL_MODULE := $(car_module)-system-stubs
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(car_module_src_files)
LOCAL_JAVA_LIBRARIES := $(car_module_java_libraries) $(car_module)
LOCAL_ADDITIONAL_JAVA_DIR := \
    $(call intermediates-dir-for,$(LOCAL_MODULE_CLASS),$(car_module),,COMMON)/src
LOCAL_SDK_VERSION := $(CAR_CURRENT_SDK_VERSION)

LOCAL_DROIDDOC_STUB_OUT_DIR := $(TARGET_OUT_COMMON_INTERMEDIATES)/$(LOCAL_MODULE_CLASS)/$(LOCAL_MODULE)_intermediates/src

LOCAL_DROIDDOC_OPTIONS:= \
    -stubpackages "$(subst $(space),:,$(car_module_java_packages))" \
    -showAnnotation android.annotation.SystemApi \
    -api $(car_module_system_api_file) \
    -removedApi $(car_module_system_removed_file) \
    -nodocs \
    -hide 113 \
    -hide 110
LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR := external/doclava/res/assets/templates-sdk
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_DROIDDOC)
car_system_stub_stamp := $(full_target)
$(car_module_system_api_file) : $(full_target)

#($(car_module_include_systemapi), true)
endif


ifneq ($(strip $(car_module_proguard_file)),)
#
# Generate a proguard files
# ---------------------------------------------
include $(CLEAR_VARS)

LOCAL_MODULE := $(car_module)-proguard
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(car_module_src_files)
LOCAL_JAVA_LIBRARIES := $(car_module_java_libraries) $(car_module)
LOCAL_ADDITIONAL_JAVA_DIR := \
    $(call intermediates-dir-for,$(LOCAL_MODULE_CLASS),$(car_module),,COMMON)/src
LOCAL_SDK_VERSION := $(CAR_CURRENT_SDK_VERSION)

docs_proguard_intermediates_output := $(call intermediates-dir-for,$(LOCAL_MODULE_CLASS),$(LOCAL_MODULE),,COMMON)/keep_list.proguard
LOCAL_DROIDDOC_OPTIONS := -proguard $(docs_proguard_intermediates_output)

include $(BUILD_DROIDDOC)

update-car-api: PRIVATE_PROGUARD_INTERMEDIATES_OUTPUT := $(docs_proguard_intermediates_output)
update-car-api: PRIVATE_PROGUARD_OUTPUT_FILE := $(car_module_proguard_file)
update-car-api: PRIVATE_CAR_MODULE := $(car_module)
update-car-api: $(car_module)-proguard-docs | $(ACP)
	@echo $(PRIVATE_CAR_MODULE) copying $(PRIVATE_PROGUARD_INTERMEDIATES_OUTPUT) to $(PRIVATE_PROGUARD_OUTPUT_FILE)
	$(hide) $(ACP) $(PRIVATE_PROGUARD_INTERMEDIATES_OUTPUT) $(PRIVATE_PROGUARD_OUTPUT_FILE)

# cleanup vars
docs_proguard_output :=
# ($(strip $(car_module_proguard_file)),)
endif


#
# Check public API
# ---------------------------------------------
.PHONY: $(car_module)-check-public-api
checkapi: $(car_module)-check-public-api
$(car_module): $(car_module)-check-public-api

last_released_sdk_$(car_module) := $(lastword $(call numerically_sort, \
    $(filter-out current, \
        $(patsubst $(car_module_api_dir)/%.txt,%, $(wildcard $(car_module_api_dir)/*.txt)) \
    )))

# Check that the API we're building hasn't broken the last-released SDK version
# if it exists
ifneq ($(last_released_sdk_$(car_module)),)
$(eval $(call check-api, \
    $(car_module)-checkapi-last, \
    $(car_module_api_dir)/$(last_released_sdk_$(car_module)).txt, \
    $(car_module_api_file), \
    $(car_module_api_dir)/removed.txt, \
    $(car_module_removed_file), \
    -hide 2 -hide 3 -hide 4 -hide 5 -hide 6 -hide 24 -hide 25 -hide 26 -hide 27 \
        -warning 7 -warning 8 -warning 9 -warning 10 -warning 11 -warning 12 \
        -warning 13 -warning 14 -warning 15 -warning 16 -warning 17 -warning 18 -hide 113, \
    cat $(api_check_last_msg_file), \
    $(car_module)-check-public-api, \
    $(car_stub_stamp)))
endif

# Check that the API we're building hasn't changed from the not-yet-released
# SDK version.
$(eval $(call check-api, \
    $(car_module)-checkapi-current, \
    $(car_module_api_dir)/current.txt, \
    $(car_module_api_file), \
    $(car_module_api_dir)/removed.txt, \
    $(car_module_removed_file), \
    -error 2 -error 3 -error 4 -error 5 -error 6 -error 7 -error 8 -error 9 -error 10 -error 11 \
        -error 12 -error 13 -error 14 -error 15 -error 16 -error 17 -error 18 -error 19 -error 20 \
        -error 21 -error 23 -error 24 -error 25 -hide 113, \
    cat $(api_check_current_msg_file), \
    $(car_module)-check-public-api, \
    $(car_stub_stamp)))

.PHONY: update-$(car_module)-api
update-$(car_module)-api: PRIVATE_API_DIR := $(car_module_api_dir)
update-$(car_module)-api: PRIVATE_MODULE := $(car_module)
update-$(car_module)-api: PRIVATE_REMOVED_API_FILE := $(car_module_removed_file)
update-$(car_module)-api: PRIVATE_PROGUARD_FILE := $(car_module_proguard_file)
update-$(car_module)-api: $(car_module_api_file) | $(ACP)
	@echo Copying $(PRIVATE_MODULE) current.txt
	$(hide) $(ACP) $< $(PRIVATE_API_DIR)/current.txt
	@echo Copying $(PRIVATE_MODULE) removed.txt
	$(hide) $(ACP) $(PRIVATE_REMOVED_API_FILE) $(PRIVATE_API_DIR)/removed.txt

# Run this update API task on the update-car-api task
update-car-api: update-$(car_module)-api

ifeq ($(car_module_include_systemapi), true)

#
# Check system API
# ---------------------------------------------
.PHONY: $(car_module)-check-system-api
checkapi: $(car_module)-check-system-api
$(car_module): $(car_module)-check-system-api

last_released_system_sdk_$(car_module) := $(lastword $(call numerically_sort, \
    $(filter-out system-current, \
        $(patsubst $(car_module_api_dir)/%.txt,%, $(wildcard $(car_module_api_dir)/system-*.txt)) \
    )))

# Check that the API we're building hasn't broken the last-released SDK version
# if it exists
ifneq ($(last_released_system_sdk_$(car_module)),)
$(eval $(call check-api, \
    $(car_module)-checksystemapi-last, \
    $(car_module_api_dir)/$(last_released_system_sdk_$(car_module)).txt, \
    $(car_module_system_api_file), \
    $(car_module_api_dir)/system-removed.txt, \
    $(car_module_system_removed_file), \
    -hide 2 -hide 3 -hide 4 -hide 5 -hide 6 -hide 24 -hide 25 -hide 26 -hide 27 \
        -warning 7 -warning 8 -warning 9 -warning 10 -warning 11 -warning 12 \
        -warning 13 -warning 14 -warning 15 -warning 16 -warning 17 -warning 18 -hide 113, \
    cat $(api_check_last_msg_file), \
    $(car_module)-check-system-api, \
    $(car_system_stub_stamp)))
endif

# Check that the API we're building hasn't changed from the not-yet-released
# SDK version.
$(eval $(call check-api, \
    $(car_module)-checksystemapi-current, \
    $(car_module_api_dir)/system-current.txt, \
    $(car_module_system_api_file), \
    $(car_module_api_dir)/system-removed.txt, \
    $(car_module_system_removed_file), \
    -error 2 -error 3 -error 4 -error 5 -error 6 -error 7 -error 8 -error 9 -error 10 -error 11 \
        -error 12 -error 13 -error 14 -error 15 -error 16 -error 17 -error 18 -error 19 -error 20 \
        -error 21 -error 23 -error 24 -error 25 -hide 113, \
    cat $(api_check_current_msg_file), \
    $(car_module)-check-system-api, \
    $(car_stub_stamp)))

.PHONY: update-$(car_module)-system-api
update-$(car_module)-system-api: PRIVATE_API_DIR := $(car_module_api_dir)
update-$(car_module)-system-api: PRIVATE_MODULE := $(car_module)
update-$(car_module)-system-api: PRIVATE_REMOVED_API_FILE := $(car_module_system_removed_file)
update-$(car_module)-system-api: PRIVATE_PROGUARD_FILE := $(car_module_proguard_file)
update-$(car_module)-system-api: $(car_module_system_api_file) | $(ACP)
	@echo Copying $(PRIVATE_MODULE) system-current.txt
	$(hide) $(ACP) $< $(PRIVATE_API_DIR)/system-current.txt
	@echo Copying $(PRIVATE_MODULE) system-removed.txt
	$(hide) $(ACP) $(PRIVATE_REMOVED_API_FILE) $(PRIVATE_API_DIR)/system-removed.txt

# Run this update API task on the update-car-api task
update-car-api: update-$(car_module)-system-api



#($(car_module_include_systemapi), true)
endif

#($(TARGET_BUILD_PDK),true)
endif

#($(BOARD_IS_AUTOMOTIVE), true)
endif
#
# Clear variables
# ---------------------------------------------
car_module :=
car_module_api_dir :=
car_module_src_files :=
car_module_java_libraries :=
car_module_java_packages :=
car_module_api_file :=
car_module_removed_file :=
car_module_system_api_file :=
car_module_system_removed__file :=
car_stub_stamp :=
car_system_stub_stamp :=
car_module_include_systemapi :=
car_module_proguard_file :=
