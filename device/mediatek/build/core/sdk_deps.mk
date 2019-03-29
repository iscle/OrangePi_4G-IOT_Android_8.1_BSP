ifeq ($(is_sdk_build),true)
$(info including $(sdk_dep_file)...)

# override build/core/Makefile
ifeq ($(strip $(TARGET_PLATFORM_VERSION)),OPM1)
PRODUCTS.$(INTERNAL_PRODUCT).PRODUCT_SDK_ATREE_FILES := device/mediatek/build/core/sdk_o1.atree
else
PRODUCTS.$(INTERNAL_PRODUCT).PRODUCT_SDK_ATREE_FILES := device/mediatek/build/core/sdk.atree
endif
sdk_dep_file := $(sdk_dir)/sdk_deps_aosp.mk
-include $(sdk_dep_file)

ifeq ($(strip $(ATREE_FILES)),)
ATREE_FILES := \
	$(ALL_DOCS) \
	$(ALL_SDK_FILES)

# development/build/sdk-linux-x86.atree
ATREE_FILES += \
	$(HOST_OUT)/lib64/libLLVM.so \
	$(HOST_OUT)/lib64/libbcc.so \
	$(HOST_OUT)/lib64/libbcinfo.so \
	$(HOST_OUT)/lib64/libclang.so \
	$(HOST_OUT)/lib64/libc++.so \
	$(HOST_OUT)/lib64/libaapt2_jni.so

# development/build/sdk.atree
ATREE_FILES += \
	$(HOST_OUT)/bin/aapt \
	$(HOST_OUT)/bin/aapt2 \
	$(HOST_OUT)/bin/aidl \
	$(HOST_OUT)/bin/split-select \
	$(HOST_OUT)/bin/zipalign \
	$(HOST_OUT)/bin/llvm-rs-cc \
	$(HOST_OUT)/bin/bcc_compat \
	$(HOST_OUT)/bin/dx \
	$(HOST_OUT)/framework/dx.jar \
	$(HOST_OUT)/bin/dexdump

endif

# reset build/core/Makefile
target_notice_file_txt :=
tools_notice_file_txt :=
SYMBOLS_ZIP :=
COVERAGE_ZIP :=
INSTALLED_SYSTEMIMAGE :=
INSTALLED_USERDATAIMAGE_TARGET :=
INSTALLED_RAMDISK_TARGET :=
INSTALLED_BUILD_PROP_TARGET :=
INSTALLED_QEMU_SYSTEMIMAGE :=
INSTALLED_QEMU_VENDORIMAGE :=

$(sdk_dir)/$(sdk_name).zip: target_notice_file_txt := /dev/null
$(sdk_dir)/$(sdk_name).zip: tools_notice_file_txt := /dev/null

endif
