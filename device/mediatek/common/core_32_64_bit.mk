#
# Copyright (C) 2014 The Android Open Source Project
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

ifeq ($(MTK_GMO_PREFER_32BIT),yes)
# If you want the build system to build 32-bit executables and apps by default, set TARGET_PREFER_32_BIT to be yes.
#TARGET_PREFER_32_BIT :=yes
TARGET_PREFER_32_BIT_APPS := true

# Inherit from this product for devices that support 64-bit apps using:
# $(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
# The inheritance for this must come before the inheritance chain that leads
# to core_minimal.mk

# For now this will allow 64-bit apps, but still compile all apps with JNI
# for 32-bit only.

ifeq ($(TARGET_PREFER_32_BIT_APPS),true)
# Copy the 32-bit primary, 64-bit secondary zygote startup script
PRODUCT_COPY_FILES += system/core/rootdir/init.zygote32_64.rc:root/init.zygote32_64.rc

# Set the zygote property to select the 32-bit primary, 64-bit secondary script
# This line must be parsed before the one in core_minimal.mk
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.zygote=zygote32_64

# Disable services,wifi-service and ethernet-service pre-otp while primary zygote is 32-bit.
$(call add-product-dex-preopt-module-config,services,disable)
$(call add-product-dex-preopt-module-config,wifi-service,disable)
$(call add-product-dex-preopt-module-config,ethernet-service,disable)
else
# Copy the 64-bit primary, 32-bit secondary zygote startup script
PRODUCT_COPY_FILES += system/core/rootdir/init.zygote64_32.rc:root/init.zygote64_32.rc
# Set the zygote property to select the 64-bit primary, 32-bit secondary script
# This line must be parsed before the one in core_minimal.mk
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.zygote=zygote64_32
endif

TARGET_SUPPORTS_32_BIT_APPS := true
TARGET_SUPPORTS_64_BIT_APPS := true
else
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
endif
