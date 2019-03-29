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

# Common tools for running unit tests of the compatibility libs

JAR_DIR=${ANDROID_HOST_OUT}/framework
TF_CONSOLE=com.android.tradefed.command.Console

COMMON_JARS="
    ddmlib-prebuilt\
    hosttestlib\
    tradefed"

checkFile() {
    if [ ! -f "$1" ]; then
        echo "Unable to locate $1"
        exit
    fi;
}

build_jar_path() {
    JAR_PATH=
    for JAR in ${2} ${COMMON_JARS}; do
        checkFile ${1}/${JAR}.jar
        JAR_PATH=${JAR_PATH}:${1}/${JAR}.jar
    done
}

run_tests() {
    build_jar_path "${JAR_DIR}" "${2}"
    for CLASS in ${1}; do
        java $RDBG_FLAG -cp ${JAR_PATH} ${TF_CONSOLE} run singleCommand host -n --class ${CLASS} ${3}
    done
}
