/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import android.opengl.GLES20;
import javax.microedition.khronos.opengles.GL10;

import java.io.IOException;
import java.lang.System;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.zip.CRC32;

public class AdaptivePlaybackTest extends MediaPlayerTestBase {
    private static final String TAG = "AdaptivePlaybackTest";
    private boolean sanity = false;
    private static final int MIN_FRAMES_BEFORE_DRC = 2;

    public Iterable<Codec> H264(CodecFactory factory) {
        return factory.createCodecList(
                mContext,
                MediaFormat.MIMETYPE_VIDEO_AVC,
                "OMX.google.h264.decoder",
                R.raw.video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz,
                R.raw.video_1280x720_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz,
                R.raw.bbb_s1_720x480_mp4_h264_mp3_2mbps_30fps_aac_lc_5ch_320kbps_48000hz);
    }

    public Iterable<Codec> HEVC(CodecFactory factory) {
        return factory.createCodecList(
                mContext,
                MediaFormat.MIMETYPE_VIDEO_HEVC,
                "OMX.google.hevc.decoder",
                R.raw.bbb_s1_720x480_mp4_hevc_mp3_1600kbps_30fps_aac_he_6ch_240kbps_48000hz,
                R.raw.bbb_s4_1280x720_mp4_hevc_mp31_4mbps_30fps_aac_he_stereo_80kbps_32000hz,
                R.raw.bbb_s1_352x288_mp4_hevc_mp2_600kbps_30fps_aac_he_stereo_96kbps_48000hz);
    }

    public Iterable<Codec> H263(CodecFactory factory) {
        return factory.createCodecList(
                mContext,
                MediaFormat.MIMETYPE_VIDEO_H263,
                "OMX.google.h263.decoder",
                R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz,
                R.raw.video_352x288_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz);
    }

    public Iterable<Codec> Mpeg4(CodecFactory factory) {
        return factory.createCodecList(
                mContext,
                MediaFormat.MIMETYPE_VIDEO_MPEG4,
                "OMX.google.mpeg4.decoder",

                R.raw.video_1280x720_mp4_mpeg4_1000kbps_25fps_aac_stereo_128kbps_44100hz,
                R.raw.video_480x360_mp4_mpeg4_860kbps_25fps_aac_stereo_128kbps_44100hz,
                R.raw.video_176x144_mp4_mpeg4_300kbps_25fps_aac_stereo_128kbps_44100hz);
    }

    public Iterable<Codec> VP8(CodecFactory factory) {
        return factory.createCodecList(
                mContext,
                MediaFormat.MIMETYPE_VIDEO_VP8,
                "OMX.google.vp8.decoder",
                R.raw.video_480x360_webm_vp8_333kbps_25fps_vorbis_stereo_128kbps_48000hz,
                R.raw.bbb_s3_1280x720_webm_vp8_8mbps_60fps_opus_6ch_384kbps_48000hz,
                R.raw.bbb_s1_320x180_webm_vp8_800kbps_30fps_opus_5ch_320kbps_48000hz);
    }

    public Iterable<Codec> VP9(CodecFactory factory) {
        return factory.createCodecList(
                mContext,
                MediaFormat.MIMETYPE_VIDEO_VP9,
                "OMX.google.vp9.decoder",
                R.raw.video_480x360_webm_vp9_333kbps_25fps_vorbis_stereo_128kbps_48000hz,
                R.raw.bbb_s4_1280x720_webm_vp9_0p31_4mbps_30fps_opus_stereo_128kbps_48000hz,
                R.raw.bbb_s1_320x180_webm_vp9_0p11_600kbps_30fps_vorbis_mono_64kbps_48000hz);
    }

    CodecFactory ALL = new CodecFactory();
    CodecFactory SW  = new SWCodecFactory();
    CodecFactory HW  = new HWCodecFactory();

    public Iterable<Codec> H264()  { return H264(ALL);  }
    public Iterable<Codec> HEVC()  { return HEVC(ALL);  }
    public Iterable<Codec> VP8()   { return VP8(ALL);   }
    public Iterable<Codec> VP9()   { return VP9(ALL);   }
    public Iterable<Codec> Mpeg4() { return Mpeg4(ALL); }
    public Iterable<Codec> H263()  { return H263(ALL);  }

    public Iterable<Codec> AllCodecs() {
        return chain(H264(ALL), HEVC(ALL), VP8(ALL), VP9(ALL), Mpeg4(ALL), H263(ALL));
    }

    public Iterable<Codec> SWCodecs() {
        return chain(H264(SW), HEVC(SW), VP8(SW), VP9(SW), Mpeg4(SW), H263(SW));
    }

    public Iterable<Codec> HWCodecs() {
        return chain(H264(HW), HEVC(HW), VP8(HW), VP9(HW), Mpeg4(HW), H263(HW));
    }

    /* tests for adaptive codecs */
    Test adaptiveEarlyEos     = new EarlyEosTest().adaptive();
    Test adaptiveEosFlushSeek = new EosFlushSeekTest().adaptive();
    Test adaptiveSkipAhead    = new AdaptiveSkipTest(true /* forward */);
    Test adaptiveSkipBack     = new AdaptiveSkipTest(false /* forward */);

    /* DRC tests for adaptive codecs */
    Test adaptiveReconfigDrc      = new ReconfigDrcTest().adaptive();
    Test adaptiveSmallReconfigDrc = new ReconfigDrcTest().adaptiveSmall();
    Test adaptiveDrc      = new AdaptiveDrcTest(); /* adaptive */
    Test adaptiveSmallDrc = new AdaptiveDrcTest().adaptiveSmall();

    /* tests for regular codecs */
    Test earlyEos          = new EarlyEosTest();
    Test eosFlushSeek      = new EosFlushSeekTest();
    Test flushConfigureDrc = new ReconfigDrcTest();

    Test[] allTests = {
        adaptiveEarlyEos,
        adaptiveEosFlushSeek,
        adaptiveSkipAhead,
        adaptiveSkipBack,
        adaptiveReconfigDrc,
        adaptiveSmallReconfigDrc,
        adaptiveDrc,
        adaptiveSmallDrc,
        earlyEos,
        eosFlushSeek,
        flushConfigureDrc,
    };

    /* helpers to run sets of tests */
    public void runEOS() { ex(AllCodecs(), new Test[] {
        adaptiveEarlyEos,
        adaptiveEosFlushSeek,
        adaptiveReconfigDrc,
        adaptiveSmallReconfigDrc,
        earlyEos,
        eosFlushSeek,
        flushConfigureDrc,
    }); }

    public void runAll() { ex(AllCodecs(), allTests); }
    public void runSW()  { ex(SWCodecs(),  allTests); }
    public void runHW()  { ex(HWCodecs(),  allTests); }

    public void sanityAll() { sanity = true; try { runAll(); } finally { sanity = false; } }
    public void sanitySW()  { sanity = true; try { runSW();  } finally { sanity = false; } }
    public void sanityHW()  { sanity = true; try { runHW();  } finally { sanity = false; } }

    public void runH264()  { ex(H264(),  allTests); }
    public void runHEVC()  { ex(HEVC(),  allTests); }
    public void runVP8()   { ex(VP8(),   allTests); }
    public void runVP9()   { ex(VP9(),   allTests); }
    public void runMpeg4() { ex(Mpeg4(), allTests); }
    public void runH263()  { ex(H263(),  allTests); }

    public void onlyH264HW()  { ex(H264(HW),  allTests); }
    public void onlyHEVCHW()  { ex(HEVC(HW),  allTests); }
    public void onlyVP8HW()   { ex(VP8(HW),   allTests); }
    public void onlyVP9HW()   { ex(VP9(HW),   allTests); }
    public void onlyMpeg4HW() { ex(Mpeg4(HW), allTests); }
    public void onlyH263HW()  { ex(H263(HW),  allTests); }

