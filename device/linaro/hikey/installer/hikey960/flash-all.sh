#!/bin/bash

INSTALLER_DIR="`dirname ${0}`"

# for cases that don't run "lunch hikey960-userdebug"
if [ -z "${ANDROID_BUILD_TOP}" ]; then
    ANDROID_BUILD_TOP=${INSTALLER_DIR}/../../../../../
    ANDROID_PRODUCT_OUT="${ANDROID_BUILD_TOP}/out/target/product/hikey960"
fi

if [ ! -d "${ANDROID_PRODUCT_OUT}" ]; then
    echo "error in locating out directory, check if it exist"
    exit
fi

echo "android out dir:${ANDROID_PRODUCT_OUT}"

fastboot flash ptable "${INSTALLER_DIR}"/ptable.img
fastboot flash xloader "${INSTALLER_DIR}"/sec_xloader.img
fastboot flash fastboot "${INSTALLER_DIR}"/fastboot.img
fastboot flash nvme "${INSTALLER_DIR}"/nvme.img
fastboot flash fw_lpm3   "${INSTALLER_DIR}"/lpm3.img
fastboot flash trustfirmware   "${INSTALLER_DIR}"/bl31.bin
fastboot flash boot "${ANDROID_PRODUCT_OUT}"/boot.img
fastboot flash dts "${ANDROID_PRODUCT_OUT}"/dt.img
fastboot flash system "${ANDROID_PRODUCT_OUT}"/system.img
fastboot flash cache "${ANDROID_PRODUCT_OUT}"/cache.img
fastboot flash userdata "${ANDROID_PRODUCT_OUT}"/userdata.img
