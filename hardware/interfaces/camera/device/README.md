## Camera Device HAL ##
---

## Overview: ##

The camera.device HAL interface is used by the Android camera service to operate
individual camera devices. Instances of camera.device HAL interface can be obtained
via one of the ICameraProvider::getCameraDeviceInterface_V<N>_x() methods, where N
is the major version of the camera device interface.

Obtaining the device interface does not turn on the respective camera device;
each camera device interface has an actual open() method to begin an active
camera session. Without invoking open(), the interface can be used for querying
camera static information.

More complete information about the Android camera HAL and subsystem can be found at
[source.android.com](http://source.android.com/devices/camera/index.html).

## Version history: ##

### ICameraDevice.hal@1.0:

HIDL version of the legacy camera device HAL. Intended as a shim for devices
needing to use the deprecated pre-HIDL camera device HAL v1.0.

May be used in HIDL passthrough mode for devices upgrading to the Android O
release; must be used in binderized mode for devices launching in the O release.

It is strongly recommended to not use this interface for new devices, as new
devices may not use this interface starting with the Android P release, and all
support for ICameraDevice@1.0 will be removed with the Android R release.

This HAL interface version only allows support at the LEGACY level for the
android.hardware.camera2 API.

Added in Android 8.0.

Subsidiary HALs:

#### ICameraDevice1PreviewCallback.hal@1.0:

Callback interface for obtaining, filling, and returning graphics buffers for
preview operation with the ICameraDevice@1.0 inteface.

#### ICameraDevice1Callback.hal@1.0:

Callback interface for sending events and data buffers from the HAL to the
camera service.

### ICameraDevice.hal@3.2:

HIDL version of the baseline camera device HAL, required for LIMITED or FULL
operation through the android.hardware.camera2 API.

The main HAL contains methods for static queries about the device, similar to
the HALv3-specific sections of the legacy camera module HAL. Simply obtaining an
instance of the camera device interface does not turn on the camera device.

May be used in passthrough mode for devices upgrading to the Android O release;
must be used in binderized mode for all new devices launching with Android O or
later.

The open() method actually opens the camera device for use, returning a Session
interface for operating the active camera. It takes a Callback interface as an
argument.

Added in Android 8.0.

Subsidiary HALs:

#### ICameraDevice3Session.hal@3.2:

Closely matches the features and operation of the pre-HIDL camera device HAL
v3.2, containing methods for configuring an active camera device and submitting
capture requests to it.

#### ICameraDevice3Callback.hal@3.2:

Callback interface for sending completed captures and other asynchronous events
from tehe HAL to the client.

### ICameraDevice.hal@3.3:

A minor revision to the ICameraDevice.hal@3.2.

  - Adds support for overriding the output dataspace of a stream, which was
    supported in the legacy camera HAL.

Added in Android 8.1.
