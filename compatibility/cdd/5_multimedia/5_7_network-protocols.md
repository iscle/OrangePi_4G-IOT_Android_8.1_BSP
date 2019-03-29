## 5.7\. Network Protocols

Device implementations MUST support the [media network protocols](
http://developer.android.com/guide/appendix/media-formats.html)
for audio and video playback as specified in the Android SDK documentation.

If device implementations include an audio or a video decoder, they:

*    [C-1-1] MUST support all required codecs and container formats in
[section 5.1](#5_1_media_codecs) over HTTP(S).

*    [C-1-2] MUST support the media segment formats shown in
the Media Segmant Formats table below over
[HTTP Live Streaming draft protocol, Version 7](
http://tools.ietf.org/html/draft-pantos-http-live-streaming-07).

*    [C-1-3] MUST support the following RTP audio video profile and related
codecs in the RTSP table below. For exceptions please see the table footnotes
in [section 5.1](#5_1_media_codecs).

Media Segment Formats

<table>

 <tr>
    <th>Segment formats</th>
    <th>Reference(s)</th>
    <th>Required codec support</th>
 </tr>

 <tr id="mp2t">
    <td>MPEG-2 Transport Stream</td>
    <td><a href="http://www.iso.org/iso/catalogue_detail?csnumber=44169">ISO 13818</a></td>
    <td>
    Video codecs:
    <ul>
    <li class="table_list">H264 AVC</li>
    <li class="table_list">MPEG-4 SP</li>
    <li class="table_list">MPEG-2</li>
    </ul>
    See <a href="#5_1_3_video_codecs">section 5.1.3</a> for details on H264 AVC, MPEG2-4 SP,<br/>
    and MPEG-2.
    <p>Audio codecs:
    <ul>
    <li class="table_list">AAC</li>
    </ul>
    See <a href="#5_1_1_audio_codecs">section 5.1.1 </a> for details on AAC and its variants.
    </td>
 </tr>

 <tr>
    <td>AAC with ADTS framing and ID3 tags</td>
    <td><a href="http://www.iso.org/iso/home/store/catalogue_tc/catalogue_detail.htm?csnumber=43345">ISO 13818-7</a></td>
    <td>See <a href="#5_1_1_audio_codecs">section 5.1.1 </a>
    for details on AAC and its variants</td>
 </tr>

 <tr>
    <td>WebVTT</td>
    <td><a href="http://dev.w3.org/html5/webvtt/">WebVTT</a></td>
    <td></td>
 </tr>

</table>

RTSP (RTP, SDP)

<table>
 <tr>
    <th>Profile name</th>
    <th>Reference(s)</th>
    <th>Required codec support</th>
 </tr>

 <tr>
    <td>H264 AVC</td>
    <td><a href="https://tools.ietf.org/html/rfc6184">RFC 6184</a></td>
    <td>See <a href="#5_1_3_video_codecs">section 5.1.3 </a>
    for details on H264 AVC</td>
 </tr>

 <tr>
    <td>MP4A-LATM</td>
    <td><a href="https://tools.ietf.org/html/rfc6416">RFC 6416</a></td>
    <td>See <a href="#5_1_1_audio_codecs">section 5.1.1 </a>
    for details on AAC and its variants</td>
 </tr>

 <tr>
    <td>H263-1998</td>
    <td>
    <a href="https://tools.ietf.org/html/rfc3551">RFC 3551</a><br/>
    <a href="https://tools.ietf.org/html/rfc4629">RFC 4629</a><br/>
    <a href="https://tools.ietf.org/html/rfc2190">RFC 2190</a>
    </td>
    <td>See <a href="#5_1_3_video_codecs">section 5.1.3 </a>
    for details on H263
    </td>
 </tr>

 <tr>
    <td>H263-2000</td>
    <td>
    <a href="https://tools.ietf.org/html/rfc4629">RFC 4629</a>
    </td>
    <td>See <a href="#5_1_3_video_codecs">section 5.1.3 </a>
    for details on H263
    </td>
 </tr>

 <tr>
    <td>AMR</td>
    <td>
    <a href="https://tools.ietf.org/html/rfc4867">RFC 4867</a>
    </td>
    <td>See <a href="#5_1_1_audio_codecs">section 5.1.1 </a>
    for details on AMR-NB
    </td>
 </tr>

 <tr>
    <td>AMR-WB</td>
    <td>
    <a href="https://tools.ietf.org/html/rfc4867">RFC 4867</a>
    </td>
    <td>See <a href="#5_1_1_audio_codecs">section 5.1.1 </a>
    for details on AMR-WB
    </td>
 </tr>

 <tr>
    <td>MP4V-ES</td>
    <td>
    <a href="https://tools.ietf.org/html/rfc6416">RFC 6416</a>
    </td>
    <td>See <a href="#5_1_3_video_codecs">section 5.1.3 </a>
    for details on MPEG-4 SP
    </td>
 </tr>

 <tr>
    <td>mpeg4-generic</td>
    <td><a href="https://tools.ietf.org/html/rfc3640">RFC 3640</a></td>
    <td>See <a href="#5_1_1_audio_codecs">section 5.1.1 </a>
    for details on AAC and its variants</td>
 </tr>

 <tr>
    <td>MP2T</td>
    <td><a href="https://tools.ietf.org/html/rfc2250">RFC 2250</a></td>
    <td>See <a href="#mp2t">MPEG-2 Transport Stream</a> underneath HTTP Live Streaming for details</td>
 </tr>

</table>
