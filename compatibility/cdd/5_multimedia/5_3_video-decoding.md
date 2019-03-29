## 5.3\. Video Decoding

Handheld device implementations:

*    [H-0-1] MUST support decoding of H.264 AVC.
*    [H-0-2] MUST support decoding of H.265 HEVC.
*    [H-0-3] MUST support decoding of MPEG-4 SP.
*    [H-0-4] MUST support decoding of VP8.
*    [H-0-5] MUST support decoding of VP9.

Television device implementations:

*    [T-0-1] MUST support decoding of H.264 AVC.
*    [T-0-2] MUST support decoding of H.265 HEVC.
*    [T-0-3] MUST support decoding of MPEG-4 SP.
*    [T-0-4] MUST support decoding of VP8.
*    [T-0-5] MUST support decoding of VP9.
*    [T-SR] Are Strongly Recommended to support MPEG-2 decoding.


Automotive device implementations:

*    [A-0-1] MUST support decoding of H.264 AVC.
*    [A-0-2] MUST support decoding of MPEG-4 SP.
*    [A-0-3] MUST support decoding of VP8.
*    [A-0-4] MUST support decoding of VP9.
*    [A-SR] Are Strongly Recommended to support H.265 HEVC decoding.


If device implementations support VP8, VP9, H.264, or H.265 codecs, they:

*   [C-1-1] MUST support dynamic video resolution and frame rate switching
through the standard Android APIs within the same stream for all VP8, VP9,
H.264, and H.265 codecs in real time and up to the maximum resolution supported
by each codec on the device.

