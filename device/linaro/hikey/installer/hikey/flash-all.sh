#!/bin/bash
if [ $# -eq 0 ]
  then
    echo "Provide the right /dev/ttyUSBX specific to recovery device"
    exit
fi

if [ ! -e "${1}" ]
  then
    echo "device: ${1} does not exist"
    exit
fi
DEVICE_PORT="${1}"
PTABLE=ptable-aosp-8g.img
if [ $# -gt 1 ]
  then
    if [ "${2}" == '4g' ]
      then
        PTABLE=ptable-aosp-4g.img
    fi
fi

INSTALLER_DIR="`dirname ${0}`"
FIRMWARE_DIR="${INSTALLER_DIR}"

# for cases that not run "lunch hikey-userdebug"
if [ -z "${ANDROID_BUILD_TOP}" ]; then
    ANDROID_BUILD_TOP=${INSTALLER_DIR}/../../../../../
    ANDROID_PRODUCT_OUT="${ANDROID_BUILD_TOP}/out/target/product/hikey"
fi

if [ -z "${DIST_DIR}" ]; then
    DIST_DIR="${ANDROID_BUILD_TOP}"/out/dist
fi

#get out directory path
while [ $# -ne 0 ]; do
    case "${1}" in
        --out) OUT_IMGDIR=${2};shift;;
        --use-compiled-binaries) FIRMWARE_DIR="${DIST_DIR}";shift;;
    esac
    shift
done

if [[ "${FIRMWARE_DIR}" == "${DIST_DIR}" && ! -e "${DIST_DIR}"/fip.bin && ! -e "${DIST_DIR}"/l-loader.bin ]]; then
    echo "No binaries found at ${DIST_DIR}. Please build the bootloader first"
    exit
fi

if [ -z "${OUT_IMGDIR}" ]; then
    if [ ! -z "${ANDROID_PRODUCT_OUT}" ]; then
        OUT_IMGDIR="${ANDROID_PRODUCT_OUT}"
    fi
fi

if [ ! -d "${OUT_IMGDIR}" ]; then
    echo "error in locating out directory, check if it exist"
    exit
fi

echo "android out dir:${OUT_IMGDIR}"

sudo python "${INSTALLER_DIR}"/hisi-idt.py --img1="${FIRMWARE_DIR}"/l-loader.bin -d "${DEVICE_PORT}"
sleep 3
# set a unique serial number
serialno=`fastboot getvar serialno 2>&1 > /dev/null`
if [ "${serialno:10:6}" == "(null)" ]; then
    fastboot oem serialno
else
    if [ "${serialno:10:15}" == "0123456789abcde" ]; then
        fastboot oem serialno
    fi
fi
fastboot flash ptable "${INSTALLER_DIR}"/"${PTABLE}"
fastboot flash fastboot "${FIRMWARE_DIR}"/fip.bin
fastboot flash nvme "${INSTALLER_DIR}"/nvme.img
fastboot flash boot "${OUT_IMGDIR}"/boot.img
fastboot flash system "${OUT_IMGDIR}"/system.img
fastboot flash cache "${OUT_IMGDIR}"/cache.img
fastboot flash userdata "${OUT_IMGDIR}"/userdata.img
