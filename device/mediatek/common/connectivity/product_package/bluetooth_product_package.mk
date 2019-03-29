# Bluetooth Configuration
ifeq ($(strip $(MTK_BT_SUPPORT)), yes)
ifneq ($(filter MTK_MT76%, $(MTK_BT_CHIP)),)
  PRODUCT_PACKAGES += libbt-vendor
  PRODUCT_PACKAGES += libbluetooth_mtk
  PRODUCT_PACKAGES += boots_srv
  PRODUCT_PACKAGES += boots
  PRODUCT_PACKAGES += btmtksdio.ko
else
  PRODUCT_PACKAGES += libbt-vendor
  PRODUCT_PACKAGES += libbluetooth_mtk
  PRODUCT_PACKAGES += libbluetooth_mtk_pure
  PRODUCT_PACKAGES += libbluetoothem_mtk
  PRODUCT_PACKAGES += libbluetooth_relayer
  PRODUCT_PACKAGES += libbluetooth_hw_test
  PRODUCT_PACKAGES += autobt
  PRODUCT_PACKAGES += bt_drv.ko
endif
endif

