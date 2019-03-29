# Prepare kernel modules, if any

# kernel_module.mk may already be included in odm.mk.
ifneq (true,$(MTK_KERNEL_MODULES_MK_PARSED))
    -include device/mediatek/$(MTK_PLATFORM_DIR)/kernel_modules.mk
    MTK_KERNEL_MODULES_MK_PARSED := true
endif

# Register kernel modules for recovery image
ifneq (true,$(strip $(TARGET_NO_KERNEL)))
    BOARD_RECOVERY_KERNEL_MODULES := $(strip $(MTK_KERNEL_MODULES))
endif