    public void onlyH264SW()  { ex(H264(SW),  allTests); }
    public void onlyHEVCSW()  { ex(HEVC(SW),  allTests); }
    public void onlyVP8SW()   { ex(VP8(SW),   allTests); }
    public void onlyVP9SW()   { ex(VP9(SW),   allTests); }
    public void onlyMpeg4SW() { ex(Mpeg4(SW), allTests); }
    public void onlyH263SW()  { ex(H263(SW),  allTests); }

    public void bytebuffer() { ex(H264(SW), new EarlyEosTest().byteBuffer()); }
    public void onlyTexture() { ex(H264(HW), new EarlyEosTest().texture()); }

    /* inidividual tests */
    public void testH264_adaptiveEarlyEos()  { ex(H264(),  adaptiveEarlyEos); }
    public void testHEVC_adaptiveEarlyEos()  { ex(HEVC(),  adaptiveEarlyEos); }
    public void testVP8_adaptiveEarlyEos()   { ex(VP8(),   adaptiveEarlyEos); }
    public void testVP9_adaptiveEarlyEos()   { ex(VP9(),   adaptiveEarlyEos); }
    public void testMpeg4_adaptiveEarlyEos() { ex(Mpeg4(), adaptiveEarlyEos); }
    public void testH263_adaptiveEarlyEos()  { ex(H263(),  adaptiveEarlyEos); }

    public void testH264_adaptiveEosFlushSeek()  { ex(H264(),  adaptiveEosFlushSeek); }
    public void testHEVC_adaptiveEosFlushSeek()  { ex(HEVC(),  adaptiveEosFlushSeek); }
    public void testVP8_adaptiveEosFlushSeek()   { ex(VP8(),   adaptiveEosFlushSeek); }
    public void testVP9_adaptiveEosFlushSeek()   { ex(VP9(),   adaptiveEosFlushSeek); }
    public void testMpeg4_adaptiveEosFlushSeek() { ex(Mpeg4(), adaptiveEosFlushSeek); }
    public void testH263_adaptiveEosFlushSeek()  { ex(H263(),  adaptiveEosFlushSeek); }

    public void testH264_adaptiveSkipAhead()  { ex(H264(),  adaptiveSkipAhead); }
    public void testHEVC_adaptiveSkipAhead()  { ex(HEVC(),  adaptiveSkipAhead); }
    public void testVP8_adaptiveSkipAhead()   { ex(VP8(),   adaptiveSkipAhead); }
    public void testVP9_adaptiveSkipAhead()   { ex(VP9(),   adaptiveSkipAhead); }
    public void testMpeg4_adaptiveSkipAhead() { ex(Mpeg4(), adaptiveSkipAhead); }
    public void testH263_adaptiveSkipAhead()  { ex(H263(),  adaptiveSkipAhead); }

    public void testH264_adaptiveSkipBack()  { ex(H264(),  adaptiveSkipBack); }
    public void testHEVC_adaptiveSkipBack()  { ex(HEVC(),  adaptiveSkipBack); }
    public void testVP8_adaptiveSkipBack()   { ex(VP8(),   adaptiveSkipBack); }
    public void testVP9_adaptiveSkipBack()   { ex(VP9(),   adaptiveSkipBack); }
    public void testMpeg4_adaptiveSkipBack() { ex(Mpeg4(), adaptiveSkipBack); }
    public void testH263_adaptiveSkipBack()  { ex(H263(),  adaptiveSkipBack); }

    public void testH264_adaptiveReconfigDrc()  { ex(H264(),  adaptiveReconfigDrc); }
    public void testHEVC_adaptiveReconfigDrc()  { ex(HEVC(),  adaptiveReconfigDrc); }
    public void testVP8_adaptiveReconfigDrc()   { ex(VP8(),   adaptiveReconfigDrc); }
    public void testVP9_adaptiveReconfigDrc()   { ex(VP9(),   adaptiveReconfigDrc); }
    public void testMpeg4_adaptiveReconfigDrc() { ex(Mpeg4(), adaptiveReconfigDrc); }
    public void testH263_adaptiveReconfigDrc()  { ex(H263(),  adaptiveReconfigDrc); }

    public void testH264_adaptiveSmallReconfigDrc()  { ex(H264(),  adaptiveSmallReconfigDrc); }
    public void testHEVC_adaptiveSmallReconfigDrc()  { ex(HEVC(),  adaptiveSmallReconfigDrc); }
    public void testVP8_adaptiveSmallReconfigDrc()   { ex(VP8(),   adaptiveSmallReconfigDrc); }
    public void testVP9_adaptiveSmallReconfigDrc()   { ex(VP9(),   adaptiveSmallReconfigDrc); }
    public void testMpeg4_adaptiveSmallReconfigDrc() { ex(Mpeg4(), adaptiveSmallReconfigDrc); }
    public void testH263_adaptiveSmallReconfigDrc()  { ex(H263(),  adaptiveSmallReconfigDrc); }

    public void testH264_adaptiveDrc() { ex(H264(), adaptiveDrc); }
    public void testHEVC_adaptiveDrc() { ex(HEVC(), adaptiveDrc); }
    public void testVP8_adaptiveDrc()  { ex(VP8(),  adaptiveDrc); }
    public void testVP9_adaptiveDrc()  { ex(VP9(),  adaptiveDrc); }
    public void testMpeg4_adaptiveDrc() { ex(Mpeg4(), adaptiveDrc); }
    public void testH263_adaptiveDrc() { ex(H263(), adaptiveDrc); }

    public void testH264_adaptiveDrcEarlyEos() { ex(H264(), new AdaptiveDrcEarlyEosTest()); }
    public void testHEVC_adaptiveDrcEarlyEos() { ex(HEVC(), new AdaptiveDrcEarlyEosTest()); }
    public void testVP8_adaptiveDrcEarlyEos()  { ex(VP8(),  new AdaptiveDrcEarlyEosTest()); }
    public void testVP9_adaptiveDrcEarlyEos()  { ex(VP9(),  new AdaptiveDrcEarlyEosTest()); }

    public void testH264_adaptiveSmallDrc()  { ex(H264(),  adaptiveSmallDrc); }
    public void testHEVC_adaptiveSmallDrc()  { ex(HEVC(),  adaptiveSmallDrc); }
    public void testVP8_adaptiveSmallDrc()   { ex(VP8(),   adaptiveSmallDrc); }
    public void testVP9_adaptiveSmallDrc()   { ex(VP9(),   adaptiveSmallDrc); }

    public void testH264_earlyEos()  { ex(H264(),  earlyEos); }
    public void testHEVC_earlyEos()  { ex(HEVC(),  earlyEos); }
    public void testVP8_earlyEos()   { ex(VP8(),   earlyEos); }
    public void testVP9_earlyEos()   { ex(VP9(),   earlyEos); }
    public void testMpeg4_earlyEos() { ex(Mpeg4(), earlyEos); }
    public void testH263_earlyEos()  { ex(H263(),  earlyEos); }

    public void testH264_eosFlushSeek()  { ex(H264(),  eosFlushSeek); }
    public void testHEVC_eosFlushSeek()  { ex(HEVC(),  eosFlushSeek); }
    public void testVP8_eosFlushSeek()   { ex(VP8(),   eosFlushSeek); }
    public void testVP9_eosFlushSeek()   { ex(VP9(),   eosFlushSeek); }
    public void testMpeg4_eosFlushSeek() { ex(Mpeg4(), eosFlushSeek); }
    public void testH263_eosFlushSeek()  { ex(H263(),  eosFlushSeek); }

    public void testH264_flushConfigureDrc()  { ex(H264(),  flushConfigureDrc); }
    public void testHEVC_flushConfigureDrc()  { ex(HEVC(),  flushConfigureDrc); }
    public void testVP8_flushConfigureDrc()   { ex(VP8(),   flushConfigureDrc); }
    public void testVP9_flushConfigureDrc()   { ex(VP9(),   flushConfigureDrc); }
    public void testMpeg4_flushConfigureDrc() { ex(Mpeg4(), flushConfigureDrc); }
    public void testH263_flushConfigureDrc()  { ex(H263(),  flushConfigureDrc); }

