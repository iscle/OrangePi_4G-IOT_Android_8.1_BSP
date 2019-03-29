#!/bin/sh
# Copyright (C) 2012 The Android Open Source Project
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
adb_options=" "
max_resolution=3
if [ $# -eq 0 ]; then
  echo "assuming default resolution"
elif [ "$1" = "-s" ]; then
  adb_options=""$1" "$2""
elif [ "$1" = "720x480" ]; then
  max_resolution=1
elif [ "$1" = "1280x720" ]; then
  max_resolution=2
elif [ "$1" = "1920x1080" ]; then
  max_resolution=3
elif [ "$1" = "all" ]; then
  max_resolution=3
else
  echo "Usage: copy_media.sh [720x480|1280x720|1920x1080] [-s serial]"
  echo "  for testing up to 1280x720, copy_media.sh 1280x720"
  echo "  default resolution, when no argument is specified, is 1920x1080"
  echo "  copy_media.sh all will copy all the files."
  exit
fi

if [ $# -gt 2 ]; then
  echo "adb option exists"
  adb_options=""$2" "$3""
fi

echo "max resolution is $1"
echo "adb options "$adb_options""

if [ $max_resolution -ge 3 ]; then
  echo "copying 1920x1080"
  adb $adb_options push bbb_short/1920x1080 /sdcard/test/bbb_short/1920x1080
  adb $adb_options push bbb_full/1920x1080 /sdcard/test/bbb_full/1920x1080
fi

if [ $max_resolution -ge 2 ]; then
  echo "copying 1280x720"
  adb $adb_options push bbb_short/1280x720 /sdcard/test/bbb_short/1280x720
  adb $adb_options push bbb_full/1280x720 /sdcard/test/bbb_full/1280x720
fi

if [ $max_resolution -ge 1 ]; then
  echo "copying 720x480"
  adb $adb_options push bbb_short/720x480 /sdcard/test/bbb_short/720x480
  adb $adb_options push bbb_full/720x480 /sdcard/test/bbb_full/720x480
fi

echo "copying all others"
adb $adb_options push bbb_short/176x144 /sdcard/test/bbb_short/176x144
adb $adb_options push bbb_full/176x144 /sdcard/test/bbb_full/176x144
adb $adb_options push bbb_short/480x360 /sdcard/test/bbb_short/480x360
adb $adb_options push bbb_full/480x360 /sdcard/test/bbb_full/480x360
