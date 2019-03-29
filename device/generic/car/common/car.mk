#
# Copyright (C) 2017 The Android Open Source Project
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

# TODO Remove this when AAE is fully treble ready
PRODUCT_FULL_TREBLE_OVERRIDE := false

# Auto modules
PRODUCT_PACKAGES += \
    android.hardware.automotive.vehicle@2.0-service

# Emulator configuration
PRODUCT_COPY_FILES += \
    device/generic/car/common/config.ini:config.ini

# Car init.rc
PRODUCT_COPY_FILES += \
    packages/services/Car/car_product/init/init.bootstat.rc:root/init.bootstat.rc \
    packages/services/Car/car_product/init/init.car.rc:root/init.car.rc

# Enable landscape
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.screen.landscape.xml:system/etc/permissions/android.hardware.screen.landscape.xml

# Overwrite handheld_core_hardware.xml with a dummy config.
PRODUCT_COPY_FILES += \
    device/generic/car/common/android.hardware.dummy.xml:system/etc/permissions/handheld_core_hardware.xml \
    device/generic/car/common/car_core_hardware.xml:system/etc/permissions/car_core_hardware.xml \
    frameworks/native/data/etc/android.hardware.type.automotive.xml:system/etc/permissions/android.hardware.type.automotive.xml

# Vendor Interface Manifest
PRODUCT_COPY_FILES += \
    device/generic/car/common/manifest.xml:$(TARGET_COPY_OUT_VENDOR)/manifest.xml

PRODUCT_PROPERTY_OVERRIDES += \
    android.car.drawer.unlimited=true \
    android.car.hvac.demo=true \
    com.android.car.radio.demo=true \
    com.android.car.radio.demo.dual=true

TARGET_USES_CAR_FUTURE_FEATURES := true

# Add car related sepolicy.
BOARD_SEPOLICY_DIRS += \
    device/generic/car/common/sepolicy \
    packages/services/Car/car_product/sepolicy

$(call inherit-product, packages/services/Car/car_product/build/car.mk)
