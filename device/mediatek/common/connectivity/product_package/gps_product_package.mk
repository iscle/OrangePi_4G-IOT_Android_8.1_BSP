# GPS Configuration
PRODUCT_PACKAGES += mnld
PRODUCT_PACKAGES += gps.mt6735
PRODUCT_PACKAGES += gps.mt6735m
PRODUCT_PACKAGES += gps.mt6737t
PRODUCT_PACKAGES += gps.mt6737m
PRODUCT_PACKAGES += gps.mt6753
PRODUCT_PACKAGES += gps.mt6570
PRODUCT_PACKAGES += gps.mt6572
PRODUCT_PACKAGES += gps.mt6580
PRODUCT_PACKAGES += gps.mt6582
PRODUCT_PACKAGES += gps.mt6592
PRODUCT_PACKAGES += gps.mt6752
PRODUCT_PACKAGES += gps.mt6755
PRODUCT_PACKAGES += gps.mt6750
PRODUCT_PACKAGES += gps.mt6795
PRODUCT_PACKAGES += gps.mt6797
PRODUCT_PACKAGES += gps.mt7623
PRODUCT_PACKAGES += gps.mt8127
PRODUCT_PACKAGES += gps.mt8163
PRODUCT_PACKAGES += gps.mt8173
PRODUCT_PACKAGES += gps.mt6757
PRODUCT_PACKAGES += gps.mt6799
PRODUCT_PACKAGES += gps.mt6759
PRODUCT_PACKAGES += gps.mt6763
PRODUCT_PACKAGES += gps.mt6758
PRODUCT_PACKAGES += gps.mt6739
PRODUCT_PACKAGES += gps.mt6771
PRODUCT_PACKAGES += gps.mt6775
PRODUCT_PACKAGES += flp.mt6735
PRODUCT_PACKAGES += flp.mt6735m
PRODUCT_PACKAGES += flp.mt6737t
PRODUCT_PACKAGES += flp.mt6737m
PRODUCT_PACKAGES += flp.mt6753
PRODUCT_PACKAGES += flp.mt6570
PRODUCT_PACKAGES += flp.mt6572
PRODUCT_PACKAGES += flp.mt6580
PRODUCT_PACKAGES += flp.mt6582
PRODUCT_PACKAGES += flp.mt6592
PRODUCT_PACKAGES += flp.mt6752
PRODUCT_PACKAGES += flp.mt6755
PRODUCT_PACKAGES += flp.mt6750
PRODUCT_PACKAGES += flp.mt6795
PRODUCT_PACKAGES += flp.mt6797
PRODUCT_PACKAGES += flp.mt7623
PRODUCT_PACKAGES += flp.mt8127
PRODUCT_PACKAGES += flp.mt8163
PRODUCT_PACKAGES += flp.mt8173
PRODUCT_PACKAGES += flp.mt6757
PRODUCT_PACKAGES += flp.mt6799
PRODUCT_PACKAGES += flp.mt6759
PRODUCT_PACKAGES += flp.mt6763
PRODUCT_PACKAGES += flp.mt6758
PRODUCT_PACKAGES += flp.mt6739
PRODUCT_PACKAGES += flp.mt6771
PRODUCT_PACKAGES += flp.mt6775

PRODUCT_PACKAGES += gps_drv.ko

ifeq ($(strip $(MTK_GPS_SUPPORT)), yes)
  DEVICE_MANIFEST_FILE += device/mediatek/common/project_manifest/manifest_gnss.xml
  PRODUCT_PACKAGES += vendor.mediatek.hardware.gnss@1.1-impl
  ifneq ($(strip $(MTK_HIDL_PROCESS_CONSOLIDATION_ENABLED)), yes)
    PRODUCT_PACKAGES += vendor.mediatek.hardware.gnss@1.1-service
  endif
endif
ifeq ($(strip $(MTK_AGPS_APP)), yes)
  PRODUCT_PACKAGES += LocationEM2 \
                      mtk_agpsd \
                      cacerts_supl \
                      AutoDialer \
                      LPPeService \
                      NlpService
  ifeq ($(strip $(RAT_CONFIG_C2K_SUPPORT)),yes)
    PRODUCT_PACKAGES += \
          libviagpsrpc \
          librpc
  endif
endif

ifndef MTK_AGPS_CONF_XML_SRC
  ifeq (OP12,$(word 1,$(subst _,$(space),$(OPTR_SPEC_SEG_DEF))))
    MTK_AGPS_CONF_XML_SRC := agps_profiles_conf2_OP12.xml
  else
    MTK_AGPS_CONF_XML_SRC := agps_profiles_conf2.xml
  endif
endif
PRODUCT_COPY_FILES += device/mediatek/common/agps/$(MTK_AGPS_CONF_XML_SRC):$(TARGET_COPY_OUT_VENDOR)/etc/agps_profiles_conf2.xml:mtk

# Mtk Nlp support
ifeq ($(strip $(MTK_NLP_SUPPORT)), yes)
  PRODUCT_PACKAGES += MtkNlp
endif

# Add for Hardware Fused Location Related Modules
PRODUCT_PACKAGES += slpd
PRODUCT_PACKAGES += lbs_hidl_service-impl
ifneq ($(strip $(MTK_HIDL_PROCESS_CONSOLIDATION_ENABLED)), yes)
  PRODUCT_PACKAGES += lbs_hidl_service
endif
ifneq ($(TARGET_BUILD_VARIANT), user)
  ifneq ($(strip $(MTK_GMO_RAM_OPTIMIZE)), yes)
    PRODUCT_PACKAGES += FlpEM2
  endif
endif
PRODUCT_COPY_FILES += device/mediatek/common/slp/slp_conf:$(TARGET_COPY_OUT_VENDOR)/etc/slp_conf:mtk

# GPS-PE offload to support Hardware Geofence and Fused Location
PRODUCT_PACKAGES += MNL.bin
