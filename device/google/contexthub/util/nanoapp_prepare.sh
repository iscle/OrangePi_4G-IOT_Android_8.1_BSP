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
	echo "    $1 <app.napp> [-e <ENCR_KEY_NUM> <ENCR_KEY_FILE>] [-s <PRIV_KEY_FILE> <PUB_KEY_FILE> [<SIG_TO_CHAIN_1> [<SIG_TO_CHAIN_2> [...]]]]" >&2
	exit 1
}

if [ $# -ge 1 ] ; then
app=${1%.napp}
shift
else
usage $0
fi

args=( $@ )

#get encryption key if it exists & encrypt app
encr_key_num=""
if [ ${#args[@]} -ge 1 ]
then
	if [[ ${args[0]} = "-e" ]]
	then
		if [ ${#args[@]} -lt 3 ]
		then
			usage $0
		fi
		encr_key_num=${args[1]}
		encr_key_file=${args[2]}
		args=("${args[@]:3}")

		if [ ! -f "$encr_key_file" ]; then
			usage $0
		fi

		nanoapp_encr -e -i "$encr_key_num" -k "$encr_key_file" "${app}.napp" "${app}.encr.napp"
		app="${app}.encr"
	fi
fi

#handle signing
if [ ${#args[@]} -ge 1 ]
then
	if [[ ${args[0]} = "-s" ]]
	then
		if [ ${#args[@]} -lt 3 ]
		then
			usage $0
		fi
		priv1=${args[1]}
		pub1=${args[2]}

		#make sure files exist
		i=1
		while [ $i -lt ${#args[@]} ]
		do
			if [ ! -f "${args[$i]}" ]; then
				usage $0
			fi
			i=$[$i+1]
		done

		nanoapp_sign -s -e "$priv1" -m "$pub1" "${app}.napp" "${app}.sign.napp"

		#append remaining chunks
		i=3
		while [ $i -lt ${#args[@]} ]
		do
			cat "${args[$i]}" >> "${app}.sign.napp"
			i=$[$i+1]
		done
	else
		usage $0
	fi
fi
