# sign image used to get boot and recovery sig files

ifndef build-signimage-target
ifneq ($(wildcard vendor/mediatek/proprietary/custom/$(MTK_PLATFORM_DIR)/security/cert_config/img_list.txt),)
define build-signimage-target
	@echo "v2 sign flow"
	OUT=$(PRODUCT_OUT) python vendor/mediatek/proprietary/scripts/sign-image_v2/SignFlow.py $(MTK_PLATFORM_DIR) $(MTK_BASE_PROJECT)
endef
else
define build-signimage-target
	@echo "v1 sign flow"
	perl vendor/mediatek/proprietary/scripts/sign-image/SignTool.pl $(MTK_BASE_PROJECT) $(MTK_PROJECT_NAME) $(MTK_PATH_CUSTOM) $(MTK_SEC_SECRO_AC_SUPPORT) $(MTK_NAND_PAGE_SIZE) $(PRODUCT_OUT) $(OUT_DIR)
endef
endif
endif

INSTALLED_SIGNIMAGE_TARGET := $(wildcard $(PRODUCT_OUT)/*-verified.*)
ifneq ($(INSTALLED_SIGNIMAGE_TARGET),)
  BUILT_SIGNIMAGE_TARGET := $(firstword $(INSTALLED_SIGNIMAGE_TARGET))
else
  BUILT_SIGNIMAGE_TARGET := $(PRODCUT_OUT)/boot-verified.img
endif

.PHONY: sign-image sign-image-nodeps
# TODO
#$(BUILT_SIGNIMAGE_TARGET): \
#		$(INSTALLED_BOOTIMAGE_TARGET) \
#		$(INSTALLED_RADIOIMAGE_TARGET) \
#		$(INSTALLED_RECOVERYIMAGE_TARGET) \
#		$(INSTALLED_SYSTEMIMAGE) \
#		$(INSTALLED_USERDATAIMAGE_TARGET) \
#		$(INSTALLED_CACHEIMAGE_TARGET) \
#		$(INSTALLED_VENDORIMAGE_TARGET)
#$(BUILT_SIGNIMAGE_TARGET): \
#		$(filter-out $(TARGET_OUT)/%,$(MTK_MODEM_INSTALLED_MODULES))
#	$(call build-signimage-target)
#
#sign-image: $(BUILT_SIGNIMAGE_TARGET)
#$(BUILT_TARGET_FILES_PACKAGE): $(BUILT_SIGNIMAGE_TARGET)

sign-image-nodeps:
	$(call build-signimage-target)

ifneq ($(INSTALLED_SIGNIMAGE_TARGET),)
$(info Found sign-image: $(INSTALLED_SIGNIMAGE_TARGET))
update-modem: sign-image-nodeps
sign-image-nodeps: snod
sign-image-nodeps: $(filter-out $(TARGET_OUT)/%,$(MTK_MODEM_INSTALLED_MODULES))
endif

ifneq ($(strip $(MTK_MODEM_APPS_FILES)),)
snod vnod: $(call module-installed-files,$(ALL_MODULES.selinux_policy.REQUIRED))
endif

