## 5.6\. Audio Latency

Audio latency is the time delay as an audio signal passes through a system.
Many classes of applications rely on short latencies, to achieve real-time
sound effects.

For the purposes of this section, use the following definitions:

*   **output latency**. The interval between when an application writes a frame
of PCM-coded data and when the corresponding sound is presented to environment
at an on-device transducer or signal leaves the device via a port and can be
observed externally.
*   **cold output latency**. The output latency for the first frame, when the
audio output system has been idle and powered down prior to the request.
*   **continuous output latency**. The output latency for subsequent frames,
after the device is playing audio.
*   **input latency**. The interval between when a sound is presented by
environment to device at an on-device transducer or signal enters the device via
a port and when an application reads the corresponding frame of PCM-coded data.
*   **lost input**. The initial portion of an input signal that is unusable or
unavailable.
*   **cold input latency**. The sum of lost input time and the input latency
for the first frame, when the audio input system has been idle and powered down
prior to the request.
*   **continuous input latency**. The input latency for subsequent frames,
while the device is capturing audio.
*   **cold output jitter**. The variability among separate measurements of cold
output latency values.
*   **cold input jitter**. The variability among separate measurements of cold
input latency values.
*   **continuous round-trip latency**. The sum of continuous input latency plus
continuous output latency plus one buffer period. The buffer period allows
time for the app to process the signal and time for the app to mitigate phase
difference between input and output streams.
*   **OpenSL ES PCM buffer queue API**. The set of PCM-related
[OpenSL ES](https://developer.android.com/ndk/guides/audio/opensl/index.html)
APIs within [Android NDK](https://developer.android.com/ndk/index.html).
*   **AAudio native audio API**. The set of
[AAudio](https://developer.android.com/ndk/guides/audio/aaudio/aaudio.html) APIs
within [Android NDK](https://developer.android.com/ndk/index.html).

If device implementations declare `android.hardware.audio.output` they are
STRONGLY RECOMMENDED to meet or exceed the following requirements:

*   [SR] Cold output latency of 100 milliseconds or less
*   [SR] Continuous output latency of 45 milliseconds or less
*   [SR] Minimize the cold output jitter

If device implementations meet the above requirements after any initial
calibration when using the OpenSL ES PCM buffer queue API, for continuous output
latency and cold output latency over at least one supported audio output device,
they are:

*   [SR] STRONGLY RECOMMENDED to report low latency audio by declaring 
`android.hardware.audio.low_latency` feature flag.
*   [SR] STRONGLY RECOMMENDED to also meet the requirements for low-latency
    audio via the AAudio API.

If device implementations do not meet the requirements for low-latency audio
via the OpenSL ES PCM buffer queue API, they:

*   [C-1-1] MUST NOT report support for low-latency audio.

If device implementations include `android.hardware.microphone`, they are
STRONGLY RECOMMENDED to meet these input audio requirements:

   *   [SR] Cold input latency of 100 milliseconds or less
   *   [SR] Continuous input latency of 30 milliseconds or less
   *   [SR] Continuous round-trip latency of 50 milliseconds or less
   *   [SR] Minimize the cold input jitter