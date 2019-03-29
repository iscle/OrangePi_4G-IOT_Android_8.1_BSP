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

infile="$1"
shift
outfile="$1"
shift

retcode=0

echo -e "\n\nChecking '$infile' for forbidden symbols\n\n" >&2

for var in "$@"
do
	look_for=$(echo "$var" | sed 's/\([^=]*\)=\(.*\)/\1/g')
	if echo "$var" | grep = >/dev/null
	then
		explanation=$(echo "$var" | sed 's/\([^=]*\)=/ /g')
	else
		explanation=""
	fi

	explanation="Forbidden function '$look_for' found. This is a build error.$explanation"


	if ${CROSS_COMPILE}nm -a "$infile" |grep -e "[0-9a-f]\{8\} [Tt] $look_for" >/dev/null
	then
		echo $explanation >&2
		retcode=-1
	fi
done

if [ $retcode -eq 0 ]
then
	echo "Symcheck found nothing bad. Proceeding" >&2
	cp "$infile" "$outfile"
fi

exit $retcode


