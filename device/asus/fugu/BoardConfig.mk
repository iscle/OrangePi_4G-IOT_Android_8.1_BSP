#
# Copyright 2013 The Android Open-Source Project
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

# Use the non-open-source parts, if they're present
-include vendor/intel/fugu/BoardConfigVendor.mk

TARGET_ARCH := x86
TARGET_ARCH_VARIANT := silvermont
TARGET_CPU_ABI := x86

TARGET_RECOVERY_FSTAB := device/asus/fugu/recovery.fstab
TARGET_RELEASETOOLS_EXTENSIONS := device/asus/fugu
TARGET_RECOVERY_UPDATER_LIBS := librecovery_updater_fugu
TARGET_RECOVERY_UI_LIB := librecovery_ui_fugu

TARGET_BOARD_PLATFORM := moorefield
TARGET_BOOTLOADER_BOARD_NAME := fugu
TARGET_USERIMAGES_USE_EXT4 := true
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 1073741824
BOARD_SYSTEMIMAGE_JOURNAL_SIZE := 0
# as of 3562118, inode usage was 2149, use 4096 to be safe
BOARD_SYSTEMIMAGE_EXTFS_INODE_COUNT := 4096
BOARD_FLASH_BLOCK_SIZE := 2048

BOARD_CACHEIMAGE_PARTITION_SIZE := 260014080

TARGET_DROIDBOOT_LIBS := libintel_droidboot
TARGET_RECOVERY_PIXEL_FORMAT := "RGBX_8888"

MALLOC_SVELTE := true

# Kernel cmdline
BOARD_KERNEL_CMDLINE := pci=noearly vmalloc=256M ptrace.ptrace_can_access=1
BOARD_KERNEL_CMDLINE += earlyprintk=nologger loglevel=8
BOARD_KERNEL_CMDLINE += androidboot.hardware=fugu androidboot.serialno=01234567890123456789
BOARD_KERNEL_CMDLINE += snd_pcm.maximum_substreams=8
BOARD_KERNEL_CMDLINE += intel_soc_pmu.enable_s3=0
BOARD_KERNEL_CMDLINE += loop.max_part=7

# Binder API version
TARGET_USES_64_BIT_BINDER := true

# Security
BUILD_WITH_SECURITY_FRAMEWORK := chaabi_token
BUILD_WITH_CHAABI_SUPPORT := true

# Wifi
BOARD_WLAN_DEVICE := bcmdhd
BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_bcmdhd
BOARD_HOSTAPD_PRIVATE_LIB   := lib_driver_cmd_bcmdhd
WPA_SUPPLICANT_VERSION := VER_0_8_X
BOARD_WPA_SUPPLICANT_DRIVER := NL80211
WIFI_DRIVER_FW_PATH_PARAM := "/sys/module/bcmdhd/parameters/firmware_path"
WIFI_DRIVER_FW_PATH_AP      := "/vendor/firmware/fw_bcmdhd_apsta.bin"
WIFI_DRIVER_FW_PATH_STA     := "/vendor/firmware/fw_bcmdhd.bin"

# Bluetooth
BOARD_HAVE_BLUETOOTH := true
BOARD_HAVE_BLUETOOTH_BCM := true
BOARD_BLUETOOTH_BDROID_BUILDCFG_INCLUDE_DIR := device/asus/fugu/bluetooth

# IMG graphics
BOARD_GFX_REV := RGX6400
ENABLE_IMG_GRAPHICS := true
ENABLE_MRFL_GRAPHICS := true
INTEL_HWC_MOOREFIELD_HDMI := true
HWUI_IMG_FBO_CACHE_OPTIM := true
TARGET_SUPPORT_HDMI_PRIMARY := true
BOARD_USES_LIBDRM := true

TARGET_USES_HWC2 := true
NUM_FRAMEBUFFER_SURFACE_BUFFERS := 3
SF_START_GRAPHICS_ALLOCATOR_SERVICE := true

# Audio
BOARD_USES_ALSA_AUDIO := true
BOARD_USES_TINY_ALSA_AUDIO := true

# System's VSYNC phase offsets in nanoseconds
VSYNC_EVENT_PHASE_OFFSET_NS := 7500000
SF_VSYNC_EVENT_PHASE_OFFSET_NS := 5000000

BOARD_EGL_CFG := device/asus/fugu/egl.cfg

MAX_EGL_CACHE_ENTRY_SIZE := 65536
MAX_EGL_CACHE_SIZE := 1048576

INTEL_VA := true
BUILD_WITH_FULL_STAGEFRIGHT := true
BOARD_USES_VIDEO := true
BOARD_USES_WRS_OMXIL_CORE := true
BOARD_USES_MRST_OMX := true
USE_HW_VP8 := true

# DRM Protected Video
BOARD_WIDEVINE_OEMCRYPTO_LEVEL := 1
USE_INTEL_SECURE_AVC := true

# Settings for the Media SDK library and plug-ins:
# - USE_MEDIASDK: use Media SDK support or not
# - MFX_IPP: sets IPP library optimization to use
USE_MEDIASDK := true
MFX_IPP := p8

# Video Post Processing
TARGET_HAS_ISV := true

OVERRIDE_RS_DRIVER := libPVRRS.so

# enable ARM codegen for x86 with Houdini
BUILD_ARM_FOR_X86 := true

# enabled to carry out all drawing operations performed on a View's canvas with GPU for 2D rendering pipeline.
USE_OPENGL_RENDERER := true
# DPST
INTEL_DPST := true

# bootstub as 2nd bootloader
TARGET_BOOTLOADER_IS_2ND := true

BOARD_SEPOLICY_DIRS += device/asus/fugu/sepolicy

USE_CLANG_PLATFORM_BUILD := true

# Use the non-open-source parts, if they're present
-include vendor/asus/fugu/BoardConfigVendor.mk

# Recipes to generate prebuilts
-include device/intel/common/external/external.mk

# Don't dex preopt prebuilt apps that will be updated from Play Store
DONT_DEXPREOPT_PREBUILTS := true

# Vendor Interface Manifest
DEVICE_MANIFEST_FILE := device/asus/fugu/manifest.xml
DEVICE_MATRIX_FILE := device/asus/fugu/compatibility_matrix.xml
