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

#
# Include this file to utilize the car-stream-ui-lib's resources and files.
#
# Make sure to include it after you've set all your desired LOCAL variables.
# Note that you must explicitly set your LOCAL_RESOURCE_DIR before including this file.
#
# For example:
#
#   LOCAL_RESOURCE_DIR := \
#        $(LOCAL_PATH)/res
#
#   include packages/apps/Car/libs/car-apps-common/car-apps-common
#

# Check that LOCAL_RESOURCE_DIR is defined
ifeq (,$(LOCAL_RESOURCE_DIR))
$(error LOCAL_RESOURCE_DIR must be defined)
endif

# Include android-support-v4, if not already included
ifeq (,$(findstring android-support-v4,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4
endif

# Include android-support-annotations, if not already included
ifeq (,$(findstring android-support-annotations,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_STATIC_JAVA_LIBRARIES += android-support-annotations
endif

# Include car-apps-common
ifeq (,$(findstring com.android.car.apps.common, $(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_RESOURCE_DIR += \
    packages/apps/Car/libs/car-apps-common/res
LOCAL_AAPT_FLAGS += --extra-packages com.android.car.apps.common
LOCAL_STATIC_JAVA_LIBRARIES += car-apps-common
endif
