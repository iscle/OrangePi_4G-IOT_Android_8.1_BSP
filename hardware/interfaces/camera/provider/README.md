## Camera Provider HAL ##
---

## Overview: ##

The camera.provider HAL is used by the Android camera service to discover,
query, and open individual camera devices.

It also allows for direct control of the flash unit of camera devices that have
one, for turning on/off torch mode.

More complete information about the Android camera HAL and subsystem can be found at
[source.android.com](http://source.android.com/devices/camera/index.html).

## Version history: ##

## types.hal: ##

### @0.0:

Common enum and struct definitions for all camera HAL interfaces. Does not
define any interfaces of its own.

## ICameraProvider.hal: ##

### @2.4:

First HIDL version of the camera provider HAL, closely matching the feature set
and operation of the pre-HIDL camera HAL module v2.4.

## ICameraProviderCallback.hal: ##

### @2.4:

First HIDL version of the camara provider HAL callback interface, closely
matching the feature set and operation of the pre-HIDL camera HAL module
callbacks v2.4.
