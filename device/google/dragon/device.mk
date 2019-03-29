#
# Copyright (C) 2015 The Android Open-Source Project
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

# There are three modes for TLK components, controlled by SECURE_OS_BUILD:
#
# "SECURE_OS_BUILD = tlk" will build everything from source.
# "SECURE_OS_BUILD = client_only" will build TLK client components from
#                    source but use a prebuilt version of the secure OS.
# "SECURE_OS_BUILD = false" will will use prebuilts for TLK and client
#                    components.

# By default build TLK from source if it is available, otherwise use
# prebuilts.  To force using the prebuilt while having the source, set:
# SECURE_OS_BUILD=false
ifeq ($(wildcard vendor/nvidia/dragon-tlk/tlk),vendor/nvidia/dragon-tlk/tlk)
    SECURE_OS_BUILD ?= tlk
endif

ifeq ($(TARGET_PREBUILT_KERNEL),)
LOCAL_KERNEL := device/google/dragon-kernel/Image.fit
else
LOCAL_KERNEL := $(TARGET_PREBUILT_KERNEL)
endif

ifeq ($(TARGET_PRODUCT), ryu_kasan)
LOCAL_FSTAB := $(LOCAL_PATH)/fstab.dragon.nocrypt
else
LOCAL_FSTAB := $(LOCAL_PATH)/fstab.dragon
endif

TARGET_RECOVERY_FSTAB = $(LOCAL_FSTAB)

PRODUCT_COPY_FILES := \
    $(LOCAL_KERNEL):kernel \
    $(LOCAL_PATH)/dump_bq25892.sh:system/bin/dump_bq25892.sh \
    $(LOCAL_PATH)/touchfwup.sh:system/bin/touchfwup.sh \
    $(LOCAL_PATH)/init.dragon.rc:root/init.dragon.rc \
    $(LOCAL_PATH)/init.dragon.usb.rc:root/init.dragon.usb.rc \
    $(LOCAL_PATH)/init.recovery.dragon.rc:root/init.recovery.dragon.rc \
    $(LOCAL_PATH)/init_regions.sh:system/bin/init_regions.sh \
    $(LOCAL_PATH)/tune-thermal-gov.sh:system/bin/tune-thermal-gov.sh \
    $(LOCAL_FSTAB):root/fstab.dragon \
    $(LOCAL_PATH)/ueventd.dragon.rc:root/ueventd.dragon.rc \
    $(LOCAL_PATH)/speakerdsp.ini:system/etc/cras/speakerdsp.ini \
    $(LOCAL_PATH)/bcmdhd.cal:system/etc/wifi/bcmdhd.cal \
    $(LOCAL_PATH)/com.nvidia.nvsi.xml:system/etc/permissions/com.nvidia.nvsi.xml

PRODUCT_PACKAGES += \
    android.hardware.wifi@1.0-service \
    libwpa_client \
    hostapd \
    wificond \
    wifilogd \
    wpa_supplicant \
    wpa_supplicant.conf \
    fs_config_files \
    crash_dispatcher \
    crash_collector \
    CrashReportProvider \
    fwtool

ifeq ($(TARGET_BUILD_VARIANT),eng)
PRODUCT_PACKAGES += \
    tinyplay \
    tinycap \
    tinymix
endif

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/tablet_core_hardware.xml:system/etc/permissions/tablet_core_hardware.xml \
    frameworks/native/data/etc/android.hardware.touchscreen.multitouch.jazzhand.xml:system/etc/permissions/android.hardware.touchscreen.multitouch.jazzhand.xml \
    frameworks/native/data/etc/android.hardware.camera.autofocus.xml:system/etc/permissions/android.hardware.camera.autofocus.xml \
    frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
    frameworks/native/data/etc/android.hardware.camera.full.xml:system/etc/permissions/android.hardware.camera.full.xml \
    frameworks/native/data/etc/android.hardware.wifi.xml:system/etc/permissions/android.hardware.wifi.xml \
    frameworks/native/data/etc/android.hardware.wifi.direct.xml:system/etc/permissions/android.hardware.wifi.direct.xml \
    frameworks/native/data/etc/android.hardware.wifi.passpoint.xml:system/etc/permissions/android.hardware.wifi.passpoint.xml \
    frameworks/native/data/etc/android.hardware.ethernet.xml:system/etc/permissions/android.hardware.ethernet.xml \
    frameworks/native/data/etc/android.hardware.usb.accessory.xml:system/etc/permissions/android.hardware.usb.accessory.xml \
    frameworks/native/data/etc/android.hardware.usb.host.xml:system/etc/permissions/android.hardware.usb.host.xml \
    frameworks/native/data/etc/android.hardware.audio.low_latency.xml:system/etc/permissions/android.hardware.audio.low_latency.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:system/etc/permissions/android.hardware.bluetooth_le.xml \
    frameworks/native/data/etc/android.hardware.opengles.aep.xml:system/etc/permissions/android.hardware.opengles.aep.xml \
    frameworks/native/data/etc/android.hardware.sensor.accelerometer.xml:system/etc/permissions/android.hardware.sensor.accelerometer.xml \
    frameworks/native/data/etc/android.hardware.sensor.compass.xml:system/etc/permissions/android.hardware.sensor.compass.xml \
    frameworks/native/data/etc/android.hardware.sensor.gyroscope.xml:system/etc/permissions/android.hardware.sensor.gyroscope.xml \
    frameworks/native/data/etc/android.hardware.sensor.light.xml:system/etc/permissions/android.hardware.sensor.light.xml \
    frameworks/native/data/etc/android.software.midi.xml:system/etc/permissions/android.software.midi.xml \
    frameworks/native/data/etc/android.software.verified_boot.xml:system/etc/permissions/android.software.verified_boot.xml \
    frameworks/native/data/etc/android.hardware.vulkan.level-1.xml:system/etc/permissions/android.hardware.vulkan.level.xml \
    frameworks/native/data/etc/android.hardware.vulkan.version-1_0_3.xml:system/etc/permissions/android.hardware.vulkan.version.xml

