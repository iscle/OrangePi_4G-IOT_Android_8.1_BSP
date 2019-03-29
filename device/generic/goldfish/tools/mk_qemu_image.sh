#!/bin/bash

set -e

if [ "$#" -ne 1 ]; then
    echo "$0 path-to-system.img | path-to-vendor.img" >&2
    exit 1
fi

srcimg=$1
base_srcimg=`basename $srcimg`
label="${base_srcimg%.*}"
dir_name=$(dirname $srcimg)
target=${dir_name}/$label-qemu.img

dd if=/dev/zero of=$target ibs=1024k count=1
dd if=$srcimg of=$target conv=notrunc,sync ibs=1024k obs=1024k seek=1
unamestr=`uname`
if [[ "$unamestr" == 'Linux' ]]; then
curdisksize=$(stat -c %s $target)
elif [[ "$unamestr" == 'Darwin' ]]; then
curdisksize=$(stat -f %z $target)
else
echo "Cannot determine OS type, quit"
exit 1
fi

dd if=/dev/zero of=$target conv=notrunc bs=1 count=1024k seek=$curdisksize

disksize=`expr $curdisksize + 1024 \* 1024 `

end=`expr $disksize \/ 512 - 2048 - 1`
${SGDISK:-sgdisk} --clear $target
${SGDISK:-sgdisk} --new=1:2048:$end --type=1:8300 --change-name=1:$label $target
