#!/usr/bin/env bash

cd "`dirname $0`"

die () {
	set +x # Turn off printing commands
	echo ""
	echo " *** fatal error: $*"
	exit 1
}

if [ -z $ANDROID_BUILD_TOP ]; then
  echo "You need to source and lunch before you can use this script"
  exit 1
fi

./prepdevice.sh || die "Unable to prepare device"

sleep 2

echo "Running tests. . ."

set -x # print commands

adb shell killall wpantund 2> /dev/null

adb shell wpantund -s 'system:ot-ncp\ 1' -o Config:Daemon:ExternalNetifManagement 1 &
WPANTUND_PID=$!
trap "kill -HUP $WPANTUND_PID 2> /dev/null" EXIT INT TERM

sleep 2

kill -0 $WPANTUND_PID || die "wpantund failed to start"

sleep 2

adb shell lowpanctl status || die
adb shell lowpanctl form blahnet || die
adb shell lowpanctl status || die
adb shell ifconfig wpan0 || die

set +x # Turn off printing commands

echo Finished.