    /* only use unchecked exceptions to allow brief test methods */
    private void ex(Iterable<Codec> codecList, Test test) {
        ex(codecList, new Test[] { test } );
    }

    private void ex(Iterable<Codec> codecList, Test[] testList) {
        if (codecList == null) {
            Log.i(TAG, "CodecList was empty. Skipping test.");
            return;
        }

        TestList tests = new TestList();
        for (Codec c : codecList) {
            for (Test test : testList) {
                if (test.isValid(c)) {
                    test.addTests(tests, c);
                }
            }
        }
        try {
            tests.run();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /* need an inner class to have access to the activity */
    abstract class ActivityTest extends Test {
        TestSurface mNullSurface = new ActivitySurface(null);
        protected TestSurface getSurface() {
            if (mUseSurface) {
                return new ActivitySurface(getActivity().getSurfaceHolder().getSurface());
            } else if (mUseSurfaceTexture) {
                return new DecoderSurface(1280, 720, mCRC);
            }
            return mNullSurface;
        }
    }

    static final int NUM_FRAMES = 50;

    /**
     * Queue some frames with an EOS on the last one.  Test that we have decoded as many
     * frames as we queued.  This tests the EOS handling of the codec to see if all queued
     * (and out-of-order) frames are actually decoded and returned.
     *
     * Also test flushing prior to sending CSD, and immediately after sending CSD.
     */
    class EarlyEosTest extends ActivityTest {
        // using bitfields to create a directed state graph that terminates at FLUSH_NEVER
        static final int FLUSH_BEFORE_CSD = (1 << 1);
        static final int FLUSH_AFTER_CSD = (1 << 0);
        static final int FLUSH_NEVER = 0;

        public boolean isValid(Codec c) {
            return getFormat(c) != null;
        }
        public void addTests(TestList tests, final Codec c) {
            int state = FLUSH_BEFORE_CSD;
            for (int i = NUM_FRAMES / 2; i > 0; --i, state >>= 1) {
                final int queuedFrames = i;
                final int earlyFlushMode = state;
                tests.add(
                    new Step("testing early EOS at " + queuedFrames, this, c) {
                        public void run() {
                            Decoder decoder = new Decoder(c.name);
                            try {
                                MediaFormat fmt = stepFormat();
                                MediaFormat configFmt = fmt;
                                if (earlyFlushMode == FLUSH_BEFORE_CSD) {
                                    // flush before CSD requires not submitting CSD with configure
                                    configFmt = Media.removeCSD(fmt);
                                }
                                decoder.configureAndStart(configFmt, stepSurface());
                                if (earlyFlushMode != FLUSH_NEVER) {
                                    decoder.flush();
                                    // We must always queue CSD after a flush that is potentially
                                    // before we receive output format has changed.  This should
                                    // work even after we receive the format change.
                                    decoder.queueCSD(fmt);
                                }
                                int decodedFrames = -decoder.queueInputBufferRange(
                                        stepMedia(),
                                        0 /* startFrame */,
                                        queuedFrames,
                                        true /* sendEos */,
                                        true /* waitForEos */);
                                if (decodedFrames <= 0) {
                                    Log.w(TAG, "Did not receive EOS -- negating frame count");
                                }
                                decoder.stop();
                                if (decodedFrames != queuedFrames) {
                                    warn("decoded " + decodedFrames + " frames out of " +
                                            queuedFrames + " queued");
                                }
                            } finally {
                                warn(decoder.getWarnings());
                                decoder.releaseQuietly();
                            }
                        }
                    });
                if (sanity) {
                    i >>= 1;
                }
            }
        }
    };

    /**
     * Similar to EarlyEosTest, but we keep the component alive and running in between the steps.
     * This is how seeking should be done if all frames must be outputted.  This also tests that
     * PTS can be repeated after flush.
     */
    class EosFlushSeekTest extends ActivityTest {
        Decoder mDecoder; // test state
        public boolean isValid(Codec c) {
            return getFormat(c) != null;
        }
        public void addTests(TestList tests, final Codec c) {
            tests.add(
                new Step("testing EOS & flush before seek - init", this, c) {
                    public void run() {
                        mDecoder = new Decoder(c.name);
                        mDecoder.configureAndStart(stepFormat(), stepSurface());
                    }});

            for (int i = NUM_FRAMES; i > 0; i--) {
                final int queuedFrames = i;
                tests.add(
                    new Step("testing EOS & flush before seeking after " + queuedFrames +
                            " frames", this, c) {
                        public void run() {
                            int decodedFrames = -mDecoder.queueInputBufferRange(
                                    stepMedia(),
                                    0 /* startFrame */,
                                    queuedFrames,
                                    true /* sendEos */,
                                    true /* waitForEos */);
                            if (decodedFrames != queuedFrames) {
                                warn("decoded " + decodedFrames + " frames out of " +
                                        queuedFrames + " queued");
                            }
                            warn(mDecoder.getWarnings());
                            mDecoder.clearWarnings();
                            mDecoder.flush();
                        }
                    });
                if (sanity) {
                    i >>= 1;
                }
            }

            tests.add(
                new Step("testing EOS & flush before seek - finally", this, c) {
                    public void run() {
                        try {
                            mDecoder.stop();
                        } finally {
                            mDecoder.release();
                        }
                    }});
        }
    };

    /**
     * Similar to EosFlushSeekTest, but we change the media size between the steps.
     * This is how dynamic resolution switching can be done on codecs that do not support
     * adaptive playback.
     */
    class ReconfigDrcTest extends ActivityTest {
        Decoder mDecoder;  // test state
        public boolean isValid(Codec c) {
            return getFormat(c) != null && c.mediaList.length > 1;
        }
        public void addTests(TestList tests, final Codec c) {
            tests.add(
                new Step("testing DRC with reconfigure - init", this, c) {
                    public void run() {
                        mDecoder = new Decoder(c.name);
                    }});

            for (int i = NUM_FRAMES, ix = 0; i > 0; i--, ix++) {
                final int queuedFrames = i;
                final int mediaIx = ix % c.mediaList.length;
                tests.add(
                    new Step("testing DRC with reconfigure after " + queuedFrames + " frames",
                            this, c, mediaIx) {
                        public void run() {
                            try {
                                mDecoder.configureAndStart(stepFormat(), stepSurface());
                                int decodedFrames = -mDecoder.queueInputBufferRange(
                                        stepMedia(),
                                        0 /* startFrame */,
                                        queuedFrames,
                                        true /* sendEos */,
                                        true /* waitForEos */);
                                if (decodedFrames != queuedFrames) {
                                    warn("decoded " + decodedFrames + " frames out of " +
                                            queuedFrames + " queued");
                                }
                                warn(mDecoder.getWarnings());
                                mDecoder.clearWarnings();
                                mDecoder.flush();
                            } finally {
                                mDecoder.stop();
                            }
                        }
                    });
                if (sanity) {
                    i >>= 1;
                }
            }
            tests.add(
                new Step("testing DRC with reconfigure - finally", this, c) {
                    public void run() {
                        mDecoder.release();
                    }});
        }
    };

    /* ADAPTIVE-ONLY TESTS - only run on codecs that support adaptive playback */

    /**
     * Test dynamic resolution change support.  Queue various sized media segments
     * with different resolutions, verify that all queued frames were decoded.  Here
     * PTS will grow between segments.
     */
    class AdaptiveDrcTest extends ActivityTest {
        Decoder mDecoder;
        int mAdjustTimeUs;
        int mDecodedFrames;
        int mQueuedFrames;

        public AdaptiveDrcTest() {
            super();
            adaptive();
        }
        public boolean isValid(Codec c) {
            checkAdaptiveFormat();
            return c.adaptive && c.mediaList.length > 1;
        }
        public void addTests(TestList tests, final Codec c) {
            tests.add(
                new Step("testing DRC with no reconfigure - init", this, c) {
                    public void run() throws Throwable {
                        // FIXME wait 2 seconds to allow system to free up previous codecs
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {}
                        mDecoder = new Decoder(c.name);
                        mDecoder.configureAndStart(stepFormat(), stepSurface());
                        mAdjustTimeUs = 0;
                        mDecodedFrames = 0;
                        mQueuedFrames = 0;
                    }});

            for (int i = NUM_FRAMES, ix = 0; i >= MIN_FRAMES_BEFORE_DRC; i--, ix++) {
                final int mediaIx = ix % c.mediaList.length;
                final int segmentSize = i;
                tests.add(
                    new Step("testing DRC with no reconfigure after " + i + " frames",
                            this, c, mediaIx) {
                        public void run() throws Throwable {
                            mQueuedFrames += segmentSize;
                            boolean lastSequence = segmentSize == MIN_FRAMES_BEFORE_DRC;
                            if (sanity) {
                                lastSequence = (segmentSize >> 1) <= MIN_FRAMES_BEFORE_DRC;
                            }
                            int frames = mDecoder.queueInputBufferRange(
                                    stepMedia(),
                                    0 /* startFrame */,
                                    segmentSize,
                                    lastSequence /* sendEos */,
                                    lastSequence /* expectEos */,
                                    mAdjustTimeUs);
                            if (lastSequence && frames >= 0) {
                                warn("did not receive EOS, received " + frames + " frames");
                            } else if (!lastSequence && frames < 0) {
                                warn("received EOS, received " + (-frames) + " frames");
                            }
                            warn(mDecoder.getWarnings());
                            mDecoder.clearWarnings();

                            mDecodedFrames += Math.abs(frames);
                            mAdjustTimeUs += 1 + stepMedia().getTimestampRangeValue(
                                    0, segmentSize, Media.RANGE_END);
                        }});
                if (sanity) {
                    i >>= 1;
                }
            }
            tests.add(
                new Step("testing DRC with no reconfigure - init", this, c) {
                    public void run() throws Throwable {
                        if (mDecodedFrames != mQueuedFrames) {
                            warn("decoded " + mDecodedFrames + " frames out of " +
                                    mQueuedFrames + " queued");
                        }
                        try {
                            mDecoder.stop();
                        } finally {
                            mDecoder.release();
                        }
                    }
                });
        }
    };

    /**
     * Queue EOS shortly after a dynamic resolution change.  Test that all frames were
     * decoded.
     */
    class AdaptiveDrcEarlyEosTest extends ActivityTest {
        public AdaptiveDrcEarlyEosTest() {
            super();
            adaptive();
        }
        public boolean isValid(Codec c) {
            checkAdaptiveFormat();
            return c.adaptive && c.mediaList.length > 1;
        }
        public Step testStep(final Codec c, final int framesBeforeDrc,
                final int framesBeforeEos) {
            return new Step("testing DRC with no reconfigure after " + framesBeforeDrc +
                    " frames and subsequent EOS after " + framesBeforeEos + " frames",
                    this, c) {
                public void run() throws Throwable {
                    Decoder decoder = new Decoder(c.name);
                    int queuedFrames = framesBeforeDrc + framesBeforeEos;
                    int framesA = 0;
                    int framesB = 0;
                    try {
                        decoder.configureAndStart(stepFormat(), stepSurface());
                        Media media = c.mediaList[0];

                        framesA = decoder.queueInputBufferRange(
                                media,
                                0 /* startFrame */,
                                framesBeforeDrc,
                                false /* sendEos */,
                                false /* expectEos */);
                        if (framesA < 0) {
                            warn("received unexpected EOS, received " + (-framesA) + " frames");
                        }
                        long adjustTimeUs = 1 + media.getTimestampRangeValue(
                                0, framesBeforeDrc, Media.RANGE_END);

                        media = c.mediaList[1];
                        framesB = decoder.queueInputBufferRange(
                                media,
                                0 /* startFrame */,
                                framesBeforeEos,
                                true /* sendEos */,
                                true /* expectEos */,
                                adjustTimeUs);
                        if (framesB >= 0) {
                            warn("did not receive EOS, received " + (-framesB) + " frames");
                        }
                        decoder.stop();
                        warn(decoder.getWarnings());
                    } finally {
                        int decodedFrames = Math.abs(framesA) + Math.abs(framesB);
                        if (decodedFrames != queuedFrames) {
                            warn("decoded " + decodedFrames + " frames out of " + queuedFrames +
                                    " queued");
                        }
                        decoder.release();
                    }
                }
            };
        }
        public void addTests(TestList tests, Codec c) {
            for (int drcFrame = 6; drcFrame >= MIN_FRAMES_BEFORE_DRC; drcFrame--) {
                for (int eosFrame = 6; eosFrame >= 1; eosFrame--) {
                    tests.add(testStep(c, drcFrame, eosFrame));
                }
            }
        }
    };

    /**
     * Similar to AdaptiveDrcTest, but tests that PTS can change at adaptive boundaries both
     * forward and backward without the need to flush.
     */
    class AdaptiveSkipTest extends ActivityTest {
        boolean forward;
        public AdaptiveSkipTest(boolean fwd) {
            forward = fwd;
            adaptive();
        }
        public boolean isValid(Codec c) {
            checkAdaptiveFormat();
            return c.adaptive;
        }
        Decoder mDecoder;
        int mAdjustTimeUs = 0;
        int mDecodedFrames = 0;
        int mQueuedFrames = 0;
        public void addTests(TestList tests, final Codec c) {
            tests.add(
                new Step("testing flushless skipping - init", this, c) {
                    public void run() throws Throwable {
                        mDecoder = new Decoder(c.name);
                        mDecoder.configureAndStart(stepFormat(), stepSurface());
                        mAdjustTimeUs = 0;
                        mDecodedFrames = 0;
                        mQueuedFrames = 0;
                    }});

            for (int i = 2, ix = 0; i <= NUM_FRAMES; i++, ix++) {
                final int mediaIx = ix % c.mediaList.length;
                final int segmentSize = i;
                final boolean lastSequence;
                if (sanity) {
                    lastSequence = (segmentSize << 1) + 1 > NUM_FRAMES;
                } else {
                    lastSequence = segmentSize >= NUM_FRAMES;
                }
                tests.add(
                    new Step("testing flushless skipping " + (forward ? "forward" : "backward") +
                            " after " + i + " frames", this, c) {
                        public void run() throws Throwable {
                            int frames = mDecoder.queueInputBufferRange(
                                stepMedia(),
                                0 /* startFrame */,
                                segmentSize,
                                lastSequence /* sendEos */,
                                lastSequence /* expectEos */,
                                mAdjustTimeUs);
                            if (lastSequence && frames >= 0) {
                                warn("did not receive EOS, received " + frames + " frames");
                            } else if (!lastSequence && frames < 0) {
                                warn("received unexpected EOS, received " + (-frames) + " frames");
                            }
                            warn(mDecoder.getWarnings());
                            mDecoder.clearWarnings();

                            mQueuedFrames += segmentSize;
                            mDecodedFrames += Math.abs(frames);
                            if (forward) {
                                mAdjustTimeUs += 10000000 + stepMedia().getTimestampRangeValue(
                                        0, segmentSize, Media.RANGE_DURATION);
                            }
                        }});
                if (sanity) {
                    i <<= 1;
                }
            }

            tests.add(
                new Step("testing flushless skipping - finally", this, c) {
                    public void run() throws Throwable {
                        if (mDecodedFrames != mQueuedFrames) {
                            warn("decoded " + mDecodedFrames + " frames out of " + mQueuedFrames +
                                    " queued");
                        }
                        try {
                            mDecoder.stop();
                        } finally {
                            mDecoder.release();
                        }
                    }});
        }
    };

    // not yet used
    static long checksum(ByteBuffer buf, int size, CRC32 crc) {
        assertTrue(size >= 0);
        assertTrue(size <= buf.capacity());
        crc.reset();
        if (buf.hasArray()) {
            crc.update(buf.array(), buf.arrayOffset(), size);
        } else {
           int pos = buf.position();
           buf.rewind();
           final int rdsize = Math.min(4096, size);
           byte bb[] = new byte[rdsize];
           int chk;
           for (int i = 0; i < size; i += chk) {
                chk = Math.min(rdsize, size - i);
                buf.get(bb, 0, chk);
                crc.update(bb, 0, chk);
            }
            buf.position(pos);
        }
        return crc.getValue();
    }

    CRC32 mCRC;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCRC = new CRC32();
    }

    /* ====================================================================== */
    /*                              UTILITY FUNCTIONS                         */
    /* ====================================================================== */
    public static String collectionString(Collection<?> c) {
        StringBuilder res = new StringBuilder("[");
        boolean subsequent = false;
        for (Object o: c) {
            if (subsequent) {
                res.append(", ");
            }
            res.append(o);
            subsequent = true;
        }
        return res.append("]").toString();
    }

    static String byteBufferToString(ByteBuffer buf, int start, int len) {
        int oldPosition = buf.position();
        buf.position(start);
        int strlen = 2; // {}
        boolean ellipsis = len < buf.limit();
        if (ellipsis) {
            strlen += 3; // ...
        } else {
            len = buf.limit();
        }
        strlen += 3 * len - (len > 0 ? 1 : 0); // XX,XX
        char[] res = new char[strlen];
        res[0] = '{';
        res[strlen - 1] = '}';
        if (ellipsis) {
            res[strlen - 2] = res[strlen - 3] = res[strlen - 4] = '.';
        }
        for (int i = 1; i < len; i++) {
            res[i * 3] = ',';
        }
        for (int i = 0; i < len; i++) {
            byte b = buf.get();
            int d = (b >> 4) & 15;
            res[i * 3 + 1] = (char)(d + (d > 9 ? 'a' - 10 : '0'));
            d = (b & 15);
            res[i * 3 + 2] = (char)(d + (d > 9 ? 'a' - 10 : '0'));
        }
        buf.position(oldPosition);
        return new String(res);
    }

    static <E> Iterable<E> chain(Iterable<E> ... iterables) {
        /* simple chainer using ArrayList */
        ArrayList<E> items = new ArrayList<E>();
        for (Iterable<E> it: iterables) {
            for (E el: it) {
                items.add(el);
            }
        }
        return items;
    }

    class Decoder implements MediaCodec.OnFrameRenderedListener {
        private final static String TAG = "AdaptiveDecoder";
        final long kTimeOutUs = 5000;
        final long kCSDTimeOutUs = 1000000;
        MediaCodec mCodec;
        ByteBuffer[] mInputBuffers;
        ByteBuffer[] mOutputBuffers;
        TestSurface mSurface;
        boolean mDoChecksum;
        boolean mQueuedEos;
        ArrayList<Long> mTimeStamps;
        ArrayList<String> mWarnings;
        Vector<Long> mRenderedTimeStamps; // using Vector as it is implicitly synchronized
        long mLastRenderNanoTime;
        int mFramesNotifiedRendered;

        public Decoder(String codecName) {
            MediaCodec codec = null;
            try {
                codec = MediaCodec.createByCodecName(codecName);
            } catch (Exception e) {
                throw new RuntimeException("couldn't create codec " + codecName, e);
            }
            Log.i(TAG, "using codec: " + codec.getName());
            mCodec = codec;
            mDoChecksum = false;
            mQueuedEos = false;
            mTimeStamps = new ArrayList<Long>();
            mWarnings = new ArrayList<String>();
            mRenderedTimeStamps = new Vector<Long>();
            mLastRenderNanoTime = System.nanoTime();
            mFramesNotifiedRendered = 0;

            codec.setOnFrameRenderedListener(this, null);
        }

        public void onFrameRendered(MediaCodec codec, long presentationTimeUs, long nanoTime) {
            final long NSECS_IN_1SEC = 1000000000;
            if (!mRenderedTimeStamps.remove(presentationTimeUs)) {
                warn("invalid timestamp " + presentationTimeUs + ", queued " +
                        collectionString(mRenderedTimeStamps));
            }
            assert nanoTime > mLastRenderNanoTime;
            mLastRenderNanoTime = nanoTime;
            ++mFramesNotifiedRendered;
            assert nanoTime > System.nanoTime() - NSECS_IN_1SEC;
        }

        public String getName() {
            return mCodec.getName();
        }

        public Iterable<String> getWarnings() {
            return mWarnings;
        }

        private void warn(String warning) {
            mWarnings.add(warning);
            Log.w(TAG, warning);
        }

        public void clearWarnings() {
            mWarnings.clear();
        }

        public void configureAndStart(MediaFormat format, TestSurface surface) {
            mSurface = surface;
            Log.i(TAG, "configure(" + format + ", " + mSurface.getSurface() + ")");
            mCodec.configure(format, mSurface.getSurface(), null /* crypto */, 0 /* flags */);
            Log.i(TAG, "start");
            mCodec.start();

            // inject some minimal setOutputSurface test
            // TODO: change this test to also change the surface midstream
            try {
                mCodec.setOutputSurface(null);
                fail("should not be able to set surface to NULL");
            } catch (IllegalArgumentException e) {}
            mCodec.setOutputSurface(mSurface.getSurface());

            mInputBuffers = mCodec.getInputBuffers();
            mOutputBuffers = mCodec.getOutputBuffers();
            Log.i(TAG, "configured " + mInputBuffers.length + " input[" +
                  mInputBuffers[0].capacity() + "] and " +
                  mOutputBuffers.length + "output[" +
                  (mOutputBuffers[0] == null ? null : mOutputBuffers[0].capacity()) + "]");
            mQueuedEos = false;
            mRenderedTimeStamps.clear();
            mLastRenderNanoTime = System.nanoTime();
            mFramesNotifiedRendered = 0;
        }

        public void stop() {
            Log.i(TAG, "stop");
            mCodec.stop();
            // if we have queued 32 frames or more, at least one should have been notified
            // to have rendered.
            if (mRenderedTimeStamps.size() > 32 && mFramesNotifiedRendered == 0) {
                fail("rendered " + mRenderedTimeStamps.size() +
                        " frames, but none have been notified.");
            }
        }

        public void flush() {
            Log.i(TAG, "flush");
            mCodec.flush();
            mQueuedEos = false;
            mTimeStamps.clear();
        }

        public String dequeueAndReleaseOutputBuffer(MediaCodec.BufferInfo info) {
            int ix = mCodec.dequeueOutputBuffer(info, kTimeOutUs);
            if (ix == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                mOutputBuffers = mCodec.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
                return null;
            } else if (ix == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = mCodec.getOutputFormat();
                Log.d(TAG, "output format has changed to " + format);
                int colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                mDoChecksum = isRecognizedFormat(colorFormat);
                return null;
            } else if (ix < 0) {
                Log.v(TAG, "no output");
                return null;
            }
            /* create checksum */
            long sum = 0;


            Log.v(TAG, "dequeue #" + ix + " => { [" + info.size + "] flags=" + info.flags +
                    " @" + info.presentationTimeUs + "}");

            // we get a nonzero size for valid decoded frames
            boolean doRender = (info.size != 0);
            if (mSurface.getSurface() == null) {
                if (mDoChecksum) {
                    sum = checksum(mOutputBuffers[ix], info.size, mCRC);
                }
                mCodec.releaseOutputBuffer(ix, doRender);
            } else if (doRender) {
                // If using SurfaceTexture, as soon as we call releaseOutputBuffer, the
                // buffer will be forwarded to SurfaceTexture to convert to a texture.
                // The API doesn't guarantee that the texture will be available before
                // the call returns, so we need to wait for the onFrameAvailable callback
                // to fire.  If we don't wait, we risk dropping frames.
                mSurface.prepare();
                mCodec.releaseOutputBuffer(ix, doRender);
                mSurface.waitForDraw();
                if (mDoChecksum) {
                    sum = mSurface.checksum();
                }
            } else {
                mCodec.releaseOutputBuffer(ix, doRender);
            }

            if (doRender) {
                mRenderedTimeStamps.add(info.presentationTimeUs);
                if (!mTimeStamps.remove(info.presentationTimeUs)) {
                    warn("invalid timestamp " + info.presentationTimeUs + ", queued " +
                            collectionString(mTimeStamps));
                }
            }

            return String.format(Locale.US, "{pts=%d, flags=%x, data=0x%x}",
                                 info.presentationTimeUs, info.flags, sum);
        }

        /* returns true iff queued a frame */
        public boolean queueInputBuffer(Media media, int frameIx, boolean EOS) {
            return queueInputBuffer(media, frameIx, EOS, 0);
        }

        public boolean queueInputBuffer(Media media, int frameIx, boolean EOS, long adjustTimeUs) {
            if (mQueuedEos) {
                return false;
            }

            int ix = mCodec.dequeueInputBuffer(kTimeOutUs);

            if (ix < 0) {
                return false;
            }

            ByteBuffer buf = mInputBuffers[ix];
            Media.Frame frame = media.getFrame(frameIx);
            buf.clear();

            long presentationTimeUs = adjustTimeUs;
            int flags = 0;
            if (frame != null) {
                buf.put((ByteBuffer)frame.buf.clear());
                presentationTimeUs += frame.presentationTimeUs;
                flags = frame.flags;
            }

            if (EOS) {
                flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mQueuedEos = true;
            }

            mTimeStamps.add(presentationTimeUs);
            Log.v(TAG, "queue { [" + buf.position() + "]=" + byteBufferToString(buf, 0, 16) +
                    " flags=" + flags + " @" + presentationTimeUs + "} => #" + ix);
            mCodec.queueInputBuffer(
                    ix, 0 /* offset */, buf.position(), presentationTimeUs, flags);
            return true;
        }

        /* returns number of frames received multiplied by -1 if received EOS, 1 otherwise */
        public int queueInputBufferRange(
                Media media, int frameStartIx, int frameEndIx, boolean sendEosAtEnd,
                boolean waitForEos) {
            return queueInputBufferRange(media,frameStartIx,frameEndIx,sendEosAtEnd,waitForEos,0);
        }

        public void queueCSD(MediaFormat format) {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            for (int csdIx = 0; ; ++csdIx) {
                ByteBuffer csdBuf = format.getByteBuffer("csd-" + csdIx);
                if (csdBuf == null) {
                    break;
                }

                int ix = mCodec.dequeueInputBuffer(kCSDTimeOutUs);
                if (ix < 0) {
                    fail("Could not dequeue input buffer for CSD #" + csdIx);
                    return;
                }

                ByteBuffer buf = mInputBuffers[ix];
                buf.clear();
                buf.put((ByteBuffer)csdBuf.clear());
                Log.v(TAG, "queue-CSD { [" + buf.position() + "]=" +
                        byteBufferToString(buf, 0, 16) + "} => #" + ix);
                mCodec.queueInputBuffer(
                        ix, 0 /* offset */, buf.position(), 0 /* timeUs */,
                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
            }
        }

        public int queueInputBufferRange(
                Media media, int frameStartIx, int frameEndIx, boolean sendEosAtEnd,
                boolean waitForEos, long adjustTimeUs) {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int frameIx = frameStartIx;
            int numFramesDecoded = 0;
            boolean sawOutputEos = false;
            int deadDecoderCounter = 0;
            ArrayList<String> frames = new ArrayList<String>();
            String buf = null;
            // After all input buffers are queued, dequeue as many output buffers as possible.
            while ((waitForEos && !sawOutputEos) || frameIx < frameEndIx || buf != null) {
                if (frameIx < frameEndIx) {
                    if (queueInputBuffer(
                            media,
                            frameIx,
                            sendEosAtEnd && (frameIx + 1 == frameEndIx),
                            adjustTimeUs)) {
                        frameIx++;
                    }
                }

                buf = dequeueAndReleaseOutputBuffer(info);
                if (buf != null) {
                    // Some decoders output a 0-sized buffer at the end. Disregard those.
                    if (info.size > 0) {
                        deadDecoderCounter = 0;
                        numFramesDecoded++;
                    }

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "saw output EOS.");
                        sawOutputEos = true;
                    }
                }
                if (++deadDecoderCounter >= 100) {
                    warn("have not received an output frame for a while");
                    break;
                }
            }

            if (numFramesDecoded < frameEndIx - frameStartIx - 16) {
                fail("Queued " + (frameEndIx - frameStartIx) + " frames but only received " +
                        numFramesDecoded);
            }
            return (sawOutputEos ? -1 : 1) * numFramesDecoded;
        }

        void release() {
            Log.i(TAG, "release");
            mCodec.release();
            mSurface.release();
            mInputBuffers = null;
            mOutputBuffers = null;
            mCodec = null;
            mSurface = null;
        }

        // don't fail on exceptions in release()
        void releaseQuietly() {
            try {
                Log.i(TAG, "release");
                mCodec.release();
            } catch (Throwable e) {
                Log.e(TAG, "Exception while releasing codec", e);
            }
            mSurface.release();
            mInputBuffers = null;
            mOutputBuffers = null;
            mCodec = null;
            mSurface = null;
        }
    };

