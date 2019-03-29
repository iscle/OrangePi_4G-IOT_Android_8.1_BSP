# Create links from /vendor to /system for AOSP without vendor builds
ifneq (,$(filter aosp_angler, $(TARGET_PRODUCT)))

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := AOSPLinks
LOCAL_MODULE_OWNER := google
LOCAL_MODULE_TAGS := optional

LOCAL_POST_INSTALL_CMD := \
    test -s vendor/huawei/angler/proprietary/prebuilt/target/product/msm8994/system/etc/izat.conf || { \
    mkdir -p $(PRODUCT_OUT)/system/etc; \
    ln -sf /vendor/etc/izat.conf $(PRODUCT_OUT)/system/etc/izat.conf; \
    ln -sf /vendor/etc/flp.conf $(PRODUCT_OUT)/system/etc/flp.conf; \
    ln -sf /vendor/etc/sap.conf $(PRODUCT_OUT)/system/etc/sap.conf; \
    ln -sf /vendor/etc/pp_calib_data_SAMSUNG_S6E3HA3X01_5P7_1440P_CMD_DUAL0.xml $(PRODUCT_OUT)/system/etc/pp_calib_data_SAMSUNG_S6E3HA3X01_5P7_1440P_CMD_DUAL0.xml; \
    mkdir -p $(PRODUCT_OUT)/system/etc/data; \
    ln -sf /vendor/etc/data/dsi_config.xml $(PRODUCT_OUT)/system/etc/data/dsi_config.xml; \
    ln -sf /vendor/etc/data/netmgr_config.xml $(PRODUCT_OUT)/system/etc/data/netmgr_config.xml; \
    mkdir -p $(PRODUCT_OUT)/system/etc/cne; \
    ln -sf /vendor/etc/cne/profile.txt $(PRODUCT_OUT)/system/etc/cne/profile.txt; \
    mkdir -p $(PRODUCT_OUT)/system/bin; \
    ln -sf /vendor/bin/imscmservice $(PRODUCT_OUT)/system/bin/imscmservice; \
    ln -sf /vendor/bin/imsdatadaemon $(PRODUCT_OUT)/system/bin/imsdatadaemon; \
    ln -sf /vendor/bin/imsqmidaemon $(PRODUCT_OUT)/system/bin/imsqmidaemon; \
    ln -sf /vendor/bin/ims_rtp_daemon $(PRODUCT_OUT)/system/bin/ims_rtp_daemon; }

include $(BUILD_PHONY_PACKAGE)

endif # aosp_angler
