#!/bin/sh

# Copyright 2015, 2016 The Android Open Source Project
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

source $ANDROID_BUILD_TOP/device/common/clear-factory-images-variables.sh
# HiKey unfortunately can't use the ./generate-factory-images-common.sh script
source $ANDROID_BUILD_TOP/device/linaro/hikey/factory-images/generate-factory-images-$TARGET_PRODUCT.sh
