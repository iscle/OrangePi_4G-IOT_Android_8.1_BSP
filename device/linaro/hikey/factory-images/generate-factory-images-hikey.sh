# Copyright 2011, 2016 The Android Open Source Project
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


DEVICE_DIR=device/linaro/hikey/
DEVICE=hikey
PRODUCT=hikey

BUILD=eng.`whoami`
BUILDNAME=`ls ${ANDROID_BUILD_TOP}/${PRODUCT}-img-${BUILD}.zip 2> /dev/null`
if [ $? -ne 0 ]; then
  VERSION=linaro-`date +"%Y.%m.%d"`
else
  BUILDNAME=`ls ${ANDROID_BUILD_TOP}/${PRODUCT}-img-*.zip 2> /dev/null`
  BUILD=`basename ${BUILDNAME} | cut -f3 -d'-' | cut -f1 -d'.'`
  VERSION=$BUILD
fi

# Prepare the staging directory
rm -rf tmp
mkdir -p tmp/$PRODUCT-$VERSION

# copy over flashing tool, and bootloader binaries
cp $ANDROID_BUILD_TOP/$DEVICE_DIR/installer/hikey/README tmp/$PRODUCT-$VERSION/
cp $ANDROID_BUILD_TOP/$DEVICE_DIR/installer/hikey/hisi-idt.py tmp/$PRODUCT-$VERSION/
cp $ANDROID_BUILD_TOP/$DEVICE_DIR/installer/hikey/l-loader.bin tmp/$PRODUCT-$VERSION/
cp $ANDROID_BUILD_TOP/$DEVICE_DIR/installer/hikey/ptable-aosp-8g.img tmp/$PRODUCT-$VERSION/
cp $ANDROID_BUILD_TOP/$DEVICE_DIR/installer/hikey/ptable-aosp-4g.img tmp/$PRODUCT-$VERSION/
cp $ANDROID_BUILD_TOP/$DEVICE_DIR/installer/hikey/fip.bin tmp/$PRODUCT-$VERSION/
cp $ANDROID_BUILD_TOP/$DEVICE_DIR/installer/hikey/nvme.img tmp/$PRODUCT-$VERSION/

cp ${SRCPREFIX}$PRODUCT-img-$BUILD.zip tmp/$PRODUCT-$VERSION/image-$PRODUCT-$VERSION.zip


# Write flash-all.sh
cat > tmp/$PRODUCT-$VERSION/flash-all.sh << EOF
#!/bin/bash

# Copyright 2012, 2016 The Android Open Source Project
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


if [ \$# -eq 0 ]
  then
    echo "Provide the right /dev/ttyUSBX specific to recovery device"
    exit
fi

if [ ! -e "\${1}" ]
  then
    echo "device: \${1} does not exist"
    exit
fi
DEVICE_PORT="\${1}"

PTABLE=ptable-aosp-8g.img
if [ \$# -gt 1 ]
  then
    if [ "\${2}" = '4g' ]
      then
        PTABLE=ptable-aosp-4g.img
    fi
fi

python hisi-idt.py --img1=l-loader.bin -d "\${DEVICE_PORT}"
sleep 3
# set a unique serial number
serialno=\`fastboot getvar serialno 2>&1 > /dev/null\`
if [ "\${serialno:10:6}" == "(null)" ]; then
    fastboot oem serialno
else
    if [ "\${serialno:10:15}" == "0123456789abcde" ]; then
        fastboot oem serialno
    fi
fi
fastboot flash ptable "\${PTABLE}"
fastboot flash fastboot fip.bin
fastboot flash nvme nvme.img
fastboot format userdata
fastboot format cache
fastboot update image-$PRODUCT-$VERSION.zip
EOF

chmod a+x tmp/$PRODUCT-$VERSION/flash-all.sh

# Create the distributable package
(cd tmp ; zip -r ../$PRODUCT-$VERSION-factory.zip $PRODUCT-$VERSION)
mv $PRODUCT-$VERSION-factory.zip $PRODUCT-$VERSION-factory-$(sha256sum < $PRODUCT-$VERSION-factory.zip | cut -b -8).zip

# Clean up
rm -rf tmp
