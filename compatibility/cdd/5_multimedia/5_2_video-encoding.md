## 5.2\. Video Encoding

Handheld device implementations MUST support the following encoding and make it
available to third-party applications.

*    [H-0-1] H.264 AVC
*    [H-0-2] VP8

Television device implementations MUST support the following encoding.

*    [T-0-1] H.264 AVC
*    [T-0-2] VP8

Automotive device implementations MUST support the following encoding:

*    [A-0-1] H.264 AVC
*    [A-0-2] VP8


If device implementations support any video encoder and make it available
to third-party apps, they:

*   SHOULD NOT be, over two sliding windows, more than ~15% over the bitrate
between intraframe (I-frame) intervals.
*   SHOULD NOT be more than ~100% over the bitrate over a sliding window
of 1 second.

If device implementations include an embedded screen display with the
diagonal length of at least 2.5 inches or include a video output port or
declare the support of a camera via the `android.hardware.camera.any`
feature flag, they:

*   [C-1-1] MUST include the support of at least one of the VP8 or H.264 video
encoders, and make it available for third-party applications.
*   SHOULD support both VP8 and H.264 video encoders, and make it available
for third-party applications.

If device implementations support any of the H.264, VP8, VP9 or HEVC video
encoders and make it available to third-party applications, they:

*   [C-2-1] MUST support dynamically configurable bitrates.
*   SHOULD support variable frame rates, where video encoder SHOULD determine
instantaneous frame duration based on the timestamps of input buffers, and
allocate its bit bucket based on that frame duration.

If device implementations support the MPEG-4 SP video encoder and make it
available to third-party apps, they:

*   SHOULD support dynamically configurable bitrates for the supported encoder.


### 5.2.1\. H.263

If device implementations support H.263 encoders and make it available
to third-party apps, they:

*   [C-1-1] MUST support Baseline Profile Level 45.
*   SHOULD support dynamically configurable bitrates for the supported encoder.


### 5.2.2\. H-264

Television device implementations are:

*   [T-SR] STRONGLY RECOMMENDED to support H.264 encoding of 720p and 1080p
resolution videos.
*   [T-SR] STRONGLY RECOMMENDED to support H.264 encoding of 1080p resolution
video at 30 frame-per-second (fps).


If device implementations support H.264 codec, they:

*   [C-1-1] MUST support Baseline Profile Level 3.
    However, support for ASO (Arbitrary Slice Ordering), FMO (Flexible Macroblock
    Ordering) and RS (Redundant Slices) is OPTIONAL. Moreover, to maintain
    compatibility with other Android devices, it is RECOMMENDED that ASO, FMO
    and RS are not used for Baseline Profile by encoders.
*   [C-1-2] MUST support the SD (Standard Definition) video encoding profiles
in the following table.
*   SHOULD support Main Profile Level 4.
*   SHOULD support the  HD (High Definition) video encoding profiles as
indicated in the following table.

If device implementations report support of H.264 encoding for 720p or 1080p
resolution videos through the media APIs, they:

*   [C-2-1] MUST support the encoding profiles in the following table.


<table>
 <tr>
    <th></th>
    <th>SD (Low quality)</th>
    <th>SD (High quality)</th>
    <th>HD 720p</th>
    <th>HD 1080p</th>
 </tr>
 <tr>
    <th>Video resolution</th>
    <td>320 x 240 px</td>
    <td>720 x 480 px</td>
    <td>1280 x 720 px</td>
    <td>1920 x 1080 px</td>
 </tr>
 <tr>
    <th>Video frame rate</th>
    <td>20 fps</td>
    <td>30 fps</td>
    <td>30 fps</td>
    <td>30 fps</td>
 </tr>
 <tr>
    <th>Video bitrate</th>
    <td>384 Kbps</td>
    <td>2 Mbps</td>
    <td>4 Mbps</td>
    <td>10 Mbps</td>
 </tr>
</table>


### 5.2.3\. VP8

If device implementations support VP8 codec, they:

*   [C-1-1] MUST support the SD video encoding profiles.
*   SHOULD support the following HD (High Definition) video encoding profiles.
*   SHOULD support writing Matroska WebM files.
*   SHOULD use a hardware VP8 codec that meets the
[WebM project RTC hardware coding requirements](
http://www.webmproject.org/hardware/rtc-coding-requirements), to ensure
acceptable quality of web video streaming and video-conference services.

If device implementations report support of VP8 encoding for 720p or 1080p
resolution videos through the media APIs, they:

*   [C-2-1] MUST support the encoding profiles in the following table.

<table>
 <tr>
    <th></th>
    <th>SD (Low quality)</th>
    <th>SD (High quality)</th>
    <th>HD 720p</th>
    <th>HD 1080p</th>
 </tr>
 <tr>
    <th>Video resolution</th>
    <td>320 x 180 px</td>
    <td>640 x 360 px</td>
    <td>1280 x 720 px</td>
    <td>1920 x 1080 px</td>
 </tr>
 <tr>
    <th>Video frame rate</th>
    <td>30 fps</td>
    <td>30 fps</td>
    <td>30 fps</td>
    <td>30 fps</td>
 </tr>
 <tr>
    <th>Video bitrate</th>
    <td>800 Kbps </td>
    <td>2 Mbps</td>
    <td>4 Mbps</td>
    <td>10 Mbps</td>
 </tr>
</table>


### 5.2.4\. VP9

If device implementations support VP9 codec, they:

*   SHOULD support writing Matroska WebM files.