    /* from EncodeDecodeTest */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    private int countFrames(
            String codecName, MediaCodecInfo codecInfo, Media media, int eosframe, TestSurface s)
            throws Exception {
        Decoder codec = new Decoder(codecName);
        codec.configureAndStart(media.getFormat(), s /* surface */);

        int numframes = codec.queueInputBufferRange(
                media, 0, eosframe, true /* sendEos */, true /* waitForEos */);
        if (numframes >= 0) {
            Log.w(TAG, "Did not receive EOS");
        } else {
            numframes *= -1;
        }

        codec.stop();
        codec.release();
        return numframes;
    }
}

/* ====================================================================== */
/*                             Video Media Asset                          */
/* ====================================================================== */
class Media {
    private final static String TAG = "AdaptiveMedia";
    private MediaFormat mFormat;
    private MediaFormat mAdaptiveFormat;
    static class Frame {
        long presentationTimeUs;
        int flags;
        ByteBuffer buf;
        public Frame(long _pts, int _flags, ByteBuffer _buf) {
            presentationTimeUs = _pts;
            flags = _flags;
            buf = _buf;
        }
    };
    private Frame[] mFrames;

    public Frame getFrame(int ix) {
        /* this works even on short sample as frame is allocated as null */
        if (ix >= 0 && ix < mFrames.length) {
            return mFrames[ix];
        }
        return null;
    }
    private Media(MediaFormat format, MediaFormat adaptiveFormat, int numFrames) {
        /* need separate copies of format as once we add adaptive flags to
           MediaFormat, we cannot remove them */
        mFormat = format;
        mAdaptiveFormat = adaptiveFormat;
        mFrames = new Frame[numFrames];
    }

