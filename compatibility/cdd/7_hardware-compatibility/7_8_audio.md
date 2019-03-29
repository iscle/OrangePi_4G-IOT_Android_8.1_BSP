## 7.8\. Audio

### 7.8.1\. Microphone


*    [H-0-1] Handheld device implementations MUST include a microphone.
*    [W-0-1] Watch device implementations MUST include a microphone.
*    [A-0-1] Automotive device implementations MUST include a microphone.

If device implementations include a microphone, they:

*   [C-1-1] MUST report the `android.hardware.microphone` feature constant.
*   [C-1-2] MUST meet the audio recording requirements in
[section 5.4](#5_4_audio_recording).
*   [C-1-3] MUST meet the audio latency requirements in
[section 5.6](#5_6_audio_latency).
*   [SR] STRONGLY RECOMMENDED to support near-ultrasound recording as described
in [section 7.8.3](#7_8_3_near_ultrasound).

If device implementations omit a microphone, they:

*    [C-2-1] MUST NOT report the `android.hardware.microphone` feature constant.
*    [C-2-2] MUST implement the audio recording API at least as no-ops, per
     [section 7](#7_hardware_compatibility).


### 7.8.2\. Audio Output

If device implementations include a speaker or an audio/multimedia output
port for an audio output peripheral such as a 4 conductor 3.5mm audio jack or
USB host mode port using [USB audio class](
https://source.android.com/devices/audio/usb#audioClass), they:

*   [C-1-1] MUST report the `android.hardware.audio.output` feature constant.
*   [C-1-2] MUST meet the audio playback requirements in
[section 5.5](#5_5_audio_playback).
*   [C-1-3] MUST meet the audio latency requirements in
[section 5.6](#5_6_audio_latency).
*   [SR] STRONGLY RECOMMENDED to support near-ultrasound playback as described
in [section 7.8.3](#7_8_3_near_ultrasound).

If device implementations do not include a speaker or audio output port, they:

*   [C-2-1] MUST NOT report the `android.hardware.audio output` feature.
*   [C-2-2] MUST implement the Audio Output related APIs as no-ops at least.

*   [H-0-1] Handheld device implementations MUST have an audio output and
declare `android.hardware.audio.output`.
*   [T-0-1] Television device implementations MUST have an audio output and
declare `android.hardware.audio.output`.
*   [A-0-1] Automotive device implementations MUST have an audio output and
declare `android.hardware.audio.output`.
*   Watch device implementations MAY but SHOULD NOT have audio output.

For the purposes of this section, an "output port" is a
[physical interface](https://en.wikipedia.org/wiki/Computer_port_%28hardware%29)
such as a 3.5mm audio jack, HDMI, or USB host mode port with USB audio class.
Support for audio output over radio-based protocols such as Bluetooth,
WiFi, or cellular network does not qualify as including an "output port".

#### 7.8.2.1\. Analog Audio Ports

In order to be compatible with the [headsets and other audio accessories](
http://source.android.com/accessories/headset-spec.html)
using the 3.5mm audio plug across the Android ecosystem, if a device
implementation includes one or more analog audio ports, at least one of the
audio port(s) SHOULD be a 4 conductor 3.5mm audio jack.

If device implementations have a 4 conductor 3.5mm audio jack, they:

*   [C-1-1] MUST support audio playback to stereo headphones and stereo headsets
with a microphone.
*   [C-1-2] MUST support TRRS audio plugs with the CTIA pin-out order.
*   [C-1-3] MUST support the detection and mapping to the keycodes for the
following 3 ranges of equivalent impedance between the microphone and ground
conductors on the audio plug:
    *   **70 ohm or less**: `KEYCODE_HEADSETHOOK`
    *   **210-290 ohm**: `KEYCODE_VOLUME_UP`
    *   **360-680 ohm**: `KEYCODE_VOLUME_DOWN`
*   [C-1-4] MUST trigger `ACTION_HEADSET_PLUG` upon a plug insert, but
only after all contacts on plug are touching their relevant segments
on the jack.
*   [C-1-5] MUST be capable of driving at least 150mV ± 10% of output voltage on
a 32 ohm speaker impedance.
*   [C-1-6] MUST have a microphone bias voltage between 1.8V ~ 2.9V.
*   [SR] STRONGLY RECOMMENDED to detect and map to the keycode for the following
range of equivalent impedance between the microphone and ground conductors
on the audio plug:
    *   **110-180 ohm:** `KEYCODE_VOICE_ASSIST`
*   SHOULD support audio plugs with the OMTP pin-out order.
*   SHOULD support audio recording from stereo headsets with a microphone.


If device implementations have a 4 conductor 3.5mm audio jack and support a
microphone, and broadcast the `android.intent.action.HEADSET_PLUG` with the
extra value microphone set as 1, they:

*   [C-2-1] MUST support the detection of microphone on the plugged in audio
accessory.

### 7.8.3\. Near-Ultrasound

Near-Ultrasound audio is the 18.5 kHz to 20 kHz band.

Device implementations:

*    MUST correctly report the support of
near-ultrasound audio capability via the [AudioManager.getProperty](
http://developer.android.com/reference/android/media/AudioManager.html#getProperty%28java.lang.String%29)
API as follows:

If [`PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND`](
http://developer.android.com/reference/android/media/AudioManager.html#PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND)
is "true", the following requirements MUST be met by the
`VOICE_RECOGNITION` and `UNPROCESSED` audio sources:

*    [C-1-1] The microphone's mean power response in the 18.5 kHz to 20 kHz band
     MUST be no more than 15 dB below the response at 2 kHz.
*    [C-1-2] The microphone's unweighted signal to noise ratio over 18.5 kHz to 20 kHz
     for a 19 kHz tone at -26 dBFS MUST be no lower than 50 dB.

If [`PROPERTY_SUPPORT_SPEAKER_NEAR_ULTRASOUND`](
http://developer.android.com/reference/android/media/AudioManager.html#PROPERTY_SUPPORT_SPEAKER_NEAR_ULTRASOUND)
is "true":

*    [C-2-1] The speaker's mean response in 18.5 kHz - 20 kHz MUST be no lower
than 40 dB below the response at 2 kHz.
