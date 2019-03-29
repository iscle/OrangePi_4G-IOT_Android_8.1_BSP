AARCH64_TOOLCHAIN=GCC49
EDK2_DIR=$(ANDROID_BUILD_TOP)/device/linaro/bootloader/edk2
UEFI_TOOLS_DIR=$(ANDROID_BUILD_TOP)/device/linaro/hikey/uefi-tools
ATF_DIR=$(ANDROID_BUILD_TOP)/device/linaro/bootloader/arm-trusted-firmware
PRODUCT_OUT?=out/target/product/hikey
DIST_DIR?=$(ANDROID_BUILD_TOP)/out/dist

ifdef DEBUG
	TARGET = DEBUG
else
	TARGET = RELEASE
endif

all: $(DIST_DIR)/fip.bin $(DIST_DIR)/l-loader.bin

$(DIST_DIR)/fip.bin:
	cd $(EDK2_DIR) && \
	rm -rf Conf/tools_def.txt Conf/BuildEnv.sh  Conf/build_rule.txt Conf/target.txt  Conf/tools_def.txt && \
	export CROSS_COMPILE_32=arm-linux-androideabi- && \
	export CROSS_COMPILE_64=aarch64-linux-android- && \
	rm -rf OpenPlatformPkg && \
	ln -sf  $(EDK2_DIR)/../OpenPlatformPkg OpenPlatformPkg && \
	rm -rf $(EDK2_DIR)/Build/  && \
	mkdir -p $(EDK2_DIR)/Build/ && \
	mkdir -p $(DIST_DIR) && \
	mkdir -p $(ANDROID_BUILD_TOP)/$(PRODUCT_OUT)/obj/uefi && \
	ln -sf $(ANDROID_BUILD_TOP)/$(PRODUCT_OUT)/obj/uefi $(EDK2_DIR)/Build/HiKey && \
	$(UEFI_TOOLS_DIR)/uefi-build.sh -b $(TARGET) -D EDK2_OUT_DIR=$(ANDROID_BUILD_TOP)/$(PRODUCT_OUT)/obj/uefi -a $(ATF_DIR) hikey && \
	cp $(EDK2_DIR)/Build/HiKey/$(TARGET)_GCC49/FV/fip.bin $(DIST_DIR)/

$(DIST_DIR)/l-loader.bin: $(DIST_DIR)/fip.bin
	cd $(ANDROID_BUILD_TOP)/device/linaro/hikey/l-loader && \
	ln -sf $(EDK2_DIR)/Build/HiKey/$(TARGET)_GCC49/FV//bl1.bin && \
	make CROSS_COMPILE=arm-linux-androideabi- l-loader.bin && \
	mv l-loader.bin $(DIST_DIR)/

clean:
	rm -rf $(DIST_DIR)/fip.bin $(DIST_DIR)/l-loader.bin

