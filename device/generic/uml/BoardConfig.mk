#
# Product-specific compile-time definitions.
#

# The generic product target doesn't have any hardware-specific pieces.
TARGET_NO_BOOTLOADER := true
TARGET_NO_KERNEL := true
TARGET_CPU_ABI := x86_64
TARGET_ARCH := x86_64
TARGET_ARCH_VARIANT := x86_64

TARGET_USER_MODE_LINUX := true

TARGET_USES_64_BIT_BINDER := true

TARGET_USERIMAGES_USE_EXT4 := true
# Let UML mount userdata.img in a non-sparse format
TARGET_USERIMAGES_SPARSE_EXT_DISABLED := true

BOARD_SYSTEMIMAGE_PARTITION_SIZE := 786432000
BOARD_USERDATAIMAGE_PARTITION_SIZE := 576716800
BOARD_FLASH_BLOCK_SIZE := 512