    public MediaFormat getFormat() {
        return mFormat;
    }

    public static MediaFormat removeCSD(MediaFormat orig) {
        MediaFormat copy = MediaFormat.createVideoFormat(
                orig.getString(orig.KEY_MIME),
                orig.getInteger(orig.KEY_WIDTH), orig.getInteger(orig.KEY_HEIGHT));
        for (String k : new String[] {
                orig.KEY_FRAME_RATE, orig.KEY_MAX_WIDTH, orig.KEY_MAX_HEIGHT,
                orig.KEY_MAX_INPUT_SIZE
        }) {
            if (orig.containsKey(k)) {
                try {
                    copy.setInteger(k, orig.getInteger(k));
                } catch (ClassCastException e) {
                    try {
                        copy.setFloat(k, orig.getFloat(k));
                    } catch (ClassCastException e2) {
                        // Could not copy value. Don't fail here, as having non-standard
                        // value types for defined keys is permissible by the media API
                        // for optional keys.
                    }
                }
            }
        }
        return copy;
    }

    public MediaFormat getAdaptiveFormat(int width, int height) {
        mAdaptiveFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, width);
        mAdaptiveFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, height);
        return mAdaptiveFormat;
    }

    public String getMime() {
        return mFormat.getString(MediaFormat.KEY_MIME);
    }

    public int getWidth() {
        return mFormat.getInteger(MediaFormat.KEY_WIDTH);
    }

    public int getHeight() {
        return mFormat.getInteger(MediaFormat.KEY_HEIGHT);
    }

    public final static int RANGE_START = 0;
    public final static int RANGE_END = 1;
    public final static int RANGE_DURATION = 2;

    public long getTimestampRangeValue(int frameStartIx, int frameEndIx, int kind) {
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (int frameIx = frameStartIx; frameIx < frameEndIx; frameIx++) {
            Frame frame = getFrame(frameIx);
            if (frame != null) {
                if (min > frame.presentationTimeUs) {
                    min = frame.presentationTimeUs;
                }
                if (max < frame.presentationTimeUs) {
                    max = frame.presentationTimeUs;
                }
            }
        }
        if (kind == RANGE_START) {
            return min;
        } else if (kind == RANGE_END) {
            return max;
        } else if (kind == RANGE_DURATION) {
            return max - min;
        } else {
            throw new IllegalArgumentException("kind is not valid: " + kind);
        }
    }

    public static Media read(Context context, int video, int numFrames)
            throws java.io.IOException {
        MediaExtractor extractor = new MediaExtractor();
        AssetFileDescriptor testFd = context.getResources().openRawResourceFd(video);
        extractor.setDataSource(testFd.getFileDescriptor(), testFd.getStartOffset(),
                testFd.getLength());

        Media media = new Media(
                extractor.getTrackFormat(0), extractor.getTrackFormat(0), numFrames);
        extractor.selectTrack(0);

        Log.i(TAG, "format=" + media.getFormat());
        ArrayList<ByteBuffer> csds = new ArrayList<ByteBuffer>();
        for (String tag: new String[] { "csd-0", "csd-1" }) {
            if (media.getFormat().containsKey(tag)) {
                ByteBuffer csd = media.getFormat().getByteBuffer(tag);
                Log.i(TAG, tag + "=" + AdaptivePlaybackTest.byteBufferToString(csd, 0, 16));
                csds.add(csd);
            }
        }

        ByteBuffer readBuf = ByteBuffer.allocate(2000000);
        for (int ix = 0; ix < numFrames; ix++) {
            int sampleSize = extractor.readSampleData(readBuf, 0 /* offset */);

            if (sampleSize < 0) {
                throw new IllegalArgumentException("media is too short at " + ix + " frames");
            } else {
                readBuf.position(0).limit(sampleSize);
                for (ByteBuffer csd: csds) {
                    sampleSize += csd.capacity();
                }
                ByteBuffer buf = ByteBuffer.allocate(sampleSize);
                for (ByteBuffer csd: csds) {
                    csd.clear();
                    buf.put(csd);
                    csd.clear();
                    Log.i(TAG, "csd[" + csd.capacity() + "]");
                }
                Log.i(TAG, "frame-" + ix + "[" + sampleSize + "]");
                csds.clear();
                buf.put(readBuf);
                media.mFrames[ix] = new Frame(
                    extractor.getSampleTime(),
                    extractor.getSampleFlags(),
                    buf);
                extractor.advance();
            }
        }
        extractor.release();
        testFd.close();
        return media;
    }
}

