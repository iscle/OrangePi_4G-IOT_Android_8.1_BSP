/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.media.cts.R;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.MediaCodec;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.MediaPerfUtils;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;

public class VideoDecoderPerfTest extends MediaPlayerTestBase {
    private static final String TAG = "VideoDecoderPerfTest";
    private static final String REPORT_LOG_NAME = "CtsMediaTestCases";
    private static final int TOTAL_FRAMES = 30000;
    private static final int MIN_FRAMES = 3000;
    private static final int MAX_TIME_MS = 120000;  // 2 minutes
    private static final int MAX_TEST_TIMEOUT_MS = 300000;  // 5 minutes
    private static final int MIN_TEST_MS = 10000;  // 10 seconds
    private static final int NUMBER_OF_REPEATS = 2;

    private static final String AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String H263 = MediaFormat.MIMETYPE_VIDEO_H263;
    private static final String HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC;
    private static final String MPEG2 = MediaFormat.MIMETYPE_VIDEO_MPEG2;
    private static final String MPEG4 = MediaFormat.MIMETYPE_VIDEO_MPEG4;
    private static final String VP8 = MediaFormat.MIMETYPE_VIDEO_VP8;
    private static final String VP9 = MediaFormat.MIMETYPE_VIDEO_VP9;

    private static final boolean GOOG = true;
    private static final boolean OTHER = false;

    private static final int MAX_SIZE_SAMPLES_IN_MEMORY_BYTES = 12 << 20;  // 12MB
    // each sample contains the buffer and the PTS offset from the frame index
    LinkedList<Pair<ByteBuffer, Double>> mSamplesInMemory = new LinkedList<Pair<ByteBuffer, Double>>();
    private MediaFormat mDecInputFormat;
    private MediaFormat mDecOutputFormat;
    private int mBitrate;

    private Resources mResources;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResources = mContext.getResources();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private void decode(String name, int resourceId, MediaFormat format) throws Exception {
        int width = format.getInteger(MediaFormat.KEY_WIDTH);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT);
        String mime = format.getString(MediaFormat.KEY_MIME);

        // Ensure we can finish this test within the test timeout. Allow 25% slack (4/5).
        long maxTimeMs = Math.min(
                MAX_TEST_TIMEOUT_MS * 4 / 5 / NUMBER_OF_REPEATS, MAX_TIME_MS);
        double measuredFps[] = new double[NUMBER_OF_REPEATS];

        for (int i = 0; i < NUMBER_OF_REPEATS; ++i) {
            // Decode to Surface.
            Log.d(TAG, "round #" + i + ": " + name + " for " + maxTimeMs + " msecs to surface");
            Surface s = getActivity().getSurfaceHolder().getSurface();
            // only verify the result for decode to surface case.
            measuredFps[i] = doDecode(name, resourceId, width, height, s, i, maxTimeMs);

            // We don't test decoding to buffer.
            // Log.d(TAG, "round #" + i + " decode to buffer");
            // doDecode(name, video, width, height, null, i, maxTimeMs);
        }

