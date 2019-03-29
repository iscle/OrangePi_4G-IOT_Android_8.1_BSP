#
# Copyright (C) 2017 The Android Open-Source Project
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

TARGET_BOOTLOADER_BOARD_NAME := taimen
DEFAULT_LOW_PERSISTENCE_MODE_BRIGHTNESS := 0x0000008c

BOARD_KERNEL_CMDLINE += console=ttyMSM0,115200,n8 earlycon=msm_serial_dm,0xc1b0000

include device/google/wahoo/BoardConfig.mk

BOARD_BOOTIMAGE_PARTITION_SIZE := 41943040
BOARD_AVB_ENABLE := true

# sepolicy
BOARD_SEPOLICY_DIRS += device/google/taimen/sepolicy

ifeq (,$(filter-out taimen_gcc, $(TARGET_PRODUCT)))
# if TARGET_PRODUCT == taimen_gcc
BOARD_VENDOR_KERNEL_MODULES += \
    device/google/wahoo-kernel/gcc/touch_core_base.ko \
    device/google/wahoo-kernel/gcc/ftm4.ko \
    device/google/wahoo-kernel/gcc/sw49408.ko \
    device/google/wahoo-kernel/gcc/lge_battery.ko
else ifeq (,$(filter-out taimen_kasan, $(TARGET_PRODUCT)))
# if TARGET_PRODUCT == taimen_kasan
BOARD_VENDOR_KERNEL_MODULES += \
    device/google/wahoo-kernel/kasan/touch_core_base.ko \
    device/google/wahoo-kernel/kasan/ftm4.ko \
    device/google/wahoo-kernel/kasan/sw49408.ko \
    device/google/wahoo-kernel/kasan/lge_battery.ko
else
BOARD_VENDOR_KERNEL_MODULES += \
    device/google/wahoo-kernel/touch_core_base.ko \
    device/google/wahoo-kernel/ftm4.ko \
    device/google/wahoo-kernel/sw49408.ko \
    device/google/wahoo-kernel/lge_battery.ko
endif

-include vendor/google_devices/taimen/proprietary/BoardConfigVendor.mk

# Testing related defines
BOARD_PERFSETUP_SCRIPT := platform_testing/scripts/perf-setup/wahoo-setup.sh

BOARD_LISA_TARGET_SCRIPTS := device/google/wahoo/lisa/

# Rounded corners recovery UI. 105px = 30dp * 3.5 density, where 30dp comes from
# rounded_corner_radius in overlay/frameworks/base/packages/SystemUI/res/values/dimens.xml.
TARGET_RECOVERY_UI_MARGIN_HEIGHT := 105
