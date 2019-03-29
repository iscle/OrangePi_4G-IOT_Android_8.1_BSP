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

$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/embedded.mk)

PRODUCT_NAME := uml
PRODUCT_DEVICE := uml
PRODUCT_BRAND := Android
PRODUCT_MODEL := UML for x86_64

# default is nosdcard, S/W button enabled in resource
DEVICE_PACKAGE_OVERLAYS := device/generic/x86/overlay
PRODUCT_CHARACTERISTICS := nosdcard

PRODUCT_COPY_FILES += $(LOCAL_PATH)/fstab.uml:root/fstab.uml
PRODUCT_COPY_FILES += $(LOCAL_PATH)/init.uml.rc:root/init.uml.rc
PRODUCT_COPY_FILES += $(LOCAL_PATH)/surfaceflinger.rc:system/etc/init/surfaceflinger.rc
