## 5.9\. Musical Instrument Digital Interface (MIDI)

If a device implementation supports the inter-app MIDI software transport
(virtual MIDI devices), and it supports MIDI over _all_ of the following
MIDI-capable hardware transports for which it provides generic non-MIDI
connectivity, it is: 

*    [SR] STRONGLY RECOMMENDED to report support for feature
android.software.midi via the [android.content.pm.PackageManager](http://developer.android.com/reference/android/content/pm/PackageManager.html)
class.

The MIDI-capable hardware transports are:

*   USB host mode (section 7.7 USB)
*   USB peripheral mode (section 7.7 USB)
*   MIDI over Bluetooth LE acting in central role (section 7.4.3 Bluetooth)

If the device implementation provides generic non-MIDI connectivity over a
particular MIDI-capable hardware transport listed above, but does not support
MIDI over that hardware transport, it:

*    [C-1-1] MUST NOT report support for feature android.software.midi.
