#!/bin/bash -ex

if [ -z "${OUT_DIR}" ]; then
    echo Must set OUT_DIR
    exit 1
fi

TOP=$(pwd)

source build/envsetup.sh
PLATFORM_SDK_VERSION=$(get_build_var PLATFORM_SDK_VERSION)

SOONG_OUT=${OUT_DIR}/soong
SOONG_NDK_OUT=${OUT_DIR}/soong/ndk
rm -rf ${SOONG_OUT}
mkdir -p ${SOONG_OUT}
cat > ${SOONG_OUT}/soong.config << EOF
{
    "Ndk_abis": true,
    "Platform_sdk_version": ${PLATFORM_SDK_VERSION}
}
EOF
BUILDDIR=${SOONG_OUT} ./bootstrap.bash
${SOONG_OUT}/soong ${SOONG_OUT}/ndk.timestamp

if [ -n "${DIST_DIR}" ]; then
    mkdir -p ${DIST_DIR} || true
    tar cjf ${DIST_DIR}/ndk_platform.tar.bz2 -C ${SOONG_OUT} ndk
fi
