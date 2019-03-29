# Wlan Configuration
ENABLE_SP = false
ifeq ($(ENABLE_SP), false)
    wlan_patch_folder := vendor/mediatek/proprietary/hardware/connectivity/firmware/wlan
else
    WLAN_SP_PATH := vendor/mediatek/proprietary/hardware/connectivity/firmware/wlan_sp*
    $(warning [wlan] enable sp $(wildcard $(WLAN_SP_PATH)))
    wlan_patch_folder := $(wildcard $(WLAN_SP_PATH))
endif

ifeq ($(strip $(MTK_COMBO_CHIP)), MT7668)
    wlan_drv_config_folder := vendor/mediatek/proprietary/hardware/connectivity/wlan/drv_config
endif

ifeq ($(strip $(MTK_COMBO_CHIP)), MT6632)
    MY_SRC_FILE := WIFI_RAM_CODE_$(MTK_COMBO_CHIP)
else ifeq ($(strip $(MTK_COMBO_CHIP)), MT6630)
    MY_SRC_FILE := WIFI_RAM_CODE_$(MTK_COMBO_CHIP)
else ifeq ($(strip $(MTK_COMBO_CHIP)), MT7668)
    MY_SRC_FILE := WIFI_RAM_CODE_$(MTK_COMBO_CHIP).bin
else
    # remove prefix and subffix chars, only left numbers.
    WLAN_CHIP_ID := $(patsubst consys_%,%,$(patsubst CONSYS_%,%,$(strip $(MTK_COMBO_CHIP))))
    # WLAN_CHIP_ID exist
    ifneq ($(strip $(WLAN_CHIP_ID)),)
        # If your chip will share the same ram code with other chips, and the ram code name is not WIFI_RAM_CODE_SOC, \
          please give a specific chip id to WLAN_CHIP_ID, it will override the previous value of WLAN_CHIP_ID \
          for example:
        WLAN_6759_SERIES := 6758 6775 6771
        WLAN_6755_SERIES := 6757 6763 6739
        ifneq ($(filter $(WLAN_6755_SERIES), $(WLAN_CHIP_ID)),)
            WLAN_CHIP_ID := 6755
        else ifneq ($(filter $(WLAN_6759_SERIES), $(WLAN_CHIP_ID)),)
            WLAN_CHIP_ID := 6759
        endif
        ifneq ($(wildcard $(wlan_patch_folder)/WIFI_RAM_CODE_$(WLAN_CHIP_ID)),)
            MY_SRC_FILE := WIFI_RAM_CODE_$(WLAN_CHIP_ID)
        endif
    endif
endif

ifneq ($(strip $(MY_SRC_FILE)),)
    PRODUCT_COPY_FILES += $(wlan_patch_folder)/$(MY_SRC_FILE):$(TARGET_COPY_OUT_VENDOR)/firmware/$(MY_SRC_FILE)
else
    $(error no firmware for project=$(MTK_TARGET_PROJECT), combo_chip=$(MTK_COMBO_CHIP), WLAN_CHIP_ID=$(WLAN_CHIP_ID))
endif

ifeq ($(strip $(MTK_COMBO_CHIP)), MT6632)
    MY_SRC_FILE := WIFI_RAM_CODE2_$(strip $(MTK_COMBO_CHIP))
    PRODUCT_COPY_FILES += $(wlan_patch_folder)/$(MY_SRC_FILE):$(TARGET_COPY_OUT_VENDOR)/firmware/$(MY_SRC_FILE)
endif

ifeq ($(strip $(MTK_COMBO_CHIP)), MT7668)
    MY_SRC_FILE := WIFI_RAM_CODE2_SDIO_$(strip $(MTK_COMBO_CHIP)).bin
    PRODUCT_COPY_FILES += $(wlan_patch_folder)/$(MY_SRC_FILE):$(TARGET_COPY_OUT_VENDOR)/firmware/$(MY_SRC_FILE)
    PRODUCT_COPY_FILES += $(wlan_drv_config_folder)/mt7668_wifi.cfg:$(TARGET_COPY_OUT_VENDOR)/firmware/wifi.cfg:mtk
    PRODUCT_COPY_FILES += $(wlan_drv_config_folder)/TxPwrLimit_MT76x8.dat:$(TARGET_COPY_OUT_VENDOR)/firmware/TxPwrLimit_MT76x8.dat:mtk
    PRODUCT_COPY_FILES += $(wlan_drv_config_folder)/EEPROM_MT7668.bin:$(TARGET_COPY_OUT_VENDOR)/firmware/EEPROM_MT7668.bin:mtk
endif

ifeq ($(MTK_TC10_FEATURE),yes)
    PRODUCT_PACKAGES += WIFI
endif

# WiFi HAL for wifi hotspot manager
PRODUCT_PACKAGES += vendor.mediatek.hardware.wifi.hostapd@1.0-impl
PRODUCT_PACKAGES += vendor.mediatek.hardware.wifi.hostapd@1.0-service

# for decoupled kernel object (.ko) of wifi driver
MT6631_CHIPS := CONSYS_6797 CONSYS_6759 CONSYS_6758 CONSYS_6775 CONSYS_6771

ifneq ($(filter MT6630, $(MTK_COMBO_CHIP)),)
PRODUCT_PACKAGES += wlan_drv_gen3.ko
PRODUCT_PROPERTY_OVERRIDES += ro.wlan.gen=gen3
endif

ifneq ($(filter MT6632, $(MTK_COMBO_CHIP)),)
PRODUCT_PACKAGES += wlan_drv_gen4.ko
PRODUCT_PROPERTY_OVERRIDES += ro.wlan.gen=gen4
endif

ifneq ($(filter MT7668, $(MTK_COMBO_CHIP)),)
PRODUCT_PACKAGES += wlan_drv_gen4_mt7668.ko
PRODUCT_PROPERTY_OVERRIDES += ro.wlan.gen=gen4_mt7668
endif

ifneq ($(filter $(MT6631_CHIPS), $(MTK_COMBO_CHIP)),)
PRODUCT_PACKAGES += wlan_drv_gen3.ko
PRODUCT_PROPERTY_OVERRIDES += ro.wlan.gen=gen3
else ifneq ($(filter CONSYS_%, $(MTK_COMBO_CHIP)),)
PRODUCT_PACKAGES += wlan_drv_gen2.ko
PRODUCT_PROPERTY_OVERRIDES += ro.wlan.gen=gen2
endif

ifeq ($(filter MT7668, $(MTK_COMBO_CHIP)),)
PRODUCT_PACKAGES += wmt_chrdev_wifi.ko
endif