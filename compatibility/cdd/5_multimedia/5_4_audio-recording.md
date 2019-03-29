## 5.4\. Audio Recording

While some of the requirements outlined in this section are listed as SHOULD
since Android 4.3, the Compatibility Definition for future versions are planned
to change these to MUST. Existing and new Android devices are **STRONGLY
RECOMMENDED** to meet these requirements that are listed as SHOULD, or they
will not be able to attain Android compatibility when upgraded to the future
version.

### 5.4.1\. Raw Audio Capture

If device implementations declare `android.hardware.microphone`, they:

*   [C-1-1] MUST allow capture of raw audio content with the following
characteristics:

   *   **Format**: Linear PCM, 16-bit
   *   **Sampling rates**: 8000, 11025, 16000, 44100 Hz
   *   **Channels**: Mono

*   [C-1-2] MUST capture at above sample rates without up-sampling.
*   [C-1-3] MUST include an appropriate anti-aliasing filter when the
sample rates given above are captured with down-sampling.
*   SHOULD allow AM radio and DVD quality capture of raw audio content, which
means the following characteristics:

   *   **Format**: Linear PCM, 16-bit
   *   **Sampling rates**: 22050, 48000 Hz
   *   **Channels**: Stereo

If device implementations allow AM radio and DVD quality capture of raw audio
content, they:

*   [C-2-1] MUST capture without up-sampling at any ratio higher
than 16000:22050 or 44100:48000.
*   [C-2-2] MUST include an appropriate anti-aliasing filter for any
up-sampling or down-sampling.

### 5.4.2\. Capture for Voice Recognition

If device implementations declare `android.hardware.microphone`, they:

*   [C-1-1] MUST capture
    `android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION` audio source at
    one of the sampling rates, 44100 and 48000.
*   [C-1-2] MUST, by default, disable any noise reduction audio processing when
    recording an audio stream from the `AudioSource.VOICE_RECOGNITION` audio
    source.
*   [C-1-3] MUST, by default, disable any automatic gain control when recording
    an audio stream from the `AudioSource.VOICE_RECOGNITION` audio source.
*   SHOULD record the voice recognition audio stream with approximately flat
    amplitude versus frequency characteristics: specifically, ±3 dB, from 100 Hz
    to 4000 Hz.
*   SHOULD record the voice recognition audio stream with input sensitivity set
    such that a 90 dB sound power level (SPL) source at 1000 Hz yields RMS of
    2500 for 16-bit samples.
*   SHOULD record the voice recognition audio stream so that the PCM amplitude
    levels linearly track input SPL changes over at least a 30 dB range from -18
    dB to +12 dB re 90 dB SPL at the microphone.
*   SHOULD record the voice recognition audio stream with total harmonic
    distortion (THD) less than 1% for 1 kHz at 90 dB SPL input level at the
    microphone.

If device impelementations declare `android.hardware.microphone` and noise
suppression (reduction) technologies tuned for speech recognition, they:

*   [C-2-1] MUST allow this audio affect to be controllable with the
    `android.media.audiofx.NoiseSuppressor` API.
*   [C-2-2] MUST uniquely identfiy each noise suppression technology
    implementation via the `AudioEffect.Descriptor.uuid` field.

### 5.4.3\. Capture for Rerouting of Playback

The `android.media.MediaRecorder.AudioSource` class includes the `REMOTE_SUBMIX`
audio source.

If device implementations declare both `android.hardware.audio.output` and
`android.hardware.microphone`, they:

*   [C-1-1] MUST properly implement the `REMOTE_SUBMIX` audio source so that
when an application uses the `android.media.AudioRecord` API to record from this
audio source, it captures a mix of all audio streams except for the following:

    * `AudioManager.STREAM_RING`
    * `AudioManager.STREAM_ALARM`
    * `AudioManager.STREAM_NOTIFICATION`

