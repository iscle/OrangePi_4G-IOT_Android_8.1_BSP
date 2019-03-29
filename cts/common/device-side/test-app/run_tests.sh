#!/bin/bash

# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

CTS_DIR=$(dirname ${0})/../../..
source ${CTS_DIR}/test_defs.sh

if [ `adb devices | wc -l` -lt 3 ]; then
    echo "NO DEVICES/EMULATORS AVAILABLE. CONNECT ONE."
    exit 1
fi

# Take the last serial in the list of devices
SERIAL=`adb devices | egrep -o "^\w+" | tail -n1`
if [ -z ${SERIAL} ]; then
    echo "FAILED TO GET ADB DEVICE SERIAL FOR TEST. EXITING"
    exit 1
fi

echo "Running device side tests on device: ${SERIAL}"

APK=${ANDROID_PRODUCT_OUT}/data/app/CompatibilityTestApp/CompatibilityTestApp.apk
checkFile ${APK}

COMMON_PACKAGE=com.android.compatibility.common
RUNNER=android.support.test.runner.AndroidJUnitRunner
# TODO [2015-12-09 kalle] Fail & exit on failing install?
adb -s ${SERIAL} install -r -g ${APK}
build_jar_path ${JAR_DIR} "${JARS}"
java $RDBG_FLAG -cp ${JAR_PATH} ${TF_CONSOLE} run singleCommand instrument --serial ${SERIAL} --package ${COMMON_PACKAGE} --runner ${RUNNER}
adb -s ${SERIAL} uninstall ${COMMON_PACKAGE}