        String error =
            MediaPerfUtils.verifyAchievableFrameRates(name, mime, width, height, measuredFps);
        assertNull(error, error);
        mSamplesInMemory.clear();
    }

    private double doDecode(
            String name, int video, int w, int h, Surface surface, int round, long maxTimeMs)
            throws Exception {
        AssetFileDescriptor testFd = mResources.openRawResourceFd(video);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(testFd.getFileDescriptor(), testFd.getStartOffset(),
                testFd.getLength());
        extractor.selectTrack(0);
        int trackIndex = extractor.getSampleTrackIndex();
        MediaFormat format = extractor.getTrackFormat(trackIndex);
        String mime = format.getString(MediaFormat.KEY_MIME);

        // use frame rate to calculate PTS offset used for PTS scaling
        double frameRate = 0.; // default - 0 is used for using zero PTS offset
        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
        } else if (!mime.equals(MediaFormat.MIMETYPE_VIDEO_VP8)
                && !mime.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
            fail("need framerate info for video file");
        }

        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;

        if (mSamplesInMemory.size() == 0) {
            int totalMemory = 0;
            ByteBuffer tmpBuf = ByteBuffer.allocate(w * h * 3 / 2);
            int sampleSize = 0;
            int index = 0;
            long firstPTS = 0;
            double presentationOffset = 0.;
            while ((sampleSize = extractor.readSampleData(tmpBuf, 0 /* offset */)) > 0) {
                if (totalMemory + sampleSize > MAX_SIZE_SAMPLES_IN_MEMORY_BYTES) {
                    break;
                }
                if (mSamplesInMemory.size() == 0) {
                    firstPTS = extractor.getSampleTime();
                }
                ByteBuffer copied = ByteBuffer.allocate(sampleSize);
                copied.put(tmpBuf);
                if (frameRate > 0.) {
                    // presentation offset is an offset from the frame index
                    presentationOffset =
                        (extractor.getSampleTime() - firstPTS) * frameRate / 1e6 - index;
                }
                mSamplesInMemory.addLast(Pair.create(copied, presentationOffset));
                totalMemory += sampleSize;
                ++index;
                extractor.advance();
            }
            Log.d(TAG, mSamplesInMemory.size() + " samples in memory for " +
                    (totalMemory / 1024) + " KB.");
            // bitrate normalized to 30fps
            mBitrate = (int)Math.round(totalMemory * 30. * 8. / mSamplesInMemory.size());
        }
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);

        int sampleIndex = 0;

        extractor.release();
        testFd.close();

        MediaCodec codec = MediaCodec.createByCodecName(name);
        VideoCapabilities cap = codec.getCodecInfo().getCapabilitiesForType(mime).getVideoCapabilities();
        frameRate = cap.getSupportedFrameRatesFor(w, h).getUpper();
        codec.configure(format, surface, null /* crypto */, 0 /* flags */);
        codec.start();
        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();
        mDecInputFormat = codec.getInputFormat();

        // start decode loop
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        final long kTimeOutUs = 1000; // 1ms timeout
        double[] frameTimeUsDiff = new double[TOTAL_FRAMES - 1];
        long lastOutputTimeUs = 0;
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int inputNum = 0;
        int outputNum = 0;
        long start = System.currentTimeMillis();
        while (!sawOutputEOS) {
            // handle input
            if (!sawInputEOS) {
                int inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);

                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                    // sample contains the buffer and the PTS offset normalized to frame index
                    Pair<ByteBuffer, Double> sample =
                            mSamplesInMemory.get(sampleIndex++ % mSamplesInMemory.size());
                    sample.first.rewind();
                    int sampleSize = sample.first.remaining();
                    dstBuf.put(sample.first);
                    // use max supported framerate to compute pts
                    long presentationTimeUs = (long)((inputNum + sample.second) * 1e6 / frameRate);

                    long elapsed = System.currentTimeMillis() - start;
                    sawInputEOS = ((++inputNum == TOTAL_FRAMES)
                                   || (elapsed > maxTimeMs)
                                   || (elapsed > MIN_TEST_MS && outputNum > MIN_FRAMES));
                    if (sawInputEOS) {
                        Log.d(TAG, "saw input EOS (stop at sample).");
                    }
                    codec.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            sampleSize,
                            presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                } else {
                    assertEquals(
                            "codec.dequeueInputBuffer() unrecognized return value: " + inputBufIndex,
                            MediaCodec.INFO_TRY_AGAIN_LATER, inputBufIndex);
                }
            }

            // handle output
            int outputBufIndex = codec.dequeueOutputBuffer(info, kTimeOutUs);

            if (outputBufIndex >= 0) {
                if (info.size > 0) { // Disregard 0-sized buffers at the end.
                    long nowUs = (System.nanoTime() + 500) / 1000;
                    if (outputNum > 1) {
                        frameTimeUsDiff[outputNum - 1] = nowUs - lastOutputTimeUs;
                    }
                    lastOutputTimeUs = nowUs;
                    outputNum++;
                }
                codec.releaseOutputBuffer(outputBufIndex, false /* render */);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "saw output EOS.");
                    sawOutputEOS = true;
                }
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mDecOutputFormat = codec.getOutputFormat();
                int width = mDecOutputFormat.getInteger(MediaFormat.KEY_WIDTH);
                int height = mDecOutputFormat.getInteger(MediaFormat.KEY_HEIGHT);
                Log.d(TAG, "output resolution " + width + "x" + height);
            } else {
                assertEquals(
                        "codec.dequeueOutputBuffer() unrecognized return index: "
                                + outputBufIndex,
                        MediaCodec.INFO_TRY_AGAIN_LATER, outputBufIndex);
            }
        }
        long finish = System.currentTimeMillis();
        int validDataNum = outputNum - 1;
        frameTimeUsDiff = Arrays.copyOf(frameTimeUsDiff, validDataNum);
        codec.stop();
        codec.release();

        Log.d(TAG, "input num " + inputNum + " vs output num " + outputNum);

        DeviceReportLog log = new DeviceReportLog(REPORT_LOG_NAME, "video_decoder_performance");
        String message = MediaPerfUtils.addPerformanceHeadersToLog(
                log, "decoder stats: decodeTo=" + ((surface == null) ? "buffer" : "surface"),
                round, name, format, mDecInputFormat, mDecOutputFormat);
        log.addValue("video_res", video, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("decode_to", surface == null ? "buffer" : "surface",
                ResultType.NEUTRAL, ResultUnit.NONE);

        double fps = outputNum / ((finish - start) / 1000.0);
        log.addValue("average_fps", fps, ResultType.HIGHER_BETTER, ResultUnit.FPS);

        MediaUtils.Stats stats = new MediaUtils.Stats(frameTimeUsDiff);
        fps = MediaPerfUtils.addPerformanceStatsToLog(log, stats, message);
        log.submit(getInstrumentation());
        return fps;
    }

    private MediaFormat[] getVideoTrackFormats(int... resources) throws Exception {
        MediaFormat[] formats = new MediaFormat[resources.length];
        for (int i = 0; i < resources.length; ++i) {
            formats[i] = MediaUtils.getTrackFormatForResource(mContext, resources[i], "video/");
        }
        return formats;
    }

    private void count(int[] resources, int numGoog, int numOther) throws Exception {
        MediaFormat[] formats = getVideoTrackFormats(resources);
        MediaUtils.verifyNumCodecs(numGoog,  false /* isEncoder */, true /* isGoog */,  formats);
        MediaUtils.verifyNumCodecs(numOther, false /* isEncoder */, false /* isGoog */, formats);
    }

    private void perf(int[] resources, boolean isGoog, int ix)  throws Exception {
        MediaFormat[] formats = getVideoTrackFormats(resources);
        String[] decoders = MediaUtils.getDecoderNames(isGoog, formats);
        String kind = isGoog ? "Google" : "non-Google";
        if (decoders.length == 0) {
            MediaUtils.skipTest("No " + kind + " decoders for " + Arrays.toString(formats));
            return;
        } else if (ix >= decoders.length) {
            Log.i(TAG, "No more " + kind + " decoders for " + Arrays.toString(formats));
            return;
        }

        String decoderName = decoders[ix];

        // Decode/measure the first supported video resource
        for (int i = 0; i < resources.length; ++i) {
            if (MediaUtils.supports(decoderName, formats[i])) {
                decode(decoderName, resources[i], formats[i]);
                break;
            }
        }
    }

    // Poor man's Parametrized test as this test must still run on CTSv1 runner.

    // The count tests are to ensure this Cts test covers all decoders. Add further
    // tests and change the count if there can be more decoders.

    // AVC tests

    private static final int[] sAvcMedia0320x0240 = {
        R.raw.bbb_s1_320x240_mp4_h264_mp2_800kbps_30fps_aac_lc_5ch_240kbps_44100hz,
    };

    public void testAvcCount0320x0240() throws Exception { count(sAvcMedia0320x0240, 1, 4); }
    public void testAvcGoog0Perf0320x0240() throws Exception { perf(sAvcMedia0320x0240, GOOG, 0); }
    public void testAvcOther0Perf0320x0240() throws Exception { perf(sAvcMedia0320x0240, OTHER, 0); }
    public void testAvcOther1Perf0320x0240() throws Exception { perf(sAvcMedia0320x0240, OTHER, 1); }
    public void testAvcOther2Perf0320x0240() throws Exception { perf(sAvcMedia0320x0240, OTHER, 2); }
    public void testAvcOther3Perf0320x0240() throws Exception { perf(sAvcMedia0320x0240, OTHER, 3); }

    private static final int[] sAvcMedia0720x0480 = {
        R.raw.bbb_s1_720x480_mp4_h264_mp3_2mbps_30fps_aac_lc_5ch_320kbps_48000hz,
    };

    public void testAvcCount0720x0480() throws Exception { count(sAvcMedia0720x0480, 1, 4); }
    public void testAvcGoog0Perf0720x0480() throws Exception { perf(sAvcMedia0720x0480, GOOG, 0); }
    public void testAvcOther0Perf0720x0480() throws Exception { perf(sAvcMedia0720x0480, OTHER, 0); }
    public void testAvcOther1Perf0720x0480() throws Exception { perf(sAvcMedia0720x0480, OTHER, 1); }
    public void testAvcOther2Perf0720x0480() throws Exception { perf(sAvcMedia0720x0480, OTHER, 2); }
    public void testAvcOther3Perf0720x0480() throws Exception { perf(sAvcMedia0720x0480, OTHER, 3); }

    // prefer highest effective bitrate, then high profile
    private static final int[] sAvcMedia1280x0720 = {
        R.raw.bbb_s4_1280x720_mp4_h264_mp31_8mbps_30fps_aac_he_mono_40kbps_44100hz,
        R.raw.bbb_s3_1280x720_mp4_h264_hp32_8mbps_60fps_aac_he_v2_stereo_48kbps_48000hz,
        R.raw.bbb_s3_1280x720_mp4_h264_mp32_8mbps_60fps_aac_he_v2_6ch_144kbps_44100hz,
    };

    public void testAvcCount1280x0720() throws Exception { count(sAvcMedia1280x0720, 1, 4); }
    public void testAvcGoog0Perf1280x0720() throws Exception { perf(sAvcMedia1280x0720, GOOG, 0); }
    public void testAvcOther0Perf1280x0720() throws Exception { perf(sAvcMedia1280x0720, OTHER, 0); }
    public void testAvcOther1Perf1280x0720() throws Exception { perf(sAvcMedia1280x0720, OTHER, 1); }
    public void testAvcOther2Perf1280x0720() throws Exception { perf(sAvcMedia1280x0720, OTHER, 2); }
    public void testAvcOther3Perf1280x0720() throws Exception { perf(sAvcMedia1280x0720, OTHER, 3); }

    // prefer highest effective bitrate, then high profile
    private static final int[] sAvcMedia1920x1080 = {
        R.raw.bbb_s4_1920x1080_wide_mp4_h264_hp4_20mbps_30fps_aac_lc_6ch_384kbps_44100hz,
        R.raw.bbb_s4_1920x1080_wide_mp4_h264_mp4_20mbps_30fps_aac_he_5ch_200kbps_44100hz,
        R.raw.bbb_s2_1920x1080_mp4_h264_hp42_20mbps_60fps_aac_lc_6ch_384kbps_48000hz,
        R.raw.bbb_s2_1920x1080_mp4_h264_mp42_20mbps_60fps_aac_he_v2_5ch_160kbps_48000hz,
    };

    public void testAvcCount1920x1080() throws Exception { count(sAvcMedia1920x1080, 1, 4); }
    public void testAvcGoog0Perf1920x1080() throws Exception { perf(sAvcMedia1920x1080, GOOG, 0); }
    public void testAvcOther0Perf1920x1080() throws Exception { perf(sAvcMedia1920x1080, OTHER, 0); }
    public void testAvcOther1Perf1920x1080() throws Exception { perf(sAvcMedia1920x1080, OTHER, 1); }
    public void testAvcOther2Perf1920x1080() throws Exception { perf(sAvcMedia1920x1080, OTHER, 2); }
    public void testAvcOther3Perf1920x1080() throws Exception { perf(sAvcMedia1920x1080, OTHER, 3); }

    // H263 tests

    private static final int[] sH263Media0176x0144 = {
        R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz,
    };

    public void testH263Count0176x0144() throws Exception { count(sH263Media0176x0144, 1, 2); }
    public void testH263Goog0Perf0176x0144() throws Exception { perf(sH263Media0176x0144, GOOG, 0); }
    public void testH263Other0Perf0176x0144() throws Exception { perf(sH263Media0176x0144, OTHER, 0); }
    public void testH263Other1Perf0176x0144() throws Exception { perf(sH263Media0176x0144, OTHER, 1); }

    private static final int[] sH263Media0352x0288 = {
        R.raw.video_352x288_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz,
    };

    public void testH263Count0352x0288() throws Exception { count(sH263Media0352x0288, 1, 2); }
    public void testH263Goog0Perf0352x0288() throws Exception { perf(sH263Media0352x0288, GOOG, 0); }
    public void testH263Other0Perf0352x0288() throws Exception { perf(sH263Media0352x0288, OTHER, 0); }
    public void testH263Other1Perf0352x0288() throws Exception { perf(sH263Media0352x0288, OTHER, 1); }

    // No media for H263 704x576

    // No media for H263 1408x1152

    // HEVC tests

    private static final int[] sHevcMedia0352x0288 = {
        R.raw.bbb_s1_352x288_mp4_hevc_mp2_600kbps_30fps_aac_he_stereo_96kbps_48000hz,
    };

    public void testHevcCount0352x0288() throws Exception { count(sHevcMedia0352x0288, 1, 4); }
    public void testHevcGoog0Perf0352x0288() throws Exception { perf(sHevcMedia0352x0288, GOOG, 0); }
    public void testHevcOther0Perf0352x0288() throws Exception { perf(sHevcMedia0352x0288, OTHER, 0); }
    public void testHevcOther1Perf0352x0288() throws Exception { perf(sHevcMedia0352x0288, OTHER, 1); }
    public void testHevcOther2Perf0352x0288() throws Exception { perf(sHevcMedia0352x0288, OTHER, 2); }
    public void testHevcOther3Perf0352x0288() throws Exception { perf(sHevcMedia0352x0288, OTHER, 3); }

    private static final int[] sHevcMedia0640x0360 = {
        R.raw.bbb_s1_640x360_mp4_hevc_mp21_1600kbps_30fps_aac_he_6ch_288kbps_44100hz,
    };

    public void testHevcCount0640x0360() throws Exception { count(sHevcMedia0640x0360, 1, 4); }
    public void testHevcGoog0Perf0640x0360() throws Exception { perf(sHevcMedia0640x0360, GOOG, 0); }
    public void testHevcOther0Perf0640x0360() throws Exception { perf(sHevcMedia0640x0360, OTHER, 0); }
    public void testHevcOther1Perf0640x0360() throws Exception { perf(sHevcMedia0640x0360, OTHER, 1); }
    public void testHevcOther2Perf0640x0360() throws Exception { perf(sHevcMedia0640x0360, OTHER, 2); }
    public void testHevcOther3Perf0640x0360() throws Exception { perf(sHevcMedia0640x0360, OTHER, 3); }

    private static final int[] sHevcMedia0720x0480 = {
        R.raw.bbb_s1_720x480_mp4_hevc_mp3_1600kbps_30fps_aac_he_6ch_240kbps_48000hz,
    };

    public void testHevcCount0720x0480() throws Exception { count(sHevcMedia0720x0480, 1, 4); }
    public void testHevcGoog0Perf0720x0480() throws Exception { perf(sHevcMedia0720x0480, GOOG, 0); }
    public void testHevcOther0Perf0720x0480() throws Exception { perf(sHevcMedia0720x0480, OTHER, 0); }
    public void testHevcOther1Perf0720x0480() throws Exception { perf(sHevcMedia0720x0480, OTHER, 1); }
    public void testHevcOther2Perf0720x0480() throws Exception { perf(sHevcMedia0720x0480, OTHER, 2); }
    public void testHevcOther3Perf0720x0480() throws Exception { perf(sHevcMedia0720x0480, OTHER, 3); }

    private static final int[] sHevcMedia1280x0720 = {
        R.raw.bbb_s4_1280x720_mp4_hevc_mp31_4mbps_30fps_aac_he_stereo_80kbps_32000hz,
    };

    public void testHevcCount1280x0720() throws Exception { count(sHevcMedia1280x0720, 1, 4); }
    public void testHevcGoog0Perf1280x0720() throws Exception { perf(sHevcMedia1280x0720, GOOG, 0); }
    public void testHevcOther0Perf1280x0720() throws Exception { perf(sHevcMedia1280x0720, OTHER, 0); }
    public void testHevcOther1Perf1280x0720() throws Exception { perf(sHevcMedia1280x0720, OTHER, 1); }
    public void testHevcOther2Perf1280x0720() throws Exception { perf(sHevcMedia1280x0720, OTHER, 2); }
    public void testHevcOther3Perf1280x0720() throws Exception { perf(sHevcMedia1280x0720, OTHER, 3); }

    private static final int[] sHevcMedia1920x1080 = {
        R.raw.bbb_s2_1920x1080_mp4_hevc_mp41_10mbps_60fps_aac_lc_6ch_384kbps_22050hz,
    };

    public void testHevcCount1920x1080() throws Exception { count(sHevcMedia1920x1080, 1, 4); }
    public void testHevcGoog0Perf1920x1080() throws Exception { perf(sHevcMedia1920x1080, GOOG, 0); }
    public void testHevcOther0Perf1920x1080() throws Exception { perf(sHevcMedia1920x1080, OTHER, 0); }
    public void testHevcOther1Perf1920x1080() throws Exception { perf(sHevcMedia1920x1080, OTHER, 1); }
    public void testHevcOther2Perf1920x1080() throws Exception { perf(sHevcMedia1920x1080, OTHER, 2); }
    public void testHevcOther3Perf1920x1080() throws Exception { perf(sHevcMedia1920x1080, OTHER, 3); }

    // prefer highest effective bitrate
    private static final int[] sHevcMedia3840x2160 = {
        R.raw.bbb_s4_3840x2160_mp4_hevc_mp5_20mbps_30fps_aac_lc_6ch_384kbps_24000hz,
        R.raw.bbb_s2_3840x2160_mp4_hevc_mp51_20mbps_60fps_aac_lc_6ch_384kbps_32000hz,
    };

    public void testHevcCount3840x2160() throws Exception { count(sHevcMedia3840x2160, 1, 4); }
    public void testHevcGoog0Perf3840x2160() throws Exception { perf(sHevcMedia3840x2160, GOOG, 0); }
    public void testHevcOther0Perf3840x2160() throws Exception { perf(sHevcMedia3840x2160, OTHER, 0); }
    public void testHevcOther1Perf3840x2160() throws Exception { perf(sHevcMedia3840x2160, OTHER, 1); }
    public void testHevcOther2Perf3840x2160() throws Exception { perf(sHevcMedia3840x2160, OTHER, 2); }
    public void testHevcOther3Perf3840x2160() throws Exception { perf(sHevcMedia3840x2160, OTHER, 3); }

    // MPEG2 tests

    // No media for MPEG2 176x144

    // No media for MPEG2 352x288

    // No media for MPEG2 640x480

    // No media for MPEG2 1280x720

    // No media for MPEG2 1920x1080

    // MPEG4 tests

    private static final int[] sMpeg4Media0176x0144 = {
        R.raw.video_176x144_mp4_mpeg4_300kbps_25fps_aac_stereo_128kbps_44100hz,
    };

    public void testMpeg4Count0176x0144() throws Exception { count(sMpeg4Media0176x0144, 1, 4); }
    public void testMpeg4Goog0Perf0176x0144() throws Exception { perf(sMpeg4Media0176x0144, GOOG, 0); }
    public void testMpeg4Other0Perf0176x0144() throws Exception { perf(sMpeg4Media0176x0144, OTHER, 0); }
    public void testMpeg4Other1Perf0176x0144() throws Exception { perf(sMpeg4Media0176x0144, OTHER, 1); }
    public void testMpeg4Other2Perf0176x0144() throws Exception { perf(sMpeg4Media0176x0144, OTHER, 2); }
    public void testMpeg4Other3Perf0176x0144() throws Exception { perf(sMpeg4Media0176x0144, OTHER, 3); }

    private static final int[] sMpeg4Media0480x0360 = {
        R.raw.video_480x360_mp4_mpeg4_860kbps_25fps_aac_stereo_128kbps_44100hz,
    };

    public void testMpeg4Count0480x0360() throws Exception { count(sMpeg4Media0480x0360, 1, 4); }
    public void testMpeg4Goog0Perf0480x0360() throws Exception { perf(sMpeg4Media0480x0360, GOOG, 0); }
    public void testMpeg4Other0Perf0480x0360() throws Exception { perf(sMpeg4Media0480x0360, OTHER, 0); }
    public void testMpeg4Other1Perf0480x0360() throws Exception { perf(sMpeg4Media0480x0360, OTHER, 1); }
    public void testMpeg4Other2Perf0480x0360() throws Exception { perf(sMpeg4Media0480x0360, OTHER, 2); }
    public void testMpeg4Other3Perf0480x0360() throws Exception { perf(sMpeg4Media0480x0360, OTHER, 3); }

   // No media for MPEG4 640x480

    private static final int[] sMpeg4Media1280x0720 = {
        R.raw.video_1280x720_mp4_mpeg4_1000kbps_25fps_aac_stereo_128kbps_44100hz,
    };

    public void testMpeg4Count1280x0720() throws Exception { count(sMpeg4Media1280x0720, 1, 4); }
    public void testMpeg4Goog0Perf1280x0720() throws Exception { perf(sMpeg4Media1280x0720, GOOG, 0); }
    public void testMpeg4Other0Perf1280x0720() throws Exception { perf(sMpeg4Media1280x0720, OTHER, 0); }
    public void testMpeg4Other1Perf1280x0720() throws Exception { perf(sMpeg4Media1280x0720, OTHER, 1); }
    public void testMpeg4Other2Perf1280x0720() throws Exception { perf(sMpeg4Media1280x0720, OTHER, 2); }
    public void testMpeg4Other3Perf1280x0720() throws Exception { perf(sMpeg4Media1280x0720, OTHER, 3); }

    // VP8 tests

    private static final int[] sVp8Media0320x0180 = {
        R.raw.bbb_s1_320x180_webm_vp8_800kbps_30fps_opus_5ch_320kbps_48000hz,
    };

    public void testVp8Count0320x0180() throws Exception { count(sVp8Media0320x0180, 1, 2); }
    public void testVp8Goog0Perf0320x0180() throws Exception { perf(sVp8Media0320x0180, GOOG, 0); }
    public void testVp8Other0Perf0320x0180() throws Exception { perf(sVp8Media0320x0180, OTHER, 0); }
    public void testVp8Other1Perf0320x0180() throws Exception { perf(sVp8Media0320x0180, OTHER, 1); }

    private static final int[] sVp8Media0640x0360 = {
        R.raw.bbb_s1_640x360_webm_vp8_2mbps_30fps_vorbis_5ch_320kbps_48000hz,
    };

    public void testVp8Count0640x0360() throws Exception { count(sVp8Media0640x0360, 1, 2); }
    public void testVp8Goog0Perf0640x0360() throws Exception { perf(sVp8Media0640x0360, GOOG, 0); }
    public void testVp8Other0Perf0640x0360() throws Exception { perf(sVp8Media0640x0360, OTHER, 0); }
    public void testVp8Other1Perf0640x0360() throws Exception { perf(sVp8Media0640x0360, OTHER, 1); }

    // prefer highest effective bitrate
    private static final int[] sVp8Media1280x0720 = {
        R.raw.bbb_s4_1280x720_webm_vp8_8mbps_30fps_opus_mono_64kbps_48000hz,
        R.raw.bbb_s3_1280x720_webm_vp8_8mbps_60fps_opus_6ch_384kbps_48000hz,
    };

    public void testVp8Count1280x0720() throws Exception { count(sVp8Media1280x0720, 1, 2); }
    public void testVp8Goog0Perf1280x0720() throws Exception { perf(sVp8Media1280x0720, GOOG, 0); }
    public void testVp8Other0Perf1280x0720() throws Exception { perf(sVp8Media1280x0720, OTHER, 0); }
    public void testVp8Other1Perf1280x0720() throws Exception { perf(sVp8Media1280x0720, OTHER, 1); }

    // prefer highest effective bitrate
    private static final int[] sVp8Media1920x1080 = {
        R.raw.bbb_s4_1920x1080_wide_webm_vp8_20mbps_30fps_vorbis_6ch_384kbps_44100hz,
        R.raw.bbb_s2_1920x1080_webm_vp8_20mbps_60fps_vorbis_6ch_384kbps_48000hz,
    };

    public void testVp8Count1920x1080() throws Exception { count(sVp8Media1920x1080, 1, 2); }
    public void testVp8Goog0Perf1920x1080() throws Exception { perf(sVp8Media1920x1080, GOOG, 0); }
    public void testVp8Other0Perf1920x1080() throws Exception { perf(sVp8Media1920x1080, OTHER, 0); }
    public void testVp8Other1Perf1920x1080() throws Exception { perf(sVp8Media1920x1080, OTHER, 1); }

    // VP9 tests

    private static final int[] sVp9Media0320x0180 = {
        R.raw.bbb_s1_320x180_webm_vp9_0p11_600kbps_30fps_vorbis_mono_64kbps_48000hz,
    };

    public void testVp9Count0320x0180() throws Exception { count(sVp9Media0320x0180, 1, 4); }
    public void testVp9Goog0Perf0320x0180() throws Exception { perf(sVp9Media0320x0180, GOOG, 0); }
    public void testVp9Other0Perf0320x0180() throws Exception { perf(sVp9Media0320x0180, OTHER, 0); }
    public void testVp9Other1Perf0320x0180() throws Exception { perf(sVp9Media0320x0180, OTHER, 1); }
    public void testVp9Other2Perf0320x0180() throws Exception { perf(sVp9Media0320x0180, OTHER, 2); }
    public void testVp9Other3Perf0320x0180() throws Exception { perf(sVp9Media0320x0180, OTHER, 3); }

    private static final int[] sVp9Media0640x0360 = {
        R.raw.bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz,
    };

    public void testVp9Count0640x0360() throws Exception { count(sVp9Media0640x0360, 1, 4); }
    public void testVp9Goog0Perf0640x0360() throws Exception { perf(sVp9Media0640x0360, GOOG, 0); }
    public void testVp9Other0Perf0640x0360() throws Exception { perf(sVp9Media0640x0360, OTHER, 0); }
    public void testVp9Other1Perf0640x0360() throws Exception { perf(sVp9Media0640x0360, OTHER, 1); }
    public void testVp9Other2Perf0640x0360() throws Exception { perf(sVp9Media0640x0360, OTHER, 2); }
    public void testVp9Other3Perf0640x0360() throws Exception { perf(sVp9Media0640x0360, OTHER, 3); }

    private static final int[] sVp9Media1280x0720 = {
        R.raw.bbb_s4_1280x720_webm_vp9_0p31_4mbps_30fps_opus_stereo_128kbps_48000hz,
    };

    public void testVp9Count1280x0720() throws Exception { count(sVp9Media1280x0720, 1, 4); }
    public void testVp9Goog0Perf1280x0720() throws Exception { perf(sVp9Media1280x0720, GOOG, 0); }
    public void testVp9Other0Perf1280x0720() throws Exception { perf(sVp9Media1280x0720, OTHER, 0); }
    public void testVp9Other1Perf1280x0720() throws Exception { perf(sVp9Media1280x0720, OTHER, 1); }
    public void testVp9Other2Perf1280x0720() throws Exception { perf(sVp9Media1280x0720, OTHER, 2); }
    public void testVp9Other3Perf1280x0720() throws Exception { perf(sVp9Media1280x0720, OTHER, 3); }

    private static final int[] sVp9Media1920x1080 = {
        R.raw.bbb_s2_1920x1080_webm_vp9_0p41_10mbps_60fps_vorbis_6ch_384kbps_22050hz,
    };

    public void testVp9Count1920x1080() throws Exception { count(sVp9Media1920x1080, 1, 4); }
    public void testVp9Goog0Perf1920x1080() throws Exception { perf(sVp9Media1920x1080, GOOG, 0); }
    public void testVp9Other0Perf1920x1080() throws Exception { perf(sVp9Media1920x1080, OTHER, 0); }
    public void testVp9Other1Perf1920x1080() throws Exception { perf(sVp9Media1920x1080, OTHER, 1); }
    public void testVp9Other2Perf1920x1080() throws Exception { perf(sVp9Media1920x1080, OTHER, 2); }
    public void testVp9Other3Perf1920x1080() throws Exception { perf(sVp9Media1920x1080, OTHER, 3); }

    // prefer highest effective bitrate
    private static final int[] sVp9Media3840x2160 = {
        R.raw.bbb_s4_3840x2160_webm_vp9_0p5_20mbps_30fps_vorbis_6ch_384kbps_24000hz,
        R.raw.bbb_s2_3840x2160_webm_vp9_0p51_20mbps_60fps_vorbis_6ch_384kbps_32000hz,
    };

    public void testVp9Count3840x2160() throws Exception { count(sVp9Media3840x2160, 1, 4); }
    public void testVp9Goog0Perf3840x2160() throws Exception { perf(sVp9Media3840x2160, GOOG, 0); }
    public void testVp9Other0Perf3840x2160() throws Exception { perf(sVp9Media3840x2160, OTHER, 0); }
    public void testVp9Other1Perf3840x2160() throws Exception { perf(sVp9Media3840x2160, OTHER, 1); }
    public void testVp9Other2Perf3840x2160() throws Exception { perf(sVp9Media3840x2160, OTHER, 2); }
    public void testVp9Other3Perf3840x2160() throws Exception { perf(sVp9Media3840x2160, OTHER, 3); }
}

