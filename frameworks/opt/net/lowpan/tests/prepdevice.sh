#!/usr/bin/env bash

die () {
	set +x # Turn off printing commands
	echo "error: $*"
	exit 1
}

if [ -z $ANDROID_BUILD_TOP ]; then
  echo "You need to source and lunch before you can use this script"
  exit 1
fi

echo "Preparing device for LowpanService tests..."

make -j32 -C $ANDROID_BUILD_TOP -f build/core/main.mk \
	MODULES-IN-frameworks-opt-net-lowpan-service \
	MODULES-IN-frameworks-opt-net-lowpan-command \
	MODULES-IN-external-wpantund \
	MODULES-IN-external-openthread \
	|| die "Build failed"

set -x # print commands

cp ${ANDROID_BUILD_TOP}/frameworks/native/data/etc/android.hardware.lowpan.xml ${ANDROID_PRODUCT_OUT}/system/etc/permissions/android.hardware.lowpan.xml

adb root || die
adb wait-for-device || die
adb remount || die
adb shell stop || die
adb disable-verity
adb sync || die
adb shell start || die

sleep 2

echo Device is ready.

