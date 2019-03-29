## 2.5\. Automotive Requirements

**Android Automotive implementation** refers to a vehicle head unit running
Android as an operating system for part or all of the system and/or
infotainment functionality. Android Automotive implementations:

Android device implementations are classified as an Automotive if they declare
the feature `android.hardware.type.automotive` or meet all the following
criteria.

*   are embedded as part of, or pluggable to, an automotive vehicle.
*   are using a screen in the driver's seat row as the primary display.

The additional requirements in the rest of this section are specific to Android
Automotive device implementations.

### 2.5.1\. Hardware

Android Automotive device implementations:

*   [A-0-1] MUST have a screen with the physical diagonal length equal to or greater
    than 6 inches.

More to be added.

### 2.5.2\. Multimedia

To be added.

### 2.5.3\. Software

*   [A-0-1] MUST declare the feature android.hardware.type.automotive.
*   [A-0-2] MUST support uiMode =
    [UI_MODE_TYPE_CAR](http://developer.android.com/reference/android/content/res/Configuration.html#UI_MODE_TYPE_CAR).
*   [A-0-3] Android Automotive implementations MUST support all public APIs in the
`android.car.*` namespace.

**WebView Compatibility (Section 3.4.1)**

*   [A-0-1] Automobile devices MUST provide a complete implementation of the android.webkit.Webview API.

**Notifications (Section 3.8.3)**

Android Automotive device implementations:

*   [A-0-1] MUST display notifications that use the [`Notification.CarExtender`](
    https://developer.android.com/reference/android/app/Notification.CarExtender.html) API when
    requested by third-party applications.

**Search (Section 3.8.4)**

*   [A-0-1] Android Automotive implementations MUST implement an assistant on
    the device to handle the [Assist action](
    http://developer.android.com/reference/android/content/Intent.html#ACTION_ASSIST).


**Media UI (Section 3.14)**

*   [A-0-1] Automotive implementations MUST include a UI framework to support
    third-party apps using the media APIs as described in section 3.14.
