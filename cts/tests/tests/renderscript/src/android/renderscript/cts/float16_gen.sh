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

LIBDIR=$ANDROID_HOST_OUT/lib64
CLANG=$ANDROID_BUILD_TOP/prebuilts/clang/linux-x86/host/3.6/bin/clang

TMPDIR=`mktemp -d /tmp/float16_gen.XXXXXXXX`
TMPFILE=$TMPDIR/tmp.java
OUTFILE=$TMPDIR/Float16TestData.java
EXECUTABLE=$TMPDIR/float16_gen

$CLANG -Wl,-rpath=$LIBDIR -L $LIBDIR -lcompiler_rt -m64 float16_gen.c -o $EXECUTABLE
$EXECUTABLE > $TMPFILE
clang-format -style='{ColumnLimit: 80}' $TMPFILE > $OUTFILE
cp $OUTFILE .

rm -rf $TMPDIR
