#!/bin/bash

if [ ! -d hardware/interfaces ] ; then
  echo "Where is hardware/interfaces?";
  exit 1;
fi

if [ ! -d system/libhidl/transport ] ; then
  echo "Where is system/libhidl/transport?";
  exit 1;
fi

echo "WARNING: This script changes files in many places."

# These files only exist to facilitate the easy transition to hidl.

options="-Lexport-header \
        -randroid.hardware:hardware/interfaces \
        -randroid.hidl:system/libhidl/transport"

# hardware/libhardware
hidl-gen $options \
         -o hardware/libhardware/include/hardware/sensors-base.h \
         android.hardware.sensors@1.0
hidl-gen $options \
         -o hardware/libhardware/include/hardware/nfc-base.h \
         android.hardware.nfc@1.0
hidl-gen $options \
         -o hardware/libhardware/include/hardware/gnss-base.h \
         android.hardware.gnss@1.0

# system/core
hidl-gen $options \
         -o system/core/include/system/graphics-base.h \
         android.hardware.graphics.common@1.0

# system/media
hidl-gen $options \
         -o system/media/audio/include/system/audio-base.h \
         android.hardware.audio.common@2.0
hidl-gen $options \
         -o system/media/audio/include/system/audio_effect-base.h \
         android.hardware.audio.effect@2.0