/* ====================================================================== */
/*                      Codec, CodecList and CodecFactory                 */
/* ====================================================================== */
class Codec {
    private final static String TAG = "AdaptiveCodec";

    public String name;
    public CodecCapabilities capabilities;
    public Media[] mediaList;
    public boolean adaptive;
    public Codec(String n, CodecCapabilities c, Media[] m) {
        name = n;
        capabilities = c;
        List<Media> medias = new ArrayList<Media>();

        if (capabilities == null) {
            adaptive = false;
        } else {
            Log.w(TAG, "checking capabilities of " + name + " for " + m[0].getMime());
            adaptive = capabilities.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback);

            for (Media media : m) {
                if (media.getHeight() >= 720 &&
                        !capabilities.isFormatSupported(media.getFormat())) {
                    // skip if 720p and up is unsupported
                    Log.w(TAG, "codec " + name + " doesn't support " + media.getFormat());
                    continue;
                }
                medias.add(media);
            }
        }

        if (medias.size() < 2) {
            Log.e(TAG, "codec " + name + " doesn't support required resolutions");
        }
        mediaList = medias.subList(0, 2).toArray(new Media[2]);
    }
}

class CodecList extends ArrayList<Codec> { };

/* all codecs of mime, plus named codec if exists */
class CodecFamily extends CodecList {
    private final static String TAG = "AdaptiveCodecFamily";
    private static final int NUM_FRAMES = AdaptivePlaybackTest.NUM_FRAMES;

