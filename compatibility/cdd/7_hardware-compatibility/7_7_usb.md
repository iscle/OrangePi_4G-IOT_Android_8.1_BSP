## 7.7\. USB

If device implementations have a USB port, they:

*    SHOULD support USB peripheral mode and SHOULD support USB host mode.

### 7.7.1\. USB peripheral mode

If handheld device implementations include a USB port supporting peripheral
mode, they:

*    [H-1-1] MUST implement the Android Open Accessory (AOA) API.

If device implementations include a USB port supporting peripheral mode:

*    [C-1-1] The port MUST be connectable to a USB host that has a standard
type-A or type-C USB port.
*    [C-1-2] MUST report the correct value of `iSerialNumber` in USB standard
device descriptor through `android.os.Build.SERIAL`.
*    [C-1-3]  MUST detect 1.5A and 3.0A chargers per the Type-C resistor
standard and MUST detect changes in the advertisement if they support
Type-C USB.
*    [SR] The port SHOULD use micro-B, micro-AB or Type-C USB form factor.
Existing and new Android devices are **STRONGLY RECOMMENDED to meet these
requirements** so they will be able to upgrade to the future platform releases.
*    [SR] The port SHOULD be located on the bottom of the device
(according to natural orientation) or enable software screen rotation for
all apps (including home screen), so that the display draws correctly when
the device is oriented with the port at bottom. Existing and new Android
devices are **STRONGLY RECOMMENDED to meet these requirements** so they will
be able to upgrade to future platform releases.
*    [SR] SHOULD implement support to draw 1.5 A current during HS chirp
and traffic as specified in the [USB Battery Charging specification, revision 1.2](http://www.usb.org/developers/docs/devclass_docs/BCv1.2_070312.zip).
Existing and new Android devices are **STRONGLY RECOMMENDED to meet these
requirements** so they will be able to upgrade to the future platform releases.
*    [SR] STRONGLY RECOMMENDED to not support proprietary
charging methods that modify Vbus voltage beyond default levels, or alter
sink/source roles as such may result in interoperability issues with the
chargers or devices that support the standard USB Power Delivery methods. While
this is called out as "STRONGLY RECOMMENDED", in future Android versions we
might REQUIRE all type-C devices to support full interoperability with standard
type-C chargers.
*    [SR] STRONGLY RECOMMENDED to support Power Delivery for data and
power role swapping when they support Type-C USB and USB host mode.
*    SHOULD support Power Delivery for high-voltage charging and support for
Alternate Modes such as display out.
*    SHOULD implement the Android Open Accessory (AOA) API and specification as
documented in the Android SDK documentation.

If device implementations including a USB port, implement the AOA specification,
they:

*    [C-2-1] MUST declare support for the hardware feature
[`android.hardware.usb.accessory`](http://developer.android.com/guide/topics/connectivity/usb/accessory.html).
*    [C-2-2] The USB mass storage class MUST include the string "android" at the
end of the interface description `iInterface` string of the USB mass storage
*    SHOULD NOT implement [AOAv2 audio](https://source.android.com/devices/accessories/aoa2#audio-support)
documented in the Android Open Accessory Protocol 2.0 documentation. AOAv2 audio
is deprecated as of Android version 8.0 (API level 26).


### 7.7.2\. USB host mode

If device implementations include a USB port supporting host mode, they:

*   [C-1-1] MUST implement the Android USB host API as documented in the
Android SDK and MUST declare support for the hardware feature
[`android.hardware.usb.host`](http://developer.android.com/guide/topics/connectivity/usb/host.html).
*   [C-1-2] MUST implement support to connect standard USB peripherals,
in other words, they MUST either:
   *   Have an on-device type C port or ship with cable(s) adapting an on-device
   proprietary port to a standard USB type-C port (USB Type-C device).
   *   Have an on-device type A or ship with cable(s) adapting an on-device
   proprietary port to a standard USB type-A port.
   *   Have an on-device micro-AB port, which SHOULD ship with a cable adapting
   to a standard type-A port.
*   [C-1-3] MUST NOT ship with an adapter converting from USB type A or
micro-AB ports to a type-C port (receptacle).
*   [SR] STRONGLY RECOMMENDED to implement the [USB audio class](
http://developer.android.com/reference/android/hardware/usb/UsbConstants.html#USB_CLASS_AUDIO)
as documented in the Android SDK documentation.
*   SHOULD support charging the connected USB peripheral device while in host
    mode; advertising a source current of at least 1.5A as specified in the
    Termination Parameters section of the
    [USB Type-C Cable and Connector Specification Revision 1.2](
    http://www.usb.org/developers/docs/usb_31_021517.zip) for USB Type-C
    connectors or using Charging Downstream Port(CDP) output current range as
    specified in the [USB Battery Charging specifications, revision 1.2](
    http://www.usb.org/developers/docs/devclass_docs/BCv1.2_070312.zip)
    for Micro-AB connectors.
*   SHOULD implement and support USB Type-C standards.

If device implementations include a USB port supporting host mode and the USB
audio class, they:

*    [C-2-1] MUST support the [USB HID class](https://developer.android.com/reference/android/hardware/usb/UsbConstants.html#USB_CLASS_HID)
*    [C-2-2] MUST support the detection and mapping of the following HID data
fields specified in the [USB HID Usage Tables](http://www.usb.org/developers/hidpage/Hut1_12v2.pdf)
and the [Voice Command Usage Request](http://www.usb.org/developers/hidpage/Voice_Command_Usage.pdf)
to the [`KeyEvent`](https://developer.android.com/reference/android/view/KeyEvent.html)
constants as below:
        *   Usage Page (0xC) Usage ID (0x0CD): `KEYCODE_MEDIA_PLAY_PAUSE`
        *   Usage Page (0xC) Usage ID (0x0E9): `KEYCODE_VOLUME_UP`
        *   Usage Page (0xC) Usage ID (0x0EA): `KEYCODE_VOLUME_DOWN`
        *   Usage Page (0xC) Usage ID (0x0CF): `KEYCODE_VOICE_ASSIST`


If device implementations include a USB port supporting host mode and
the Storage Access Framework (SAF), they:

*   [C-3-1] MUST recognize any remotely connected MTP (Media Transfer Protocol)
devices and make their contents accessible through the `ACTION_GET_CONTENT`,
`ACTION_OPEN_DOCUMENT`, and `ACTION_CREATE_DOCUMENT` intents. .

If device implementations include a USB port supporting host mode and USB
Type-C, they:

*   [C-4-1] MUST implement Dual Role Port functionality as defined by the USB
Type-C specification (section 4.5.1.3.3).
*   [SR] STRONGLY RECOMMENDED to support DisplayPort, SHOULD support USB
SuperSpeed Data Rates, and are STRONGLY RECOMMENDED to support Power Delivery
for data and power role swapping.
*   [SR] STRONGLY RECOMMENDED to NOT support Audio Adapter Accessory Mode as
described in the Appendix A of the
[USB Type-C Cable and Connector Specification Revision 1.2](
http://www.usb.org/developers/docs/).
*   SHOULD implement the Try.\* model that is most appropriate for the
device form factor. For example a handheld device SHOULD implement the
Try.SNK model.