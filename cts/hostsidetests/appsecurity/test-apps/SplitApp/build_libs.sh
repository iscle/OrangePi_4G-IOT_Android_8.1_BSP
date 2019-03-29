#!/bin/bash
#
# Copyright (C) 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

NDK_BUILD="$HOME/android-ndk-r10b/ndk-build"

# Go build everything
rm -rf libs
cd jni/
$NDK_BUILD clean
$NDK_BUILD
cd ../

for arch in `ls libs/`;
do
    (
    mkdir -p tmp/$arch/raw/lib/$arch/
    mv libs/$arch/* tmp/$arch/raw/lib/$arch/

    echo "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"
        package=\"com.android.cts.splitapp\"
        split=\"lib_${arch//[^a-zA-Z0-9_]/_}\">
    <application android:hasCode=\"false\" />
</manifest>" > tmp/$arch/AndroidManifest.xml

    cp NativeTemplate.mk tmp/$arch/Android.mk
    sed -i -r "s/ARCHARCH/$arch/" tmp/$arch/Android.mk

    )
done

echo "include \$(call all-subdir-makefiles)" > tmp/Android.mk

rm -rf libs
rm -rf obj

mv tmp libs
