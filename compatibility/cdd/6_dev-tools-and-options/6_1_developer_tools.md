## 6.1\. Developer Tools

Device implementations:

*   [C-0-1] MUST support the Android Developer Tools provided in the Android
SDK.
*   [**Android Debug Bridge (adb)**](http://developer.android.com/tools/help/adb.html)
    *   [C-0-2] MUST support all adb functions as documented in the Android
    SDK including [dumpsys](https://source.android.com/devices/input/diagnostics.html).
    *   [C-0-3] MUST NOT alter the format or the contents of device system
    events (batterystats , diskstats, fingerprint, graphicsstats, netstats,
    notification, procstats) logged via dumpsys.
    *   [C-0-4] MUST have the device-side adb daemon be inactive by default and
    there MUST be a user-accessible mechanism to turn on the Android Debug
    Bridge.
    *   [C-0-5] MUST support secure adb. Android includes support for secure
    adb. Secure adb enables adb on known authenticated hosts.
    *   [C-0-6] MUST provide a mechanism allowing adb to be connected from a
    host machine. For example:

        *   Device implementations without a USB port supporting peripheral mode
        MUST implement adb via local-area network (such as Ethernet or Wi-Fi).
        *   MUST provide drivers for Windows 7, 9 and 10, allowing developers to
        connect to the device using the adb protocol.

*    [**Dalvik Debug Monitor Service (ddms)**](http://developer.android.com/tools/debugging/ddms.html)
    *   [C-0-7] MUST support all ddms features as documented in the Android SDK.
    As ddms uses adb, support for ddms SHOULD be inactive by default, but
    MUST be supported whenever the user has activated the Android Debug Bridge,
    as above.
*    [**Monkey**](http://developer.android.com/tools/help/monkey.html)
    *   [C-0-8] MUST include the Monkey framework and make it available for
    applications to use.
*    [**SysTrace**](http://developer.android.com/tools/help/systrace.html)
    *   [C-0-9] MUST support systrace tool as documented in the Android SDK.
    Systrace must be inactive by default and there MUST be a user-accessible
    mechanism to turn on Systrace.