# Copyright (C) 2015 The Android Open Source Project
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
#

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

CAR_CURRENT_SDK_VERSION := 23
CAR_API_CHECK := $(LOCAL_PATH)/apicheck.mk
api_check_current_msg_file := $(LOCAL_PATH)/apicheck_msg_current.txt
api_check_last_msg_file := $(LOCAL_PATH)/apicheck_msg_last.txt

.PHONY: update-car-api

# Include the sub-makefiles
include $(call all-makefiles-under,$(LOCAL_PATH))

# Clear out variables
CAR_CURRENT_SDK_VERSION :=
CAR_API_CHECK :=
api_check_current_msg_file :=
api_check_last_msg_file :=
