#!/bin/bash
# Copyright (C) 2014 The Android Open Source Project
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

# helper script for running the signature unit tests

checkFile() {
    if [ ! -f "$1" ]; then
        echo "Unable to locate $1"
        exit
    fi;
}

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

JAR_DIR=${ANDROID_BUILD_TOP}/out/host/$OS/framework
JARS="tradefed.jar hosttestlib.jar signature-hostside.jar signature-tests.jar"

for JAR in $JARS; do
    checkFile ${JAR_DIR}/${JAR}
    JAR_PATH=${JAR_PATH}:${JAR_DIR}/${JAR}
done

java $RDBG_FLAG \
  -cp ${JAR_PATH} com.android.tradefed.command.Console run singleCommand host -n --class android.signature.cts.tests.AllTests "$@"