PRODUCT_COPY_FILES += \
    frameworks/av/services/audiopolicy/config/a2dp_audio_policy_configuration.xml:system/etc/a2dp_audio_policy_configuration.xml \
    frameworks/av/services/audiopolicy/config/usb_audio_policy_configuration.xml:system/etc/usb_audio_policy_configuration.xml \
    frameworks/av/services/audiopolicy/config/r_submix_audio_policy_configuration.xml:system/etc/r_submix_audio_policy_configuration.xml \
    frameworks/av/services/audiopolicy/config/default_volume_tables.xml:system/etc/default_volume_tables.xml \
    $(LOCAL_PATH)/audio_policy_configuration.xml:system/etc/audio_policy_configuration.xml \
    $(LOCAL_PATH)/audio_policy_volumes_drc.xml:system/etc/audio_policy_volumes_drc.xml \
    $(LOCAL_PATH)/mixer_paths_0.xml:system/etc/mixer_paths_0.xml

PRODUCT_COPY_FILES += \
    frameworks/av/media/libstagefright/data/media_codecs_google_audio.xml:system/etc/media_codecs_google_audio.xml \
    frameworks/av/media/libstagefright/data/media_codecs_google_video.xml:system/etc/media_codecs_google_video.xml \
    $(LOCAL_PATH)/media_codecs.xml:system/etc/media_codecs.xml \
    $(LOCAL_PATH)/media_codecs_performance.xml:system/etc/media_codecs_performance.xml \
    $(LOCAL_PATH)/media_profiles.xml:system/etc/media_profiles.xml

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/bluetooth/BCM4350C0_003.001.012.0364.0754.hcd:$(TARGET_COPY_OUT_VENDOR)/firmware/bcm4350c0.hcd \
    $(LOCAL_PATH)/bluetooth/bt_vendor.conf:$(TARGET_COPY_OUT_SYSTEM)/etc/bluetooth/bt_vendor.conf

# Bluetooth HAL
PRODUCT_PACKAGES += \
    libbt-vendor \
    android.hardware.bluetooth@1.0-impl-dragon

# Copy dsp firmware to the vendor parition so it is available when hotwording
# starts
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rt5677_elf_vad:vendor/firmware/rt5677_elf_vad

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/enctune.conf:system/etc/enctune.conf

PRODUCT_COPY_FILES += \
    device/google/dragon/audio_effects.conf:system/etc/audio_effects.conf

PRODUCT_AAPT_CONFIG := normal large xlarge hdpi xhdpi xxhdpi
PRODUCT_AAPT_PREF_CONFIG := xhdpi

PRODUCT_CHARACTERISTICS := tablet,nosdcard

DEVICE_PACKAGE_OVERLAYS := \
    $(LOCAL_PATH)/overlay

PRODUCT_TAGS += dalvik.gc.type-precise

PRODUCT_PACKAGES += \
    librs_jni \
    com.android.future.usb.accessory

PRODUCT_PACKAGES += \
    power.dragon \
    sensors.dragon \
    android.hardware.sensors@1.0-impl \
    android.hardware.power@1.0-impl

# Health HAL
PRODUCT_PACKAGES += \
    android.hardware.health@1.0-impl

# Light HAL
PRODUCT_PACKAGES += \
    lights.dragon \
    android.hardware.light@2.0-impl

# Keymaster HAL
PRODUCT_PACKAGES += \
    android.hardware.keymaster@3.0-impl

# Gatekeeper HAL
PRODUCT_PACKAGES += \
    android.hardware.gatekeeper@1.0-impl

# Dumpstate HAL
PRODUCT_PACKAGES += \
    android.hardware.dumpstate@1.0-service.dragon

# Gralloc HAL
PRODUCT_PACKAGES += \
    android.hardware.graphics.mapper@2.0-impl \
    android.hardware.graphics.allocator@2.0-impl \
    android.hardware.graphics.allocator@2.0-service

