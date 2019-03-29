# config.mk
#
# Product-specific compile-time definitions.
#

TARGET_NO_BOOTLOADER := true
TARGET_NO_KERNEL := true

TARGET_ARCH := mips64
ifeq (,$(TARGET_ARCH_VARIANT))
TARGET_ARCH_VARIANT := mips64r6
endif
TARGET_CPU_ABI  := mips64

TARGET_2ND_ARCH := mips
ifeq (,$(TARGET_2ND_ARCH_VARIANT))
ifeq ($(TARGET_ARCH_VARIANT),mips64r6)
TARGET_2ND_ARCH_VARIANT :=  mips32r6
else
TARGET_2ND_ARCH_VARIANT :=  mips32r2-fp
endif
endif
TARGET_2ND_CPU_ABI  := mips

# Make TARGET_XXX_CPU_VARIANT the same as TARGET_XXX_ARCH_VARIANT
TARGET_CPU_VARIANT := $(TARGET_ARCH_VARIANT)
TARGET_2ND_CPU_VARIANT := $(TARGET_2ND_ARCH_VARIANT)

# The emulator (qemu) uses the Goldfish devices
HAVE_HTC_AUDIO_DRIVER := true
BOARD_USES_GENERIC_AUDIO := true

USE_CAMERA_STUB := true

# Enable dex-preoptimization to speed up the first boot sequence
# of an SDK AVD. Note that this operation only works on Linux for now
ifeq ($(HOST_OS),linux)
  ifeq ($(WITH_DEXPREOPT),)
    WITH_DEXPREOPT := true
  endif
endif

# Build OpenGLES emulation guest and host libraries
BUILD_EMULATOR_OPENGL := true
USE_OPENGL_RENDERER := true

BOARD_USE_LEGACY_UI := true

# PDK does not use ext4 image, but it is added here to prevent build break.
BOARD_EGL_CFG := device/generic/goldfish-opengl/system/egl/egl.cfg
TARGET_USERIMAGES_USE_EXT4 := true
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 786432000
BOARD_USERDATAIMAGE_PARTITION_SIZE := 576716800
BOARD_CACHEIMAGE_PARTITION_SIZE := 69206016
BOARD_CACHEIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_FLASH_BLOCK_SIZE := 512
TARGET_USERIMAGES_SPARSE_EXT_DISABLED := true

BOARD_SEPOLICY_DIRS += build/target/board/generic/sepolicy

DEX_PREOPT_DEFAULT := nostripping
