#From O, project with less than 1G memory, must set prop ro.config.low_ram=true
PRODUCT_PROPERTY_OVERRIDES += ro.mtk_config_max_dram_size=$(CUSTOM_CONFIG_MAX_DRAM_SIZE)
ifneq (yes,$(strip $(MTK_BASIC_PACKAGE)))
    ifeq (yes,$(strip $(MTK_GMO_ROM_OPTIMIZE)))
        DEVICE_PACKAGE_OVERLAYS += device/mediatek/common/overlay/slim_rom
    endif
endif
ifeq (yes,$(strip $(MTK_GMO_RAM_OPTIMIZE)))
    PRODUCT_PROPERTY_OVERRIDES += ro.mtk_gmo_ram_optimize=1
    PRODUCT_COPY_FILES += device/mediatek/common/fstab.enableswap_ago:root/fstab.enableswap
    PRODUCT_PACKAGES += Launcher3Go
    ifeq (0x20000000,$(strip $(CUSTOM_CONFIG_MAX_DRAM_SIZE)))
        ifneq ($(filter yes,$(BUILD_AGO_GMS) $(MTK_GMO_RAM_OPTIMIZE)),)
            $(call inherit-product, $(SRC_TARGET_DIR)/product/go_defaults_512.mk)
            PRODUCT_COPY_FILES += device/mediatek/common/ago/init/init.ago_512.rc:$(MTK_TARGET_VENDOR_RC)/init.ago.rc
        endif
    endif

    ifeq (0x40000000,$(strip $(CUSTOM_CONFIG_MAX_DRAM_SIZE)))
        ifneq ($(filter yes,$(BUILD_AGO_GMS) $(MTK_GMO_RAM_OPTIMIZE)),)
            $(call inherit-product, $(SRC_TARGET_DIR)/product/go_defaults.mk)
        endif
        PRODUCT_COPY_FILES += device/mediatek/common/ago/init/init.ago_default.rc:$(MTK_TARGET_VENDOR_RC)/init.ago.rc
    endif
    ifeq ($(strip $(MTK_K64_SUPPORT)), no)
        PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.zygote=zygote32
    endif
    DEVICE_PACKAGE_OVERLAYS += device/mediatek/common/overlay/ago
    DEVICE_PACKAGE_OVERLAYS += device/mediatek/common/overlay/slim_ram
    ifneq ($(wildcard vendor/mediatek/proprietary/packages/apps/SystemUI/Android.mk),)
        PRODUCT_ENFORCE_RRO_TARGETS += MtkSystemUI
    else
        PRODUCT_ENFORCE_RRO_TARGETS += SystemUI
    endif

    # For overriding AOSP Files application
    ifeq (yes,$(strip $(BUILD_AGO_GMS)))
        ifneq ($(wildcard vendor/mediatek/proprietary/packages/apps/DocumentsUI/Android.mk),)
            PRODUCT_ENFORCE_RRO_TARGETS += MtkDocumentsUI
        else
            PRODUCT_ENFORCE_RRO_TARGETS += DocumentsUI
        endif
    endif

    $(call inherit-product-if-exists, frameworks/base/data/sounds/AudioPackageGo.mk)

    # HWUI
    PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.hwui.path_cache_size=0
    PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.hwui.text_small_cache_width=512
    PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.hwui.text_small_cache_height=256
    PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.hwui.disable_asset_atlas=true

    # Disable fast starting window in GMO project
    PRODUCT_DEFAULT_PROPERTY_OVERRIDES += ro.mtk_perf_fast_start_win=0

    #Images for LCD test in factory mode
    PRODUCT_COPY_FILES += vendor/mediatek/proprietary/custom/common/factory/res/images/lcd_test_00_gmo.png:$(TARGET_COPY_OUT_VENDOR)/res/images/lcd_test_00.png:mtk
    PRODUCT_COPY_FILES += vendor/mediatek/proprietary/custom/common/factory/res/images/lcd_test_01_gmo.png:$(TARGET_COPY_OUT_VENDOR)/res/images/lcd_test_01.png:mtk
    PRODUCT_COPY_FILES += vendor/mediatek/proprietary/custom/common/factory/res/images/lcd_test_02_gmo.png:$(TARGET_COPY_OUT_VENDOR)/res/images/lcd_test_02.png:mtk
else
    ifeq ($(strip $(MTK_LIVEWALLPAPER_APP)), yes)
        PRODUCT_COPY_FILES += packages/wallpapers/LivePicker/android.software.live_wallpaper.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.live_wallpaper.xml
    endif
    PRODUCT_COPY_FILES += device/mediatek/common/fstab.enableswap:root/fstab.enableswap
    PRODUCT_COPY_FILES += device/mediatek/common/ago/init/init.ago_default.rc:$(MTK_TARGET_VENDOR_RC)/init.ago.rc
    # Add the 1GB overlays (to enable pinning on 1GB but not 512)
    DEVICE_PACKAGE_OVERLAYS += device/mediatek/common/overlay/ago/ago_1gb
    PRODUCT_PACKAGES += Launcher3
    # Add MtkLauncher3 to replace Launcher3 when vendor code is available
    ifneq ($(wildcard vendor/mediatek/proprietary/packages/apps/Launcher3/Android.mk),)
        PRODUCT_PACKAGES += MtkLauncher3
    endif
    $(call inherit-product-if-exists, frameworks/base/data/sounds/AllAudio.mk)
endif
