include device/linaro/hikey/BoardConfigCommon.mk

TARGET_CPU_VARIANT := cortex-a53
TARGET_2ND_CPU_VARIANT := cortex-a53

TARGET_BOARD_PLATFORM := hikey
ifeq ($(TARGET_KERNEL_USE), 3.18)
BOARD_KERNEL_CMDLINE := console=ttyAMA3,115200 androidboot.console=ttyAMA3 androidboot.hardware=hikey firmware_class.path=/system/etc/firmware efi=noruntime
else
BOARD_KERNEL_CMDLINE := console=ttyFIQ0 androidboot.console=ttyFIQ0 androidboot.hardware=hikey firmware_class.path=/system/etc/firmware efi=noruntime
endif

## printk.devkmsg only has meaning for kernel 4.9 and later
## it would be ignored by kernel 3.18 and kernel 4.4
BOARD_KERNEL_CMDLINE += printk.devkmsg=on

TARGET_NO_DTIMAGE := true

BOARD_SYSTEMIMAGE_PARTITION_SIZE := 1610612736
ifeq ($(TARGET_USERDATAIMAGE_4GB), true)
BOARD_USERDATAIMAGE_PARTITION_SIZE := 1595915776
else
BOARD_USERDATAIMAGE_PARTITION_SIZE := 5588893184
endif
BOARD_CACHEIMAGE_PARTITION_SIZE := 268435456
BOARD_FLASH_BLOCK_SIZE := 131072
