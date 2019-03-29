ifneq ($(filter hikey%, $(TARGET_DEVICE)),)
ifneq ($(TARGET_NO_DTIMAGE), true)

MKDTIMG := device/linaro/hikey/installer/hikey960/mkdtimg
DTB := $(PRODUCT_OUT)/hi3660-hikey960.dtb

$(PRODUCT_OUT)/dt.img: $(DTB)
	$(MKDTIMG) -c -d $(DTB) -o $@

droidcore: $(PRODUCT_OUT)/dt.img

# Images will be packed into target_files zip, and hikey-img.zip.
INSTALLED_RADIOIMAGE_TARGET += $(PRODUCT_OUT)/dt.img
BOARD_PACK_RADIOIMAGES += dt.img

endif
endif
