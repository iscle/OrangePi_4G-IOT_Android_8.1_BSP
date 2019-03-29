## 5.11\. Capture for Unprocessed

Android includes support for recording of unprocessed audio via the
`android.media.MediaRecorder.AudioSource.UNPROCESSED` audio source. In
OpenSL ES, it can be accessed with the record preset
`SL_ANDROID_RECORDING_PRESET_UNPROCESSED`.

If device implementations intent to support unprocessed audio source and make
it available to third-party apps, they:

*    [C-1-1] MUST report the support through the `android.media.AudioManager`
property [PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED](http://developer.android.com/reference/android/media/AudioManager.html#PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED).

*    [C-1-2] MUST exhibit approximately flat amplitude-versus-frequency
characteristics in the mid-frequency range: specifically &plusmn;10dB from
100 Hz to 7000 Hz for each and every microphone used to record the unprocessed
audio source.

*    [C-1-3] MUST exhibit amplitude levels in the low frequency
range: specifically from &plusmn;20 dB from 5 Hz to 100 Hz compared to the
mid-frequency range for each and every microphone used to record the
unprocessed audio source.

*    [C-1-4] MUST exhibit amplitude levels in the high frequency
range: specifically from &plusmn;30 dB from 7000 Hz to 22 KHz compared to the
mid-frequency range for each and every microphone used to record the
unprocessed audio source.

*    [C-1-5] MUST set audio input sensitivity such that a 1000 Hz sinusoidal
tone source played at 94 dB Sound Pressure Level (SPL) yields a response with
RMS of 520 for 16 bit-samples (or -36 dB Full Scale for floating point/double
precision samples) for each and every microphone used to record the unprocessed
audio source.

*    [C-1-6] MUST have a signal-to-noise ratio (SNR) at 60 dB or higher for
each and every microphone used to record the unprocessed audio source.
(whereas the SNR is measured as the difference between 94 dB SPL and equivalent
SPL of self noise, A-weighted).

*    [C-1-7] MUST have a total harmonic distortion (THD) less than be less than
1% for 1 kHZ at 90 dB SPL input level at each and every microphone used to
record the unprocessed audio source.

*    MUST not have any other signal processing (e.g. Automatic Gain Control,
High Pass Filter, or Echo cancellation) in the path other than a level
multiplier to bring the level to desired range. In other words:
*    [C-1-8] If any signal processing is present in the architecture for any
reason, it MUST be disabled and effectively introduce zero delay or extra
latency to the signal path.
*    [C-1-9] The level multiplier, while allowed to be on the path, MUST NOT
introduce delay or latency to the signal path.

All SPL measurements are made directly next to the microphone under test.
For multiple microphone configurations, these requirements apply to
each microphone.

If device implementations declare `android.hardware.microphone` but do not
support unprocessed audio source, they:

*    [C-2-1] MUST return `null` for the `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)`
API method, to properly indicate the lack of support.
*    [SR] are still STRONGLY RECOMMENDED to satisfy as many of the requirements
for the signal path for the unprocessed recording source.