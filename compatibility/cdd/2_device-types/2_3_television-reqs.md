## 2.3\. Television Requirements

An **Android Television device** refers to an Android device implementation that
is an entertainment interface for consuming digital media, movies, games, apps,
and/or live TV for users sitting about ten feet away (a “lean back” or “10-foot
user interface”).

Android device implementations are classified as a Television if they meet all
the following criteria:

*   Have provided a mechanism to remotely control the rendered user interface on
    the display that might sit ten feet away from the user.
*   Have an embedded screen display with the diagonal length larger than 24
    inches OR include a video output port, such as VGA, HDMI, DisplayPort or a
    wireless port for display.

The additional requirements in the rest of this section are specific to Android
Television device implementations.

### 2.3.1\. Hardware

To be added.

### 2.3.2\. Multimedia

To be added.

### 2.3.3\. Software

Android Television device implementations:

*    [T-0-1] MUST declare the features
     [`android.software.leanback`](http://developer.android.com/reference/android/content/pm/PackageManager.html#FEATURE_LEANBACK)
     and `android.hardware.type.television`.

**WebView compatibility (Section 3.4.1)**

*    [T-0-1] Television devices MUST provide a complete implementation of the android.webkit.Webview API.


**Lock Screen Media Control (Section 3.8.10)**

If Android Television device implementations support a lock screen,they:

*   [T-1-1] MUST display the Lock screen Notifications including the Media Notification Template.

**Multi-windows (Section 3.8.14)**

*   [T-SR] Android Television device implementations are STRONGLY RECOMMENDED to
    support picture-in-picture (PIP) mode multi-window.

**Accessibility (Section 3.10)**

*   [T-SR] Android Television device implementations MUST support third-party
    accessibility services.

*   [T-SR] Android Television device implementations are STRONGLY RECOMMENDED to
    preload accessibility services on the device comparable with or exceeding
    functionality of the Switch Access and TalkBack (for languages supported by
    the preloaded Text-to-speech engine) accessibility services as provided in
    the [talkback open source project](https://github.com/google/talkback).

**Text-to-Speech (Section 3.11)**

If device implementations report the feature android.hardware.audio.output,
they:

*   [T-SR] STRONGLY RECOMMENDED to include a TTS engine supporting the
    languages available on the device.

*   [T-0-1] MUST support installation of third-party TTS engines.


**TV Input Framework (Section 3.12)**

To be added.


