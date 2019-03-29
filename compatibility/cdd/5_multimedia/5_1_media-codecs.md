## 5.1\. Media Codecs

### 5.1.1\. Audio Encoding

See more details in [5.1.3. Audio Codecs Details](#5_1_3_audio_codecs_details).

Handheld device implementations MUST support the following audio encoding:

*    [H-0-1] AMR-NB
*    [H-0-2] AMR-WB
*    [H-0-3] MPEG-4 AAC Profile (AAC LC)
*    [H-0-4] MPEG-4 HE AAC Profile (AAC+)
*    [H-0-5] AAC ELD (enhanced low delay AAC)


Television device implementations MUST support the following audio encoding:

*    [T-0-1] MPEG-4 AAC Profile (AAC LC)
*    [T-0-2] MPEG-4 HE AAC Profile (AAC+)
*    [T-0-3] AAC ELD (enhanced low delay AAC)

Automotive device implementations MUST support the following audio encoding:

*    [A-1-1] MPEG-4 AAC Profile (AAC LC)
*    [A-1-2] MPEG-4 HE AAC Profile (AAC+)
*    [A-1-3] AAC ELD (enhanced low delay AAC)

If device implementations declare `android.hardware.microphone`,
they MUST support the following audio encoding:

*    [C-1-1] PCM/WAVE


### 5.1.2\. Audio Decoding

See more details in [5.1.3. Audio Codecs Details](#5_1_3_audio_codecs_details).

Handheld device implementations MUST support the following decoding.

*    [H-0-1] AMR-NB
*    [H-0-2] AMR-WB

If device implementations declare support for the
`android.hardware.audio.output` feature, they:

*    [C-1-1] MPEG-4 AAC Profile (AAC LC)
*    [C-1-2] MPEG-4 HE AAC Profile (AAC+)
*    [C-1-3] MPEG-4 HE AACv2 Profile (enhanced AAC+)
*    [C-1-4] AAC ELD (enhanced low delay AAC)
*    [C-1-5] FLAC
*    [C-1-6] MP3
*    [C-1-7] MIDI
*    [C-1-8] Vorbis
*    [C-1-9] PCM/WAVE
*    [C-1-10] Opus

If device implementations support the decoding of AAC input buffers of
multichannel streams (i.e. more than two channels) to PCM through the default
AAC audio decoder in the `android.media.MediaCodec` API, the following MUST be
supported:

*    [C-2-1] Decoding MUST be performed without downmixing (e.g. a 5.0 AAC
stream must be decoded to five channels of PCM, a 5.1 AAC stream must be decoded
to six channels of PCM).
*    [C-2-2] Dynamic range metadata MUST be as defined in "Dynamic Range Control
(DRC)" in ISO/IEC 14496-3, and the `android.media.MediaFormat` DRC keys to
configure the dynamic range-related behaviors of the audio decoder. The
AAC DRC keys were introduced in API 21,and are:
KEY_AAC_DRC_ATTENUATION_FACTOR, KEY_AAC_DRC_BOOST_FACTOR,
KEY_AAC_DRC_HEAVY_COMPRESSION, KEY_AAC_DRC_TARGET_REFERENCE_LEVEL and
KEY_AAC_ENCODED_TARGET_LEVEL


### 5.1.3\. Audio Codecs Details

<table>
 <tr>
    <th>Format/Codec</th>
    <th>Details</th>
    <th>Supported File Types/Container Formats</th>
 </tr>
 <tr>
    <td>MPEG-4 AAC Profile<br />(AAC LC)</td>
    <td>Support for mono/stereo/5.0/5.1 content with standard
    sampling rates from 8 to 48 kHz.</td>
    <td>
    <ul>
    <li class="table_list">3GPP (.3gp)</li>
    <li class="table_list">MPEG-4 (.mp4, .m4a)</li>
    <li class="table_list">ADTS raw AAC (.aac, ADIF not supported)</li>
    <li class="table_list">MPEG-TS (.ts, not seekable)</li></ul>
    </td>
 </tr>
 <tr>
    <td>MPEG-4 HE AAC Profile (AAC+)</td>
    <td>Support for mono/stereo/5.0/5.1 content with standard
    sampling rates from 16 to 48 kHz.</td>
    <td></td>
 </tr>
 <tr>
    <td>MPEG-4 HE AACv2<br />

Profile (enhanced AAC+)</td>
    <td>Support for mono/stereo/5.0/5.1 content with standard
    sampling rates from 16 to 48 kHz.</td>
    <td></td>
 </tr>
 <tr>
    <td>AAC ELD (enhanced low delay AAC)</td>
    <td>Support for mono/stereo content with standard sampling rates from 16 to
    48 kHz.</td>
    <td></td>
 </tr>
 <tr>
    <td>AMR-NB</td>
    <td>4.75 to 12.2 kbps sampled @ 8 kHz</td>
    <td>3GPP (.3gp)</td>
 </tr>
 <tr>
    <td>AMR-WB</td>
    <td>9 rates from 6.60 kbit/s to 23.85 kbit/s sampled @ 16 kHz</td>
    <td></td>
 </tr>
 <tr>
    <td>FLAC</td>
    <td>Mono/Stereo (no multichannel). Sample rates up to 48 kHz (but up to 44.1
    kHz is RECOMMENDED on devices with 44.1 kHz output, as the 48 to 44.1 kHz
    downsampler does not include a low-pass filter). 16-bit RECOMMENDED; no
    dither applied for 24-bit.</td>
    <td>FLAC (.flac) only</td>
 </tr>
 <tr>
    <td>MP3</td>
    <td>Mono/Stereo 8-320Kbps constant (CBR) or variable bitrate (VBR)</td>
    <td>MP3 (.mp3)</td>
 </tr>
 <tr>
    <td>MIDI</td>
    <td>MIDI Type 0 and 1. DLS Version 1 and 2. XMF and Mobile XMF. Support for
    ringtone formats RTTTL/RTX, OTA, and iMelody</td>
    <td><ul>
    <li class="table_list">Type 0 and 1 (.mid, .xmf, .mxmf)</li>
    <li class="table_list">RTTTL/RTX (.rtttl, .rtx)</li>
    <li class="table_list">OTA (.ota)</li>
    <li class="table_list">iMelody (.imy)</li></ul></td>
 </tr>
 <tr>
    <td>Vorbis</td>
    <td></td>
    <td><ul>
    <li class="table_list">Ogg (.ogg)</li>
    <li class="table_list">Matroska (.mkv, Android 4.0+)</li></ul></td>
 </tr>
 <tr>
    <td>PCM/WAVE</td>
    <td>16-bit linear PCM (rates up to limit of hardware). Devices MUST support
    sampling rates for raw PCM recording at 8000, 11025, 16000, and 44100 Hz
    frequencies.</td>
    <td>WAVE (.wav)</td>
 </tr>
 <tr>
    <td>Opus</td>
    <td></td>
    <td>Matroska (.mkv), Ogg(.ogg)</td>
 </tr>
</table>

### 5.1.4\. Image Encoding

See more details in [5.1.6. Image Codecs Details](#5_1_6_image_codecs_details).

Device implementations MUST support encoding the following image encoding:

*    [C-0-1] JPEG
*    [C-0-2] PNG
*    [C-0-3] WebP

### 5.1.5\. Image Decoding

See more details in [5.1.6. Image Codecs Details](#5_1_6_image_codecs_details).

Device impelementations MUST support encoding the following image decoding:

*    [C-0-1] JPEG
*    [C-0-2] GIF
*    [C-0-3] PNG
*    [C-0-4] BMP
*    [C-0-5] WebP
*    [C-0-6] Raw

### 5.1.6\. Image Codecs Details

<table>
 <tr>
    <th>Format/Codec</th>
    <th>Details</th>
    <th>Supported File Types/Container Formats</th>
 </tr>
 <tr>
    <td>JPEG</td>
    <td>Base+progressive</td>
    <td>JPEG (.jpg)</td>
 </tr>
 <tr>
    <td>GIF</td>
    <td></td>
    <td>GIF (.gif)</td>
 </tr>
 <tr>
    <td>PNG</td>
    <td></td>
    <td>PNG (.png)</td>
 </tr>
 <tr>
    <td>BMP</td>
    <td></td>
    <td>BMP (.bmp)</td>
 </tr>
 <tr>
    <td>WebP</td>
    <td></td>
    <td>WebP (.webp)</td>
 </tr>
 <tr>
    <td>Raw</td>
    <td></td>
    <td>ARW (.arw), CR2 (.cr2), DNG (.dng), NEF (.nef), NRW (.nrw), ORF (.orf),
        PEF (.pef), RAF (.raf), RW2 (.rw2), SRW (.srw)</td>
 </tr>
</table>



### 5.1.7\. Video Codecs

*   For acceptable quality of web video streaming and video-conference
services, device implementations SHOULD use a hardware VP8 codec that meets the
[requirements](http://www.webmproject.org/hardware/rtc-coding-requirements/).

If device implementations include a video decoder or encoder:

*   [C-1-1] Video codecs MUST support output and input bytebuffer sizes that
accommodate the largest feasible compressed and uncompressed frame as dictated
by the standard and configuration but also not overallocate.

*   [C-1-2] Video encoders and decoders MUST support YUV420 flexible color
format (COLOR_FormatYUV420Flexible).

If device implementations advertise HDR profile support through
[`Display.HdrCapabilities`](
https://developer.android.com/reference/android/view/Display.HdrCapabilities.html),
they:

*   [C-2-1] MUST support HDR static metadata parsing and handling.

If device implementations advertise intra refresh support through
`FEATURE_IntraRefresh` in the [`MediaCodecInfo.CodecCapabilities`](
https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities.html#FEATURE_IntraRefresh)
class, they:

*   [C-3-1]MUST support the refresh periods in the range of 10 - 60 frames and
accurately operate within 20% of configured refresh period.



### 5.1.8\. Video Codecs List


<table>
 <tr>
    <th>Format/Codec</th>
    <th>Details</th>
    <th>Supported File Types/<br>Container Formats</th>
 </tr>
 <tr>
    <td>H.263</td>
    <td></td>
    <td><ul>
    <li class="table_list">3GPP (.3gp)</li>
    <li class="table_list">MPEG-4 (.mp4)</li></ul></td>
 </tr>
 <tr>
    <td>H.264 AVC</td>
    <td>See <a href="#5_2_video_encoding">section 5.2 </a>and
    <a href="#5_3_video_decoding">5.3</a> for details</td>
    <td><ul>
    <li class="table_list">3GPP (.3gp)</li>
    <li class="table_list">MPEG-4 (.mp4)</li>
    <li class="table_list">MPEG-2 TS (.ts, AAC audio only, not seekable, Android
    3.0+)</li></ul></td>
 </tr>
 <tr>
    <td>H.265 HEVC</td>
    <td>See <a href="#5_3_video_decoding">section 5.3</a> for details</td>
    <td>MPEG-4 (.mp4)</td>
 </tr>
<tr>
  <td>MPEG-2</td>
  <td>Main Profile</td>
  <td>MPEG2-TS</td>
</tr>
 <tr>
    <td>MPEG-4 SP</td>
    <td></td>
    <td>3GPP (.3gp)</td>
 </tr>
 <tr>
    <td>VP8</td>
    <td>See <a href="#5_2_video_encoding">section 5.2</a> and
    <a href="#5_3_video_decoding">5.3</a> for details</td>
    <td><ul>
    <li class="table_list"><a href="http://www.webmproject.org/">WebM
    (.webm)</a></li>
    <li class="table_list">Matroska (.mkv)</li></ul>
    </td>
 </tr>
 <tr>
    <td>VP9</td>
    <td>See <a href="#5_3_video_decoding">section 5.3</a> for details</td>
    <td><ul>
    <li class="table_list"><a href="http://www.webmproject.org/">WebM
    (.webm)</a></li>
    <li class="table_list">Matroska (.mkv)</li></ul>
    </td>
 </tr>
</table>


