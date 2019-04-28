#!/usr/bin/env bash

# Copyright 2017, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

function setup_paths {
    if [ -z "${ANDROID_BUILD_TOP}" ]; then
        echo "Could not resolve ANDROID_BUILD_TOP. Make sure you run source build/envsetup.sh and lunch <target> first."
        exit
    fi

    ANDROID_CLASSES="${ANDROID_BUILD_TOP}/out/target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes"
    if [ ! -d "${ANDROID_CLASSES}" ]; then
        echo "Could not find folder ${ANDROID_CLASSES}. Make sure you compile ManagedProvisioning first"
        exit
    fi

    MP="${ANDROID_BUILD_TOP}/packages/apps/ManagedProvisioning"
    TOOLS_JAVA="${MP}/tools/java"
    CP="${TOOLS_JAVA}:${MP}/src:${ANDROID_CLASSES}"
    OUT_PATH="${MP}/swiper-themes.xml"
}

setup_paths

pushd "${TOOLS_JAVA}" > /dev/null

echo "compiling.."
javac -cp "${CP}" com/android/managedprovisioning/tools/anim/SwiperThemeGenerator.java

echo "generating themes.."
java  -cp "${CP}" com.android.managedprovisioning.tools.anim.SwiperThemeGenerator "${OUT_PATH}"

echo "output stored under: ${OUT_PATH}"
echo "done"

popd > /dev/null