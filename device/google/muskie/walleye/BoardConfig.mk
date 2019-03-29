#
# Copyright (C) 2016 The Android Open-Source Project
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

TARGET_BOOTLOADER_BOARD_NAME := walleye
DEFAULT_LOW_PERSISTENCE_MODE_BRIGHTNESS := 0x00000056

include device/google/wahoo/BoardConfig.mk
-include vendor/google_devices/muskie/proprietary/BoardConfigVendor.mk

BOARD_BOOTIMAGE_PARTITION_SIZE := 33554432

#sepolicy common to muskie/walleye
BOARD_SEPOLICY_DIRS += device/google/muskie/sepolicy

# Testing related defines
BOARD_PERFSETUP_SCRIPT := platform_testing/scripts/perf-setup/wahoo-setup.sh

BOARD_LISA_TARGET_SCRIPTS := device/google/wahoo/lisa/