    public CodecFamily(Context context, String mime, String explicitCodecName, int ... resources) {
        try {
            /* read all media */
            Media[] mediaList = new Media[resources.length];
            for (int i = 0; i < resources.length; i++) {
                Log.v(TAG, "reading media " + resources[i]);
                Media media = Media.read(context, resources[i], NUM_FRAMES);
                assert media.getMime().equals(mime):
                        "test stream " + resources[i] + " has " + media.getMime() +
                        " mime type instead of " + mime;

                /* assuming the first timestamp is the smallest */
                long firstPTS = media.getFrame(0).presentationTimeUs;
                long smallestPTS = media.getTimestampRangeValue(0, NUM_FRAMES, Media.RANGE_START);

                assert firstPTS == smallestPTS:
                        "first frame timestamp (" + firstPTS + ") is not smallest (" +
                        smallestPTS + ")";

                mediaList[i] = media;
            }

            /* enumerate codecs */
            MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (MediaCodecInfo codecInfo : mcl.getCodecInfos()) {
                if (codecInfo.isEncoder()) {
                    continue;
                }
                for (String type : codecInfo.getSupportedTypes()) {
                    if (type.equals(mime)) {
                        /* mark the explicitly named codec as included */
                        if (codecInfo.getName().equals(explicitCodecName)) {
                            explicitCodecName = null;
                        }
                        add(new Codec(
                                codecInfo.getName(),
                                codecInfo.getCapabilitiesForType(mime),
                                mediaList));
                        break;
                    }
                }
            }

            /* test if the explicitly named codec is present on the system */
            if (explicitCodecName != null) {
                MediaCodec codec = MediaCodec.createByCodecName(explicitCodecName);
                if (codec != null) {
                    codec.release();
                    add(new Codec(explicitCodecName, null, mediaList));
                }
            }
        } catch (Throwable t) {
            Log.wtf("Constructor failed", t);
            throw new RuntimeException("constructor failed", t);
        }
    }
}

/* named codec if exists */
class CodecByName extends CodecList {
    public CodecByName(Context context, String mime, String codecName, int ... resources) {
        for (Codec c: new CodecFamily(context, mime, codecName, resources)) {
            if (c.name.equals(codecName)) {
                add(c);
            }
        }
    }
}

/* all codecs of mime, except named codec if exists */
class CodecFamilyExcept extends CodecList {
    public CodecFamilyExcept(
            Context context, String mime, String exceptCodecName, int ... resources) {
        for (Codec c: new CodecFamily(context, mime, null, resources)) {
            if (!c.name.equals(exceptCodecName)) {
                add(c);
            }
        }
    }
}

class CodecFactory {
    protected boolean hasCodec(String codecName) {
        MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo info : list.getCodecInfos()) {
            if (codecName.equals(info.getName())) {
                return true;
            }
        }
        return false;
    }

    public CodecList createCodecList(
            Context context, String mime, String googleCodecName, int ...resources) {
        if (!hasCodec(googleCodecName)) {
            return null;
        }
        return new CodecFamily(context, mime, googleCodecName, resources);
    }
}

class SWCodecFactory extends CodecFactory {
    public CodecList createCodecList(
            Context context, String mime, String googleCodecName, int ...resources) {
        if (!hasCodec(googleCodecName)) {
            return null;
        }
        return new CodecByName(context, mime, googleCodecName, resources);
    }
}

