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

# Helper script for running unit tests for compatibility libraries

CTS_DIR=$(dirname ${0})
source ${CTS_DIR}/test_defs.sh

echo
echo "---- BUILD ---- "
echo

# check if in Android build env
if [ ! -z ${ANDROID_BUILD_TOP} ]; then
    HOST=`uname`
    if [ "$HOST" == "Linux" ]; then
        OS="linux-x86"
    elif [ "$HOST" == "Darwin" ]; then
        OS="darwin-x86"
    else
        echo "Unrecognized OS"
        exit
    fi;
fi;

BUILD_TARGETS="
    compatibility-common-util-tests\
    compatibility-host-util-tests\
    compatibility-device-util-tests\
    compatibility-tradefed-tests\
    cts-tradefed-tests\
    compatibility-device-info-tests\
    compatibility-manifest-generator-tests
    compatibility-host-media-preconditions-tests\
    CompatibilityTestApp"

pushd ${CTS_DIR}/..
make ${BUILD_TARGETS} -j32
BUILD_STATUS=$?
popd
if [ "${BUILD_STATUS}" != "0" ]; then
    echo "BUILD FAILED - EXIT"
    exit 1;
fi;


echo
echo "---- DEVICE-SIDE TESTS ---- "
echo

${CTS_DIR}/common/device-side/test-app/run_tests.sh

echo
echo "---- HOST TESTS ---- "
echo

############### Run the host side tests ###############
${CTS_DIR}/common/host-side/tradefed/tests/run_tests.sh
${CTS_DIR}/common/host-side/manifest-generator/tests/run_tests.sh
${CTS_DIR}/common/host-side/util/tests/run_tests.sh
${CTS_DIR}/common/util/tests/run_tests.sh

${CTS_DIR}/tools/cts-tradefed/tests/run_tests.sh

${CTS_DIR}/tests/tests/mediastress/preconditions/tests/run_tests.sh
