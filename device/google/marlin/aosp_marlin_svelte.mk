#
# Copyright 2015 The Android Open Source Project
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

# Sample: This is where we'd set a backup provider if we had one
# $(call inherit-product, device/sample/products/backup_overlay.mk)

PRODUCT_DEFAULT_PROPERTY_OVERRIDES = ro.adb.secure=0
PRODUCT_COPY_FILES += device/google/marlin/fstab.aosp_svelte:root/fstab.marlin

$(call inherit-product, device/google/marlin/aosp_marlin.mk)

PRODUCT_NAME := aosp_marlin_svelte
PRODUCT_DEVICE := marlin
PRODUCT_BRAND := Android
PRODUCT_MODEL := AOSP svelte on msm8996
PRODUCT_MANUFACTURER := google
PRODUCT_RESTRICT_VENDOR_FILES := true

TARGET_PREBUILT_KERNEL := device/google/marlin-kernel/Image.gz-dtb.svelte

PRODUCT_PROPERTY_OVERRIDES += ro.config.low_ram=true
