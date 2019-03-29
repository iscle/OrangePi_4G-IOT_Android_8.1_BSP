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

include $(SRC_TARGET_DIR)/product/emulator.mk
$(call inherit-product, device/generic/mips64/mini_mips64.mk)

$(call inherit-product, device/generic/mini-emulator-armv7-a-neon/mini_emulator_common.mk)

PRODUCT_NAME := m_e_mips64
PRODUCT_DEVICE := mini-emulator-mips64
PRODUCT_BRAND := Android
PRODUCT_MODEL := mini-emulator-mips64

LOCAL_KERNEL := prebuilts/qemu-kernel/mips64/kernel-qemu
PRODUCT_COPY_FILES += \
    $(LOCAL_KERNEL):kernel