If device implementations declare support for the Dolby Vision decoder through
[`HDR_TYPE_DOLBY_VISION`](https://developer.android.com/reference/android/view/Display.HdrCapabilities.html#HDR_TYPE_DOLBY_VISION)
, they:

*   [C-2-1] MUST provide a Dolby Vision-capable extractor.
*   [C-2-2] MUST properly display Dolby Vision content on the device screen or
on a standard video output port (e.g., HDMI).
*   [C-2-3] MUST set the track index of backward-compatible base-layer(s) (if
present) to be the same as the combined Dolby Vision layer's track index.

### 5.3.1\. MPEG-2

If device implementations support MPEG-2 decoders, they:

*   [C-1-1] MUST support the Main Profile High Level.

### 5.3.2\. H.263

If device implementations support H.263 decoders, they:

*   [C-1-1] MUST support Baseline Profile Level 30 and Level 45.

### 5.3.3\. MPEG-4

If device implementations with MPEG-4 decoders, they:

*   [C-1-1] MUST support Simple Profile Level 3.

### 5.3.4\. H.264

If device implementations support H.264 decoders, they:

*   [C-1-1] MUST support Main Profile Level 3.1 and Baseline Profile. Support
for ASO (Arbitrary Slice Ordering), FMO (Flexible Macroblock Ordering) and RS
(Redundant Slices) is OPTIONAL.
*   [C-1-2] MUST be capable of decoding videos with the SD (Standard Definition)
    profiles listed in the following table and encoded with the Baseline Profile
    and Main Profile Level 3.1 (including 720p30).
*   SHOULD be capable of decoding videos with the HD (High Definition) profiles
    as indicated in the following table.

If the height that is reported by the `Display.getSupportedModes()` method is
equal or greater than the video resolution, device implementations:

*   [C-2-1] MUST support the HD 720p video encoding profiles in the following
table.
*   [C-2-2] MUST support the HD 1080p video encoding profiles in the following
table.

If Television device implementations support H.264 decoders, they:

*   [T-1-1] MUST support High Profile Level 4.2 and the HD 1080p (at 60 fps)
decoding profile.
*   [T-1-2] MUST be capable of decoding videos with both HD profiles as
indicated in the following table and encoded with either the Baseline Profile,
Main Profile, or the High Profile Level 4.2

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
    <td>30 fps</td>
    <td>30 fps</td>
    <td>60 fps</td>
    <td>30 fps (60 fps<sup>Television</sup>)</td>
 </tr>
 <tr>
    <th>Video bitrate</th>
    <td>800 Kbps </td>
    <td>2 Mbps</td>
    <td>8 Mbps</td>
    <td>20 Mbps</td>
 </tr>
</table>

### 5.3.5\. H.265 (HEVC)

If device implementations support H.265 codec, they:

*   [C-1-1] MUST support the Main Profile Level 3 Main tier and the SD video
decoding profiles as indicated in the following table.
*   SHOULD support the HD decoding profiles as indicated in the following table.
*   [C-1-2] MUST support the HD decoding profiles as indicated in the following
table if there is a hardware decoder.

If the height that is reported by the `Display.getSupportedModes()` method is
equal to or greater than the video resolution, then:

*   [C-2-1] Device implementations MUST support at least one of H.265 or VP9
decoding of 720, 1080 and UHD profiles.

If Television device implementations support H.265 codec and the HD 1080p
decoding profile, they:

*   [T-1-1] MUST support the Main Profile Level 4.1 Main tier.
*   [T-SR] STRONGLY RECOMMENDED to support 60 fps video frame rate
for HD 1080p.

If Television device implementations support H.265 codec and the UHD decoding
profile, then:

*   [T-2-1] The codec MUST support Main10 Level 5 Main Tier profile.

<table>
 <tr>
    <th></th>
    <th>SD (Low quality)</th>
    <th>SD (High quality)</th>
    <th>HD 720p</th>
    <th>HD 1080p</th>
    <th>UHD</th>
 </tr>
 <tr>
    <th>Video resolution</th>
    <td>352 x 288 px</td>
    <td>720 x 480 px</td>
    <td>1280 x 720 px</td>
    <td>1920 x 1080 px</td>
    <td>3840 x 2160 px</td>
 </tr>
 <tr>
    <th>Video frame rate</th>
    <td>30 fps</td>
    <td>30 fps</td>
    <td>30 fps</td>
    <td>30/60 fps (60 fps<sup>Television with H.265 hardware decoding</sup>)</td>
    <td>60 fps</td>
 </tr>
 <tr>
    <th>Video bitrate</th>
    <td>600 Kbps </td>
    <td>1.6 Mbps</td>
    <td>4 Mbps</td>
    <td>5 Mbps</td>
    <td>20 Mbps</td>
 </tr>
</table>


### 5.3.6\. VP8

If device implementations support VP8 codec, they:

*   [C-1-1] MUST support the SD decoding profiles in the following table.
*   SHOULD use a hardware VP8 codec that meets the
[requirements]("http://www.webmproject.org/hardware/rtc-coding-requirements/").
*   SHOULD support the HD decoding profiles in the following table.


If the height as reported by the `Display.getSupportedModes()` method is equal
or greater than the video resolution, then:

*   [C-2-1] Device implementations MUST support 720p profiles in the
following table.
*   [C-2-2] Device implementations MUST support 1080p profiles in the
following table.

If Television device implementations support VP8 codec, they:

*   [T-1-1] MUST support the HD 1080p60 decoding profile.

If Television device implementations support VP8 codec and support 720p, they:

*   [T-2-1] MUST support the HD 720p60 decoding profile.



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
    <td>30 fps (60 fps<sup>Television</sup>)</td>
    <td>30 (60 fps<sup>Television</sup>)</td>
 </tr>
 <tr>
    <th>Video bitrate</th>
    <td>800 Kbps </td>
    <td>2 Mbps</td>
    <td>8 Mbps</td>
    <td>20 Mbps</td>
 </tr>
</table>


### 5.3.7\. VP9

If device implementations support VP9 codec, they:

*   [C-1-1] MUST support the SD video decoding profiles as indicated in the
following table.
*   SHOULD support the HD decoding profiles as indicated in the following table.

If device implementations support VP9 codec and a hardware decoder:

*   [C-2-2] MUST support the HD decoding profiles as indicated in the following
table.

If the height that is reported by the `Display.getSupportedModes()` method is
equal to or greater than the video resolution, then:

*   [C-3-1] Device implementations MUST support at least one of VP9 or H.265
decoding of the 720, 1080 and UHD profiles.

If Television device implementations support VP9 codec and the UHD video
decoding, they:

*   [T-1-1] MUST support 8-bit color depth and SHOULD support VP9 Profile 2
(10-bit).

If Television device implementations support VP9 codec, the 1080p profile and
VP9 hardware decoding, they:

*   [T-2-1] MUST support 60 fps for 1080p.

<table>
 <tr>
    <th></th>
    <th>SD (Low quality)</th>
    <th>SD (High quality)</th>
    <th>HD 720p</th>
    <th>HD 1080p</th>
    <th>UHD</th>
 </tr>
 <tr>
    <th>Video resolution</th>
    <td>320 x 180 px</td>
    <td>640 x 360 px</td>
    <td>1280 x 720 px</td>
    <td>1920 x 1080 px</td>
    <td>3840 x 2160 px</td>
 </tr>
 <tr>
    <th>Video frame rate</th>
    <td>30 fps</td>
    <td>30 fps</td>
    <td>30 fps</td>
    <td>30 fps (60 fps<sup>Television with VP9 hardware decoding</sup>)</td>
    <td>60 fps</td>
 </tr>
 <tr>
    <th>Video bitrate</th>
    <td>600 Kbps</td>
    <td>1.6 Mbps</td>
    <td>4 Mbps</td>
    <td>5 Mbps</td>
    <td>20 Mbps</td>
 </tr>
</table>