class HWCodecFactory extends CodecFactory {
    public CodecList createCodecList(
            Context context, String mime, String googleCodecName, int ...resources) {
        if (!hasCodec(googleCodecName)) {
            return null;
        }
        return new CodecFamilyExcept(context, mime, googleCodecName, resources);
    }
}

/* ====================================================================== */
/*                  Test Steps, Test (Case)s, and Test List               */
/* ====================================================================== */
class StepRunner implements Runnable {
    public StepRunner(Step s) {
        mStep = s;
        mThrowed = null;
    }
    public void run() {
        try {
            mStep.run();
        } catch (Throwable e) {
            mThrowed = e;
        }
    }
    public void throwThrowed() throws Throwable {
        if (mThrowed != null) {
            throw mThrowed;
        }
    }
    private Throwable mThrowed;
    private Step mStep;
}

class TestList extends ArrayList<Step> {
    private final static String TAG = "AdaptiveTestList";
    public void run() throws Throwable {
        Throwable res = null;
        for (Step step: this) {
            try {
                Log.i(TAG, step.getDescription());
                if (step.stepSurface().needsToRunInSeparateThread()) {
                    StepRunner runner = new StepRunner(step);
                    Thread th = new Thread(runner, "stepWrapper");
                    th.start();
                    th.join();
                    runner.throwThrowed();
                } else {
                    step.run();
                }
            } catch (Throwable e) {
                Log.e(TAG, "while " + step.getDescription(), e);
                res = e;
                mFailedSteps++;
            } finally {
                mWarnings += step.getWarnings();
            }
        }
        if (res != null) {
            throw new RuntimeException(
                mFailedSteps + " failed steps, " + mWarnings + " warnings",
                res);
        }
    }
    public int getWarnings() {
        return mWarnings;
    }
    public int getFailures() {
        return mFailedSteps;
    }
    private int mFailedSteps;
    private int mWarnings;
}

abstract class Test {
    public static final int FORMAT_ADAPTIVE_LARGEST = 1;
    public static final int FORMAT_ADAPTIVE_FIRST = 2;
    public static final int FORMAT_REGULAR = 3;

    protected int mFormatType;
    protected boolean mUseSurface;
    protected boolean mUseSurfaceTexture;

    public Test() {
        mFormatType = FORMAT_REGULAR;
        mUseSurface = true;
        mUseSurfaceTexture = false;
    }

    public Test adaptive() {
        mFormatType = FORMAT_ADAPTIVE_LARGEST;
        return this;
    }

    public Test adaptiveSmall() {
        mFormatType = FORMAT_ADAPTIVE_FIRST;
        return this;
    }

    public Test byteBuffer() {
        mUseSurface = false;
        mUseSurfaceTexture = false;
        return this;
    }

    public Test texture() {
        mUseSurface = false;
        mUseSurfaceTexture = true;
        return this;
    }

    public void checkAdaptiveFormat() {
        assert mFormatType != FORMAT_REGULAR:
                "must be used with adaptive format";
    }

    abstract protected TestSurface getSurface();

    /* TRICKY: format is updated in each test run as we are actually reusing the
       same 2 MediaFormat objects returned from MediaExtractor.  Therefore,
       format must be explicitly obtained in each test step.

       returns null if codec does not support the format.
       */
    protected MediaFormat getFormat(Codec c) {
        return getFormat(c, 0);
    }

    protected MediaFormat getFormat(Codec c, int i) {
        MediaFormat format = null;
        if (mFormatType == FORMAT_REGULAR) {
            format = c.mediaList[i].getFormat();
        } else if (mFormatType == FORMAT_ADAPTIVE_FIRST && c.adaptive) {
            format = c.mediaList[i].getAdaptiveFormat(
                c.mediaList[i].getWidth(), c.mediaList[i].getHeight());
        } else if (mFormatType == FORMAT_ADAPTIVE_LARGEST && c.adaptive) {
            /* update adaptive format to max size used */
            format = c.mediaList[i].getAdaptiveFormat(0, 0);
            for (Media media : c.mediaList) {
                /* get the largest width, and the largest height independently */
                if (media.getWidth() > format.getInteger(MediaFormat.KEY_MAX_WIDTH)) {
                    format.setInteger(MediaFormat.KEY_MAX_WIDTH, media.getWidth());
                }
                if (media.getHeight() > format.getInteger(MediaFormat.KEY_MAX_HEIGHT)) {
                    format.setInteger(MediaFormat.KEY_MAX_HEIGHT, media.getHeight());
                }
            }
        }
        return format;
    }

    public boolean isValid(Codec c) { return true; }
    public abstract void addTests(TestList tests, Codec c);
}

abstract class Step {
    private static final String TAG = "AdaptiveStep";

    public Step(String title, Test instance, Codec codec, Media media) {
        mTest = instance;
        mCodec = codec;
        mMedia = media;
        mDescription = title + " on " + stepSurface().getSurface() + " using " +
            mCodec.name + " and " + stepFormat();
    }
    public Step(String title, Test instance, Codec codec, int mediaIx) {
        this(title, instance, codec, codec.mediaList[mediaIx]);
    }
    public Step(String title, Test instance, Codec codec) {
        this(title, instance, codec, 0);
    }
    public Step(String description) {
        mDescription = description;
    }
    public Step() { }

    public abstract void run() throws Throwable;

    private String mDescription;
    private Test mTest;
    private Codec mCodec;
    private Media mMedia;
    private int mWarnings;

    /* TRICKY: use non-standard getter names so that we don't conflict with the getters
       in the Test classes, as most test Steps are defined as anonymous classes inside
       the test classes. */
    public MediaFormat stepFormat() {
        int ix = Arrays.asList(mCodec.mediaList).indexOf(mMedia);
        return mTest.getFormat(mCodec, ix);
    }

    public TestSurface stepSurface() {
        return mTest.getSurface();
    }

    public Media  stepMedia()       { return mMedia; }

    public String getDescription() { return mDescription; }
    public int    getWarnings()    { return mWarnings; }

    public void warn(String message) {
        Log.e(TAG, "WARNING: " + message + " in " + getDescription());
        mWarnings++;
    }
    public void warn(String message, Throwable t) {
        Log.e(TAG, "WARNING: " + message + " in " + getDescription(), t);
        mWarnings++;
    }
    public void warn(Iterable<String> warnings) {
        for (String warning: warnings) {
            warn(warning);
        }
    }
}

interface TestSurface {
    public Surface getSurface();
    public long checksum();
    public void release();
    public void prepare();         // prepare surface prior to render
    public void waitForDraw();     // wait for rendering to take place
    public boolean needsToRunInSeparateThread();
}

class DecoderSurface extends OutputSurface implements TestSurface {
    private ByteBuffer mBuf;
    int mWidth;
    int mHeight;
    CRC32 mCRC;

    public DecoderSurface(int width, int height, CRC32 crc) {
        super(width, height);
        mWidth = width;
        mHeight = height;
        mCRC = crc;
        mBuf = ByteBuffer.allocateDirect(4 * width * height);
    }

    public void prepare() {
        makeCurrent();
    }

    public void waitForDraw() {
        awaitNewImage();
        drawImage();
    }

    public long checksum() {
        mBuf.position(0);
        GLES20.glReadPixels(0, 0, mWidth, mHeight, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, mBuf);
        mBuf.position(0);
        return AdaptivePlaybackTest.checksum(mBuf, mBuf.capacity(), mCRC);
    }

    public void release() {
        super.release();
        mBuf = null;
    }

    public boolean needsToRunInSeparateThread() {
        return true;
    }
}

class ActivitySurface implements TestSurface {
    private Surface mSurface;
    public ActivitySurface(Surface s) {
        mSurface = s;
    }
    public Surface getSurface() {
        return mSurface;
    }
    public void prepare() { }
    public void waitForDraw() { }
    public long checksum() {
        return 0;
    }
    public void release() {
        // don't release activity surface, as it is reusable
    }
    public boolean needsToRunInSeparateThread() {
        return false;
    }
}

