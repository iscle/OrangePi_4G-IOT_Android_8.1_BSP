#
# Copyright (C) 2011 The Android Open-Source Project
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

PRODUCT_COPY_FILES +=	device/linaro/hikey-kernel/Image.gz-hikey960:kernel \
			device/linaro/hikey-kernel/hi3660-hikey960.dtb:hi3660-hikey960.dtb

PRODUCT_COPY_FILES +=	$(LOCAL_PATH)/fstab.hikey960:root/fstab.hikey960 \
			device/linaro/hikey/init.common.rc:root/init.hikey960.rc \
			device/linaro/hikey/init.hikey960.power.rc:root/init.hikey960.power.rc \
			device/linaro/hikey/init.common.usb.rc:root/init.hikey960.usb.rc \
			device/linaro/hikey/ueventd.common.rc:root/ueventd.hikey960.rc \
			device/linaro/hikey/common.kl:system/usr/keylayout/hikey960.kl \
			frameworks/native/data/etc/android.hardware.vulkan.level-0.xml:system/etc/permissions/android.hardware.vulkan.level.xml \
			frameworks/native/data/etc/android.hardware.vulkan.version-1_0_3.xml:system/etc/permissions/android.hardware.vulkan.version.xml

# Build HiKey960 HDMI audio HAL. Experimental only may not work. FIXME
PRODUCT_PACKAGES += audio.primary.hikey960

PRODUCT_PACKAGES += gralloc.hikey960

PRODUCT_PACKAGES += power.hikey960

# Include vendor binaries
$(call inherit-product-if-exists, vendor/linaro/hikey960/device-vendor.mk)
