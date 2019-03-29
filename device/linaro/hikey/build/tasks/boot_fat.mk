ifneq ($(filter hikey%, $(TARGET_DEVICE)),)

ifeq ($(TARGET_BOOTIMAGE_USE_FAT), true)
$(PRODUCT_OUT)/boot_fat.uefi.img: $(INSTALLED_KERNEL_TARGET) $(INSTALLED_RAMDISK_TARGET) $(PRODUCT_OUT)/hi6220-hikey.dtb
# $@ is referring to $(PRODUCT_OUT)/boot_fat.uefi.img
	dd if=/dev/zero of=$@ bs=512 count=98304
	mkfs.fat -n "boot" $@
	$(FAT16COPY) $@ $(PRODUCT_OUT)/kernel
	$(FAT16COPY) $@ $(PRODUCT_OUT)/hi6220-hikey.dtb
	$(FAT16COPY) $@ device/linaro/hikey/bootloader/*
	$(FAT16COPY) $@ $(PRODUCT_OUT)/ramdisk.img

droidcore: $(PRODUCT_OUT)/boot_fat.uefi.img
endif

endif
