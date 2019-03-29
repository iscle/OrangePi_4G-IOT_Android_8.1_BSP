## 2.4\. Watch Requirements

An **Android Watch device** refers to an Android device implementation intended to
be worn on the body, perhaps on the wrist.

Android device implementations are classified as a Watch if they meet all the
following criteria:

*   Have a screen with the physical diagonal length in the range from 1.1 to 2.5
    inches.
*   Have a mechanism provided to be worn on the body.

The additional requirements in the rest of this section are specific to Android
Watch device implementations.

### 2.4.1\. Hardware

To be added.

### 2.4.2\. Multimedia

To be added.

### 2.4.3\. Software

Android Watch device implementations:

*   [W-0-1] MUST declare the feature android.hardware.type.watch.
*   [W-0-2] MUST support uiMode =
    [UI_MODE_TYPE_WATCH](http://developer.android.com/reference/android/content/res/Configuration.html#UI_MODE_TYPE_WATCH).


**Search (Section 3.8.4)**

*   [W-SR] Watch device implementations are STRONGLY RECOMMENDED to implement
    an assistant on the device to handle the [Assist action](
    http://developer.android.com/reference/android/content/Intent.html#ACTION_ASSIST).


**Accessibility (Section 3.10)**

*   [W-1-1] Android Watch device implementations that declare the
    `android.hardware.audio.output` feature flag MUST support third-party
    accessibility services.

*   [W-SR] Android Watch device implementations that declare `android.hardware.
    audio.output` are STRONGLY RECOMMENDED to preload accessibility services on
    the device comparable with or exceeding functionality of the Switch Access
    and TalkBack (for languages supported by the preloaded Text-to-speech
    engine) accessibility services as provided in the [talkback open source
    project]( https://github.com/google/talkback).

**Text-to-Speech (Section 3.11)**

If device implementations report the feature android.hardware.audio.output,
they:

*   [W-SR] STRONGLY RECOMMENDED to include a TTS engine supporting the
    languages available on the device.

*   [W-0-1] MUST support installation of third-party TTS engines.
