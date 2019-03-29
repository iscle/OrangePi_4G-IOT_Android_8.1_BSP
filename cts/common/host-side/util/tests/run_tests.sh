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

CTS_DIR=$(dirname ${0})/../../../..
source ${CTS_DIR}/test_defs.sh

JARS="
    compatibility-common-util-hostsidelib\
    compatibility-common-util-tests\
    compatibility-host-util\
    compatibility-host-util-tests\
    compatibility-mock-tradefed\
    compatibility-tradefed-tests"

run_tests "com.android.compatibility.common.util.HostUnitTests" "${JARS}" "${@}"

