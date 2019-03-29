#!/bin/bash

#
# Copyright (C) 2016 The Android Open Source Project
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

# Exit in error if we use an undefined variable (i.e. commit a typo).
set -u

usage () { #show usage and bail out
	echo "USAGE:" >&2
	echo "    $1 <PRIV_KEY_FILE> <PUB_KEY_FILE> nanohub.update.bin" >&2
	exit 1
}

if [ $# != 3 ] ; then
usage $0
fi

priv=$1
pub=$2
raw_image=$3

# make signed image with header; suitable for BL
# to be consumed by BL it has to be named nanohub.kernel.signed
nanoapp_postprocess -n os -r ${raw_image} ${raw_image}.oshdr
nanoapp_sign -s -e ${priv} -m ${pub} -r ${raw_image}.oshdr nanohub.kernel.signed

# embed this image inside nanoapp container

nanoapp_postprocess -n os nanohub.kernel.signed ${raw_image}.napp