#TODO(dgreid) is this right?
PRODUCT_PROPERTY_OVERRIDES := \
    wifi.interface=wlan0 \
    ro.hwui.texture_cache_size=86 \
    ro.hwui.layer_cache_size=56 \
    ro.hwui.r_buffer_cache_size=8 \
    ro.hwui.path_cache_size=40 \
    ro.hwui.gradient_cache_size=1 \
    ro.hwui.drop_shadow_cache_size=6 \
    ro.hwui.texture_cache_flushrate=0.4 \
    ro.hwui.text_small_cache_width=1024 \
    ro.hwui.text_small_cache_height=1024 \
    ro.hwui.text_large_cache_width=2048 \
    ro.hwui.text_large_cache_height=1024 \
    ro.hwui.disable_scissor_opt=true

# The default locale should be determined from VPD, not from build.prop.
PRODUCT_SYSTEM_PROPERTY_BLACKLIST := \
    ro.product.locale

# OEM Unlock reporting
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    ro.oem_unlock_supported=1

# setup dalvik vm configs.
$(call inherit-product, frameworks/native/build/tablet-10in-xhdpi-2048-dalvik-heap.mk)

# set default USB configuration
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    ro.adb.secure=1 \
    ro.sf.lcd_density=320 \
    ro.opengles.version=196610

# for audio
#TODO(dgreid) do we need libnvvisualizer?
PRODUCT_PACKAGES += \
    audio.primary.dragon \
    sound_trigger.primary.dragon \
    audio.a2dp.default \
    audio.usb.default \
    audio.r_submix.default

PRODUCT_PACKAGES += \
    android.hardware.audio@2.0-impl \
    android.hardware.audio.effect@2.0-impl \
    android.hardware.soundtrigger@2.0-impl

PRODUCT_PACKAGES += \
    android.hardware.drm@1.0-impl

PRODUCT_PACKAGES += \
    android.hardware.graphics.composer@2.1-impl

PRODUCT_PROPERTY_OVERRIDES += \
    ro.audio.monitorRotation=true \
    ro.frp.pst=/dev/block/platform/700b0600.sdhci/by-name/PST

# Default OMX service to non-Treble
PRODUCT_PROPERTY_OVERRIDES += \
    persist.media.treble_omx=false

# ro.product.first_api_level indicates the first api level the device has commercially launched on.
PRODUCT_PROPERTY_OVERRIDES += \
    ro.product.first_api_level=23

# for keyboard key mappings
PRODUCT_PACKAGES += \
    DragonKeyboard

# Vibrator HAL
PRODUCT_PACKAGES += \
    android.hardware.vibrator@1.0-impl

# Allows healthd to boot directly from charger mode rather than initiating a reboot.
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    ro.enable_boot_charger_mode=1

# TODO(dgreid) - Add back verity dependencies like flounder has.

$(call inherit-product, build/target/product/vboot.mk)
$(call inherit-product, build/target/product/verity.mk)
# including verity.mk automatically enabled boot signer which conflicts with
# vboot
PRODUCT_SUPPORTS_BOOT_SIGNER := false
PRODUCT_SUPPORTS_VERITY_FEC := false
PRODUCT_SYSTEM_VERITY_PARTITION := /dev/block/platform/700b0600.sdhci/by-name/APP
PRODUCT_VENDOR_VERITY_PARTITION := /dev/block/platform/700b0600.sdhci/by-name/VNR

# The following group is necessary to support building the NVIDIA vendor
# HALs and prebuilts.
BOARD_SUPPORT_NVOICE := true
BOARD_SUPPORT_NVAUDIOFX :=true
BOARD_USES_LIBDRM := true
NVRM_GPU_SUPPORT_NOUVEAU := 1
NV_GPU_USE_SYNC_FD := 1
USE_DRM_HWCOMPOSER := 1

PRODUCT_PROPERTY_OVERRIDES += \
    ro.hardware.vulkan=tegra

$(call inherit-product-if-exists, hardware/nvidia/tegra132/tegra132.mk)
$(call inherit-product-if-exists, vendor/google_devices/dragon/device-vendor.mk)
$(call inherit-product-if-exists, hardware/broadcom/wlan/bcmdhd/config/config-bcm.mk)

ENABLE_LIBDRM := true
BOARD_GPU_DRIVERS := tegra
USE_XML_AUDIO_POLICY_CONF := 1
PRODUCT_PACKAGES += \
    f54test \
    hwcomposer.drm \
    libdrm \
    rmi4update \
    rmihidtool

# Vendor seccomp policy files for media components:
PRODUCT_COPY_FILES += \
    device/google/dragon/seccomp_policy/mediacodec.policy:$(TARGET_COPY_OUT_VENDOR)/etc/seccomp_policy/mediacodec.policy

$(call inherit-product-if-exists, vendor/nvidia/dragon/dragon-vendor.mk)
