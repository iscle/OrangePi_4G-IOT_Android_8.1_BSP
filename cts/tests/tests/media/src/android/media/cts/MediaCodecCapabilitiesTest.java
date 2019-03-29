/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.media.cts;

import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.AudioCapabilities;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecInfo.VideoCapabilities;
import static android.media.MediaCodecInfo.CodecProfileLevel.*;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import static android.media.MediaFormat.MIMETYPE_VIDEO_AVC;
import static android.media.MediaFormat.MIMETYPE_VIDEO_H263;
import static android.media.MediaFormat.MIMETYPE_VIDEO_HEVC;
import static android.media.MediaFormat.MIMETYPE_VIDEO_MPEG4;
import static android.media.MediaFormat.MIMETYPE_VIDEO_VP8;
import static android.media.MediaFormat.MIMETYPE_VIDEO_VP9;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.DynamicConfigDeviceSide;
import com.android.compatibility.common.util.MediaUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.util.Vector;

/**
 * Basic sanity test of data returned by MediaCodeCapabilities.
 */
public class MediaCodecCapabilitiesTest extends MediaPlayerTestBase {

    private static final String TAG = "MediaCodecCapabilitiesTest";
    private static final int PLAY_TIME_MS = 30000;
    private static final int TIMEOUT_US = 1000000;  // 1 sec
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames

    private final MediaCodecList mAllCodecs =
            new MediaCodecList(MediaCodecList.ALL_CODECS);
    private final MediaCodecInfo[] mAllInfos =
            mAllCodecs.getCodecInfos();

