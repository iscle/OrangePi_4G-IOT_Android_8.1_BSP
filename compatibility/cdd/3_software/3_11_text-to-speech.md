## 3.11\. Text-to-Speech

Android includes APIs that allow applications to make use of text-to-speech
(TTS) services and allows service providers to provide implementations of TTS
services.

If device implementations reporting the feature android.hardware.audio.output,
they:

*   [C-1-1] MUST support the [Android TTS framework](
http://developer.android.com/reference/android/speech/tts/package-summary.html)
APIs.

If device implementations support installation of third-party TTS engines, they:

*   [C-2-1] MUST provide user affordance to allow the user to select a TTS
    engine for use at system level.

