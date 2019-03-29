#
# Copyright 2016 The Android Open Source Project
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

# Inherit from the common Open Source product configuration
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_base_telephony.mk)


$(call inherit-product, device/google/muskie/device-walleye.mk)
$(call inherit-product-if-exists, vendor/google_devices/muskie/proprietary/device-vendor-walleye.mk)

PRODUCT_PACKAGES += \
    Dialer \
    Launcher3 \
    WallpaperPicker

PRODUCT_COPY_FILES += \
    device/google/muskie/audio_effects.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_effects.xml

PRODUCT_RESTRICT_VENDOR_FILES := owner

PRODUCT_MANUFACTURER := Google
PRODUCT_BRAND := Android
PRODUCT_NAME := aosp_walleye
PRODUCT_DEVICE := walleye
PRODUCT_MODEL := AOSP on walleye