    private static final String AVC_BASELINE_12_KEY =
            "media_codec_capabilities_test_avc_baseline12";
    private static final String AVC_BASELINE_30_KEY =
            "media_codec_capabilities_test_avc_baseline30";
    private static final String AVC_HIGH_31_KEY = "media_codec_capabilities_test_avc_high31";
    private static final String AVC_HIGH_40_KEY = "media_codec_capabilities_test_avc_high40";
    private static final String MODULE_NAME = "CtsMediaTestCases";
    private DynamicConfigDeviceSide dynamicConfig;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        dynamicConfig = new DynamicConfigDeviceSide(MODULE_NAME);
    }

    // Android device implementations with H.264 encoders, MUST support Baseline Profile Level 3.
    // SHOULD support Main Profile/ Level 4, if supported the device must also support Main
    // Profile/Level 4 decoding.
    public void testH264EncoderProfileAndLevel() throws Exception {
        if (!MediaUtils.checkEncoder(MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }

        assertTrue(
                "H.264 must support Baseline Profile Level 3",
                hasEncoder(MIMETYPE_VIDEO_AVC, AVCProfileBaseline, AVCLevel3));

        if (hasEncoder(MIMETYPE_VIDEO_AVC, AVCProfileMain, AVCLevel4)) {
            assertTrue(
                    "H.264 decoder must support Main Profile Level 4 if it can encode it",
                    hasDecoder(MIMETYPE_VIDEO_AVC, AVCProfileMain, AVCLevel4));
        }
    }

    // Android device implementations with H.264 decoders, MUST support Baseline Profile Level 3.
    // Android Television Devices MUST support High Profile Level 4.2.
    public void testH264DecoderProfileAndLevel() throws Exception {
        if (!MediaUtils.checkDecoder(MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }

        assertTrue(
                "H.264 must support Baseline Profile Level 3",
                hasDecoder(MIMETYPE_VIDEO_AVC, AVCProfileBaseline, AVCLevel3));

        if (isTv()) {
            assertTrue(
                    "H.264 must support High Profile Level 4.2 on TV",
                    checkDecoder(MIMETYPE_VIDEO_AVC, AVCProfileHigh, AVCLevel42));
        }
    }

    // Android device implementations with H.263 encoders, MUST support Level 45.
    public void testH263EncoderProfileAndLevel() throws Exception {
        if (!MediaUtils.checkEncoder(MIMETYPE_VIDEO_H263)) {
            return; // skip
        }

        assertTrue(
                "H.263 must support Level 45",
                hasEncoder(MIMETYPE_VIDEO_H263, MPEG4ProfileSimple, H263Level45));
    }

    // Android device implementations with H.263 decoders, MUST support Level 30.
    public void testH263DecoderProfileAndLevel() throws Exception {
        if (!MediaUtils.checkDecoder(MIMETYPE_VIDEO_H263)) {
            return; // skip
        }

        assertTrue(
                "H.263 must support Level 30",
                hasDecoder(MIMETYPE_VIDEO_H263, MPEG4ProfileSimple, H263Level30));
    }

    // Android device implementations with MPEG-4 decoders, MUST support Simple Profile Level 3.
    public void testMpeg4DecoderProfileAndLevel() throws Exception {
        if (!MediaUtils.checkDecoder(MIMETYPE_VIDEO_MPEG4)) {
            return; // skip
        }

        assertTrue(
                "MPEG-4 must support Simple Profile Level 3",
                hasDecoder(MIMETYPE_VIDEO_MPEG4, MPEG4ProfileSimple, MPEG4Level3));
    }

    // Android device implementations, when supporting H.265 codec MUST support the Main Profile
    // Level 3 Main tier.
    // Android Television Devices MUST support the Main Profile Level 4.1 Main tier.
    // When the UHD video decoding profile is supported, it MUST support Main10 Level 5 Main
    // Tier profile.
    public void testH265DecoderProfileAndLevel() throws Exception {
        if (!MediaUtils.checkDecoder(MIMETYPE_VIDEO_HEVC)) {
            return; // skip
        }

        assertTrue(
                "H.265 must support Main Profile Main Tier Level 3",
                hasDecoder(MIMETYPE_VIDEO_HEVC, HEVCProfileMain, HEVCMainTierLevel3));

        if (isTv()) {
            assertTrue(
                    "H.265 must support Main Profile Main Tier Level 4.1 on TV",
                    hasDecoder(MIMETYPE_VIDEO_HEVC, HEVCProfileMain, HEVCMainTierLevel41));
        }

        if (isTv() && MediaUtils.canDecodeVideo(MIMETYPE_VIDEO_HEVC, 3840, 2160, 30)) {
            assertTrue(
                    "H.265 must support Main10 Profile Main Tier Level 5 if UHD is supported",
                    hasDecoder(MIMETYPE_VIDEO_HEVC, HEVCProfileMain10, HEVCMainTierLevel5));
        }
    }

    public void testAvcBaseline1() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_AVC, AVCProfileBaseline, AVCLevel1)) {
            return; // skip
        }

        // TODO: add a test stream
        MediaUtils.skipTest(TAG, "no test stream");
    }

    public void testAvcBaseline12() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_AVC, AVCProfileBaseline, AVCLevel12)) {
            return; // skip
        }

        String urlString = dynamicConfig.getValue(AVC_BASELINE_12_KEY);
        playVideoWithRetries(urlString, 256, 144, PLAY_TIME_MS);
    }

    public void testAvcBaseline30() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_AVC, AVCProfileBaseline, AVCLevel3)) {
            return; // skip
        }

        String urlString = dynamicConfig.getValue(AVC_BASELINE_30_KEY);
        playVideoWithRetries(urlString, 640, 360, PLAY_TIME_MS);
    }

    public void testAvcHigh31() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_AVC, AVCProfileHigh, AVCLevel31)) {
            return; // skip
        }

        String urlString = dynamicConfig.getValue(AVC_HIGH_31_KEY);
        playVideoWithRetries(urlString, 1280, 720, PLAY_TIME_MS);
    }

    public void testAvcHigh40() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_AVC, AVCProfileHigh, AVCLevel4)) {
            return; // skip
        }
        if (ApiLevelUtil.isBefore(18)) {
            MediaUtils.skipTest(TAG, "fragmented mp4 not supported");
            return;
        }

        String urlString = dynamicConfig.getValue(AVC_HIGH_40_KEY);
        playVideoWithRetries(urlString, 1920, 1080, PLAY_TIME_MS);
    }

    public void testHevcMain1() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_HEVC, HEVCProfileMain, HEVCMainTierLevel1)) {
            return; // skip
        }

        // TODO: add a test stream
        MediaUtils.skipTest(TAG, "no test stream");
    }

    public void testHevcMain2() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_HEVC, HEVCProfileMain, HEVCMainTierLevel2)) {
            return; // skip
        }

        // TODO: add a test stream
        MediaUtils.skipTest(TAG, "no test stream");
    }

    public void testHevcMain21() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_HEVC, HEVCProfileMain, HEVCMainTierLevel21)) {
            return; // skip
        }

        // TODO: add a test stream
        MediaUtils.skipTest(TAG, "no test stream");
    }

    public void testHevcMain3() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_HEVC, HEVCProfileMain, HEVCMainTierLevel3)) {
            return; // skip
        }

        // TODO: add a test stream
        MediaUtils.skipTest(TAG, "no test stream");
    }

    public void testHevcMain31() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_HEVC, HEVCProfileMain, HEVCMainTierLevel31)) {
            return; // skip
        }

        // TODO: add a test stream
        MediaUtils.skipTest(TAG, "no test stream");
    }

    public void testHevcMain4() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_HEVC, HEVCProfileMain, HEVCMainTierLevel4)) {
            return; // skip
        }

        // TODO: add a test stream
        MediaUtils.skipTest(TAG, "no test stream");
    }

    public void testHevcMain41() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_HEVC, HEVCProfileMain, HEVCMainTierLevel41)) {
            return; // skip
        }

        // TODO: add a test stream
        MediaUtils.skipTest(TAG, "no test stream");
    }

    public void testHevcMain5() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_HEVC, HEVCProfileMain, HEVCMainTierLevel5)) {
            return; // skip
        }

        // TODO: add a test stream
        MediaUtils.skipTest(TAG, "no test stream");
    }

    public void testHevcMain51() throws Exception {
        if (!checkDecoder(MIMETYPE_VIDEO_HEVC, HEVCProfileMain, HEVCMainTierLevel51)) {
            return; // skip
        }

        // TODO: add a test stream
        MediaUtils.skipTest(TAG, "no test stream");
    }

    private boolean checkDecoder(String mime, int profile, int level) {
        if (!hasDecoder(mime, profile, level)) {
            MediaUtils.skipTest(TAG, "no " + mime + " decoder for profile "
                    + profile + " and level " + level);
            return false;
        }
        return true;
    }

    private boolean hasDecoder(String mime, int profile, int level) {
        return supports(mime, false /* isEncoder */, profile, level);
    }

    private boolean hasEncoder(String mime, int profile, int level) {
        return supports(mime, true /* isEncoder */, profile, level);
    }

    private boolean supports(
            String mime, boolean isEncoder, int profile, int level) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo info : mcl.getCodecInfos()) {
            if (isEncoder != info.isEncoder()) {
                continue;
            }
            try {
                CodecCapabilities caps = info.getCapabilitiesForType(mime);
                for (CodecProfileLevel pl : caps.profileLevels) {
                    if (pl.profile != profile) {
                        continue;
                    }

                    // H.263 levels are not completely ordered:
                    // Level45 support only implies Level10 support
                    if (mime.equalsIgnoreCase(MIMETYPE_VIDEO_H263)) {
                        if (pl.level != level && pl.level == H263Level45 && level > H263Level10) {
                            continue;
                        }
                    }
                    if (pl.level >= level) {
                        return true;
                    }
                }
            } catch (IllegalArgumentException e) {
            }
        }
        return false;
    }

    private boolean isVideoMime(String mime) {
        return mime.toLowerCase().startsWith("video/");
    }

    private Set<String> requiredAdaptiveFormats() {
        Set<String> adaptiveFormats = new HashSet<String>();
        adaptiveFormats.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        adaptiveFormats.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
        adaptiveFormats.add(MediaFormat.MIMETYPE_VIDEO_VP8);
        adaptiveFormats.add(MediaFormat.MIMETYPE_VIDEO_VP9);
        return adaptiveFormats;
    }

    public void testHaveAdaptiveVideoDecoderForAllSupportedFormats() {
        Set<String> supportedFormats = new HashSet<String>();
        boolean skipped = true;

        // gather all supported video formats
        for (MediaCodecInfo info : mAllInfos) {
            if (info.isEncoder()) {
                continue;
            }
            for (String mime : info.getSupportedTypes()) {
                if (isVideoMime(mime)) {
                    supportedFormats.add(mime);
                }
            }
        }

        // limit to CDD-required formats for now
        supportedFormats.retainAll(requiredAdaptiveFormats());

        // check if there is an adaptive decoder for each
        for (String mime : supportedFormats) {
            skipped = false;
            // implicit assumption that QCIF video is always valid.
            MediaFormat format = MediaFormat.createVideoFormat(mime, 176, 144);
            format.setFeatureEnabled(CodecCapabilities.FEATURE_AdaptivePlayback, true);
            String codec = mAllCodecs.findDecoderForFormat(format);
            assertTrue(
                    "could not find adaptive decoder for " + mime, codec != null);
        }
        if (skipped) {
            MediaUtils.skipTest("no video decoders that are required to be adaptive found");
        }
    }

    public void testAllVideoDecodersAreAdaptive() {
        Set<String> adaptiveFormats = requiredAdaptiveFormats();
        boolean skipped = true;
        for (MediaCodecInfo info : mAllInfos) {
            if (info.isEncoder()) {
                continue;
            }
            for (String mime : info.getSupportedTypes()) {
                if (!isVideoMime(mime)
                        // limit to CDD-required formats for now
                        || !adaptiveFormats.contains(mime)) {
                    continue;
                }
                skipped = false;
                CodecCapabilities caps = info.getCapabilitiesForType(mime);
                assertTrue(
                    info.getName() + " is not adaptive for " + mime,
                    caps.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback));
            }
        }
        if (skipped) {
            MediaUtils.skipTest("no video decoders that are required to be adaptive found");
        }
    }

    private MediaFormat createReasonableVideoFormat(
            CodecCapabilities caps, String mime, boolean encoder, int width, int height) {
        VideoCapabilities vidCaps = caps.getVideoCapabilities();
        MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
        if (encoder) {
            // bitrate
            int maxWidth = vidCaps.getSupportedWidths().getUpper();
            int maxHeight = vidCaps.getSupportedHeightsFor(width).getUpper();
            int maxRate = vidCaps.getSupportedFrameRatesFor(width, height).getUpper().intValue();
            int bitrate = vidCaps.getBitrateRange().clamp(
                    (int)(vidCaps.getBitrateRange().getUpper()
                            / Math.sqrt((double)maxWidth * maxHeight / width / height)));
            Log.i(TAG, "reasonable bitrate for " + width + "x" + height + "@" + maxRate
                    + " " + mime + " = " + bitrate);
            format.setInteger(format.KEY_BIT_RATE, bitrate);
            format.setInteger(format.KEY_FRAME_RATE, maxRate);
            format.setInteger(format.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        }
        return format;
    }

    public void testSecureCodecsAdvertiseSecurePlayback() throws IOException {
        boolean skipped = true;
        for (MediaCodecInfo info : mAllInfos) {
            boolean isEncoder = info.isEncoder();
            if (isEncoder || !info.getName().endsWith(".secure")) {
                continue;
            }
            for (String mime : info.getSupportedTypes()) {
                if (!isVideoMime(mime)) {
                    continue;
                }
                skipped = false;
                CodecCapabilities caps = info.getCapabilitiesForType(mime);
                assertTrue(
                        info.getName() + " does not advertise secure playback",
                        caps.isFeatureSupported(CodecCapabilities.FEATURE_SecurePlayback));
            }
        }
        if (skipped) {
            MediaUtils.skipTest("no video decoders found ending in .secure");
        }
    }

    public void testAllNonTunneledVideoCodecsSupportFlexibleYUV() throws IOException {
        boolean skipped = true;
        for (MediaCodecInfo info : mAllInfos) {
            boolean isEncoder = info.isEncoder();
            for (String mime: info.getSupportedTypes()) {
                if (!isVideoMime(mime)) {
                    continue;
                }
                CodecCapabilities caps = info.getCapabilitiesForType(mime);
                if (caps.isFeatureRequired(CodecCapabilities.FEATURE_TunneledPlayback)
                        || caps.isFeatureRequired(CodecCapabilities.FEATURE_SecurePlayback)) {
                    continue;
                }
                skipped = false;
                boolean found = false;
                for (int c : caps.colorFormats) {
                    if (c == caps.COLOR_FormatYUV420Flexible) {
                        found = true;
                        break;
                    }
                }
                assertTrue(
                    info.getName() + " does not advertise COLOR_FormatYUV420Flexible",
                    found);

                MediaCodec codec = null;
                MediaFormat format = null;
                try {
                    codec = MediaCodec.createByCodecName(info.getName());
                    // implicit assumption that QCIF video is always valid.
                    format = createReasonableVideoFormat(caps, mime, isEncoder, 176, 144);
                    format.setInteger(
                            MediaFormat.KEY_COLOR_FORMAT,
                            caps.COLOR_FormatYUV420Flexible);

                    codec.configure(format, null /* surface */, null /* crypto */,
                            isEncoder ? codec.CONFIGURE_FLAG_ENCODE : 0);
                    MediaFormat configuredFormat =
                            isEncoder ? codec.getInputFormat() : codec.getOutputFormat();
                    Log.d(TAG, "color format is " + configuredFormat.getInteger(
                            MediaFormat.KEY_COLOR_FORMAT));
                    if (isEncoder) {
                        codec.start();
                        int ix = codec.dequeueInputBuffer(TIMEOUT_US);
                        assertNotNull(
                                info.getName() + " encoder has non-flexYUV input buffer #" + ix,
                                codec.getInputImage(ix));
                    } else {
                        // TODO: test these on various decoders (need test streams)
                    }
                } finally {
                    if (codec != null) {
                        codec.release();
                    }
                }
            }
        }
        if (skipped) {
            MediaUtils.skipTest("no non-tunneled/non-secure video decoders found");
        }
    }

    private static MediaFormat createMinFormat(String mime, CodecCapabilities caps) {
        MediaFormat format;
        if (caps.getVideoCapabilities() != null) {
            VideoCapabilities vcaps = caps.getVideoCapabilities();
            int minWidth = vcaps.getSupportedWidths().getLower();
            int minHeight = vcaps.getSupportedHeightsFor(minWidth).getLower();
            int minBitrate = vcaps.getBitrateRange().getLower();
            int minFrameRate = Math.max(vcaps.getSupportedFrameRatesFor(minWidth, minHeight)
                    .getLower().intValue(), 1);
            format = MediaFormat.createVideoFormat(mime, minWidth, minHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, caps.colorFormats[0]);
            format.setInteger(MediaFormat.KEY_BIT_RATE, minBitrate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, minFrameRate);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        } else {
            AudioCapabilities acaps = caps.getAudioCapabilities();
            int minSampleRate = acaps.getSupportedSampleRateRanges()[0].getLower();
            int minChannelCount = 1;
            int minBitrate = acaps.getBitrateRange().getLower();
            format = MediaFormat.createAudioFormat(mime, minSampleRate, minChannelCount);
            format.setInteger(MediaFormat.KEY_BIT_RATE, minBitrate);
        }

        return format;
    }

    private static int getActualMax(
            boolean isEncoder, String name, String mime, CodecCapabilities caps, int max) {
        int flag = isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0;
        MediaFormat format = createMinFormat(mime, caps);
        Log.d(TAG, "Test format " + format);
        Vector<MediaCodec> codecs = new Vector<MediaCodec>();
        MediaCodec codec = null;
        for (int i = 0; i < max; ++i) {
            try {
                Log.d(TAG, "Create codec " + name + " #" + i);
                codec = MediaCodec.createByCodecName(name);
                codec.configure(format, null, null, flag);
                codec.start();
                codecs.add(codec);
                codec = null;
            } catch (IllegalArgumentException e) {
                fail("Got unexpected IllegalArgumentException " + e.getMessage());
            } catch (IOException e) {
                fail("Got unexpected IOException " + e.getMessage());
            } catch (MediaCodec.CodecException e) {
                // ERROR_INSUFFICIENT_RESOURCE is expected as the test keep creating codecs.
                // But other exception should be treated as failure.
                if (e.getErrorCode() == MediaCodec.CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                    Log.d(TAG, "Got CodecException with ERROR_INSUFFICIENT_RESOURCE.");
                    break;
                } else {
                    fail("Unexpected CodecException " + e.getDiagnosticInfo());
                }
            } finally {
                if (codec != null) {
                    Log.d(TAG, "release codec");
                    codec.release();
                    codec = null;
                }
            }
        }
        int actualMax = codecs.size();
        for (int i = 0; i < codecs.size(); ++i) {
            Log.d(TAG, "release codec #" + i);
            codecs.get(i).release();
        }
        codecs.clear();
        return actualMax;
    }

    private boolean knownTypes(String type) {
        return (type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_AAC  ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_AC3      ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_AMR_NB   ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_AMR_WB   ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_EAC3     ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_FLAC     ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_G711_ALAW) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_G711_MLAW) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_MPEG     ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_MSGSM    ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_OPUS     ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_RAW      ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_VORBIS   ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AVC      ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_H263     ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_HEVC     ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_MPEG2    ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_MPEG4    ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_VP8      ) ||
            type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_VP9      ));
    }

    public void testGetMaxSupportedInstances() {
        final int MAX_INSTANCES = 32;
        StringBuilder xmlOverrides = new StringBuilder();
        MediaCodecList allCodecs = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo info : allCodecs.getCodecInfos()) {
            Log.d(TAG, "codec: " + info.getName());
            Log.d(TAG, "  isEncoder = " + info.isEncoder());

            String[] types = info.getSupportedTypes();
            for (int j = 0; j < types.length; ++j) {
                if (!knownTypes(types[j])) {
                    Log.d(TAG, "skipping unknown type " + types[j]);
                    continue;
                }
                Log.d(TAG, "calling getCapabilitiesForType " + types[j]);
                CodecCapabilities caps = info.getCapabilitiesForType(types[j]);
                int max = caps.getMaxSupportedInstances();
                Log.d(TAG, "getMaxSupportedInstances returns " + max);
                assertTrue(max > 0);

                int actualMax = getActualMax(
                        info.isEncoder(), info.getName(), types[j], caps, MAX_INSTANCES);
                Log.d(TAG, "actualMax " + actualMax + " vs reported max " + max);
                if (actualMax < (int)(max * 0.9) || actualMax > (int) Math.ceil(max * 1.1)) {
                    String codec = "<MediaCodec name=\"" + info.getName() +
                            "\" type=\"" + types[j] + "\" >";
                    String limit = "    <Limit name=\"concurrent-instances\" max=\"" +
                            actualMax + "\" />";
                    xmlOverrides.append(codec);
                    xmlOverrides.append("\n");
                    xmlOverrides.append(limit);
                    xmlOverrides.append("\n");
                    xmlOverrides.append("</MediaCodec>\n");
                }
            }
        }

        if (xmlOverrides.length() > 0) {
            String failMessage = "In order to pass the test, please publish following " +
                    "codecs' concurrent instances limit in /etc/media_codecs.xml: \n";
           fail(failMessage + xmlOverrides.toString());
        }
    }
}
