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

import android.media.cts.R;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodec.CryptoInfo;
import android.media.MediaCodec.CryptoInfo.Pattern;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaDrm;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.support.test.filters.SmallTest;
import android.platform.test.annotations.RequiresDevice;
import android.test.AndroidTestCase;
import android.util.Log;
import android.view.Surface;

import com.android.compatibility.common.util.MediaUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * General MediaCodec tests.
 *
 * In particular, check various API edge cases.
 *
 * <p>The file in res/raw used by testDecodeShortInput are (c) copyright 2008,
 * Blender Foundation / www.bigbuckbunny.org, and are licensed under the Creative Commons
 * Attribution 3.0 License at http://creativecommons.org/licenses/by/3.0/us/.
 */
@SmallTest
@RequiresDevice
public class MediaCodecTest extends AndroidTestCase {
    private static final String TAG = "MediaCodecTest";
    private static final boolean VERBOSE = false;           // lots of logging

    // parameters for the video encoder
                                                            // H.264 Advanced Video Coding
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int BIT_RATE = 2000000;            // 2Mbps
    private static final int FRAME_RATE = 15;               // 15fps
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    // parameters for the audio encoder
    private static final String MIME_TYPE_AUDIO = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final int AUDIO_SAMPLE_RATE = 44100;
    private static final int AUDIO_AAC_PROFILE = 2; /* OMX_AUDIO_AACObjectLC */
    private static final int AUDIO_CHANNEL_COUNT = 2; // mono
    private static final int AUDIO_BIT_RATE = 128000;

    private static final int TIMEOUT_USEC = 100000;
    private static final int TIMEOUT_USEC_SHORT = 100;

    private boolean mVideoEncoderHadError = false;
    private boolean mAudioEncoderHadError = false;
    private volatile boolean mVideoEncodingOngoing = false;

    private static final int INPUT_RESOURCE_ID =
            R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;

    // The test should fail if the decoder never produces output frames for the input.
    // Time out decoding, as we have no way to query whether the decoder will produce output.
    private static final int DECODING_TIMEOUT_MS = 10000;

    /**
     * Tests:
     * <br> Exceptions for MediaCodec factory methods
     * <br> Exceptions for MediaCodec methods when called in the incorrect state.
     *
     * A selective test to ensure proper exceptions are thrown from MediaCodec
     * methods when called in incorrect operational states.
     */
    public void testException() throws Exception {
        boolean tested = false;
        // audio decoder (MP3 should be present on all Android devices)
        MediaFormat format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_MPEG, 44100 /* sampleRate */, 2 /* channelCount */);
        tested = verifyException(format, false /* isEncoder */) || tested;

        // audio encoder (AMR-WB may not be present on some Android devices)
        format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AMR_WB, 16000 /* sampleRate */, 1 /* channelCount */);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 19850);
        tested = verifyException(format, true /* isEncoder */) || tested;

        // video decoder (H.264/AVC may not be present on some Android devices)
        format = createMediaFormat();
        tested = verifyException(format, false /* isEncoder */) || tested;

        // video encoder (H.264/AVC may not be present on some Android devices)
        tested = verifyException(format, true /* isEncoder */) || tested;

        // signal test is skipped due to no device media codecs.
        if (!tested) {
            MediaUtils.skipTest(TAG, "cannot find any compatible device codecs");
        }
    }

    // wrap MediaCodec encoder and decoder creation
    private static MediaCodec createCodecByType(String type, boolean isEncoder)
            throws IOException {
        if (isEncoder) {
            return MediaCodec.createEncoderByType(type);
        }
        return MediaCodec.createDecoderByType(type);
    }

    private static void logMediaCodecException(MediaCodec.CodecException ex) {
        if (ex.isRecoverable()) {
            Log.w(TAG, "CodecException Recoverable: " + ex.getErrorCode());
        } else if (ex.isTransient()) {
            Log.w(TAG, "CodecException Transient: " + ex.getErrorCode());
        } else {
            Log.w(TAG, "CodecException Fatal: " + ex.getErrorCode());
        }
    }

    private static boolean verifyException(MediaFormat format, boolean isEncoder)
            throws IOException {
        String mimeType = format.getString(MediaFormat.KEY_MIME);
        if (!supportsCodec(mimeType, isEncoder)) {
            Log.i(TAG, "No " + (isEncoder ? "encoder" : "decoder")
                    + " found for mimeType= " + mimeType);
            return false;
        }

        final boolean isVideoEncoder = isEncoder && mimeType.startsWith("video/");

        // create codec (enter Initialized State)
        MediaCodec codec;

        // create improperly
        final String methodName = isEncoder ? "createEncoderByType" : "createDecoderByType";
        try {
            codec = createCodecByType(null, isEncoder);
            fail(methodName + " should return NullPointerException on null");
        } catch (NullPointerException e) { // expected
        }
        try {
            codec = createCodecByType("foobarplan9", isEncoder); // invalid type
            fail(methodName + " should return IllegalArgumentException on invalid type");
        } catch (IllegalArgumentException e) { // expected
        }
        try {
            codec = MediaCodec.createByCodecName("foobarplan9"); // invalid name
            fail(methodName + " should return IllegalArgumentException on invalid name");
        } catch (IllegalArgumentException e) { // expected
        }
        // correct
        codec = createCodecByType(format.getString(MediaFormat.KEY_MIME), isEncoder);

        // test a few commands
        try {
            codec.start();
            fail("start should return IllegalStateException when in Initialized state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("start should not return MediaCodec.CodecException on wrong state");
        } catch (IllegalStateException e) { // expected
        }
        try {
            codec.flush();
            fail("flush should return IllegalStateException when in Initialized state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("flush should not return MediaCodec.CodecException on wrong state");
        } catch (IllegalStateException e) { // expected
        }
        MediaCodecInfo codecInfo = codec.getCodecInfo(); // obtaining the codec info now is fine.
        try {
            int bufIndex = codec.dequeueInputBuffer(0);
            fail("dequeueInputBuffer should return IllegalStateException"
                    + " when in the Initialized state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("dequeueInputBuffer should not return MediaCodec.CodecException"
                    + " on wrong state");
        } catch (IllegalStateException e) { // expected
        }
        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int bufIndex = codec.dequeueOutputBuffer(info, 0);
            fail("dequeueOutputBuffer should return IllegalStateException"
                    + " when in the Initialized state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("dequeueOutputBuffer should not return MediaCodec.CodecException"
                    + " on wrong state");
        } catch (IllegalStateException e) { // expected
        }

        // configure (enter Configured State)

        // configure improperly
        try {
            codec.configure(format, null /* surface */, null /* crypto */,
                    isEncoder ? 0 : MediaCodec.CONFIGURE_FLAG_ENCODE /* flags */);
            fail("configure needs MediaCodec.CONFIGURE_FLAG_ENCODE for encoders only");
        } catch (MediaCodec.CodecException e) { // expected
            logMediaCodecException(e);
        } catch (IllegalStateException e) {
            fail("configure should not return IllegalStateException when improperly configured");
        }
        // correct
        codec.configure(format, null /* surface */, null /* crypto */,
                isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0 /* flags */);

        // test a few commands
        try {
            codec.flush();
            fail("flush should return IllegalStateException when in Configured state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("flush should not return MediaCodec.CodecException on wrong state");
        } catch (IllegalStateException e) { // expected
        }
        try {
            Surface surface = codec.createInputSurface();
            if (!isEncoder) {
                fail("createInputSurface should not work on a decoder");
            }
        } catch (IllegalStateException |
                 IllegalArgumentException e) { // expected for decoder and audio encoder
            if (isVideoEncoder) {
                throw e;
            }
        }

        // test getInputBuffers before start()
        try {
            ByteBuffer[] buffers = codec.getInputBuffers();
            fail("getInputBuffers called before start() should throw exception");
        } catch (IllegalStateException e) { // expected
        }

        // start codec (enter Executing state)
        codec.start();

        // test getInputBuffers after start()
        try {
            ByteBuffer[] buffers = codec.getInputBuffers();
            if (buffers == null) {
                fail("getInputBuffers called after start() should not return null");
            }
            if (isVideoEncoder && buffers.length > 0) {
                fail("getInputBuffers returned non-zero length array with input surface");
            }
        } catch (IllegalStateException e) {
            fail("getInputBuffers called after start() shouldn't throw exception");
        }

        // test a few commands
        try {
            codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
            fail("configure should return IllegalStateException when in Executing state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            // TODO: consider configuring after a flush.
            fail("configure should not return MediaCodec.CodecException on wrong state");
        } catch (IllegalStateException e) { // expected
        }

        // two flushes should be fine.
        codec.flush();
        codec.flush();

        // stop codec (enter Initialized state)
        // two stops should be fine.
        codec.stop();
        codec.stop();

        // release codec (enter Uninitialized state)
        // two releases should be fine.
        codec.release();
        codec.release();

        try {
            codecInfo = codec.getCodecInfo();
            fail("getCodecInfo should should return IllegalStateException" +
                    " when in Uninitialized state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("getCodecInfo should not return MediaCodec.CodecException on wrong state");
        } catch (IllegalStateException e) { // expected
        }
        try {
            codec.stop();
            fail("stop should return IllegalStateException when in Uninitialized state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("stop should not return MediaCodec.CodecException on wrong state");
        } catch (IllegalStateException e) { // expected
        }
        return true;
    }

    /**
     * Tests:
     * <br> calling createInputSurface() before configure() throws exception
     * <br> calling createInputSurface() after start() throws exception
     * <br> calling createInputSurface() with a non-Surface color format is not required to throw exception
     */
    public void testCreateInputSurfaceErrors() {
        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE);
            return;
        }

        MediaFormat format = createMediaFormat();
        MediaCodec encoder = null;
        Surface surface = null;

        // Replace color format with something that isn't COLOR_FormatSurface.
        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        int colorFormat = findNonSurfaceColorFormat(codecInfo, MIME_TYPE);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);

        try {
            try {
                encoder = MediaCodec.createByCodecName(codecInfo.getName());
            } catch (IOException e) {
                fail("failed to create codec " + codecInfo.getName());
            }
            try {
                surface = encoder.createInputSurface();
                fail("createInputSurface should not work pre-configure");
            } catch (IllegalStateException ise) {
                // good
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            try {
                surface = encoder.createInputSurface();
                fail("createInputSurface should not work post-start");
            } catch (IllegalStateException ise) {
                // good
            }
        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
        }
        assertNull(surface);
    }

    /**
     * Tests:
     * <br> signaling end-of-stream before any data is sent works
     * <br> signaling EOS twice throws exception
     * <br> submitting a frame after EOS throws exception [TODO]
     */
    public void testSignalSurfaceEOS() {
        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE);
            return;
        }

        MediaFormat format = createMediaFormat();
        MediaCodec encoder = null;
        InputSurface inputSurface = null;

        try {
            try {
                encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            } catch (IOException e) {
                fail("failed to create " + MIME_TYPE + " encoder");
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
            encoder.start();

            // send an immediate EOS
            encoder.signalEndOfInputStream();

            try {
                encoder.signalEndOfInputStream();
                fail("should not be able to signal EOS twice");
            } catch (IllegalStateException ise) {
                // good
            }

            // submit a frame post-EOS
            GLES20.glClearColor(0.0f, 0.5f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            try {
                inputSurface.swapBuffers();
                if (false) {    // TODO
                    fail("should not be able to submit frame after EOS");
                }
            } catch (Exception ex) {
                // good
            }
        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (inputSurface != null) {
                inputSurface.release();
            }
        }
    }

    /**
     * Tests:
     * <br> stopping with buffers in flight doesn't crash or hang
     */
    public void testAbruptStop() {
        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE);
            return;
        }

        // There appears to be a race, so run it several times with a short delay between runs
        // to allow any previous activity to shut down.
        for (int i = 0; i < 50; i++) {
            Log.d(TAG, "testAbruptStop " + i);
            doTestAbruptStop();
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
        }
    }
    private void doTestAbruptStop() {
        MediaFormat format = createMediaFormat();
        MediaCodec encoder = null;
        InputSurface inputSurface = null;

        try {
            try {
                encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            } catch (IOException e) {
                fail("failed to create " + MIME_TYPE + " encoder");
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
            encoder.start();

            int totalBuffers = encoder.getOutputBuffers().length;
            if (VERBOSE) Log.d(TAG, "Total buffers: " + totalBuffers);

            // Submit several frames quickly, without draining the encoder output, to try to
            // ensure that we've got some queued up when we call stop().  If we do too many
            // we'll block in swapBuffers().
            for (int i = 0; i < totalBuffers; i++) {
                GLES20.glClearColor(0.0f, (i % 8) / 8.0f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                inputSurface.swapBuffers();
            }
            Log.d(TAG, "stopping");
            encoder.stop();
            Log.d(TAG, "stopped");
        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (inputSurface != null) {
                inputSurface.release();
            }
        }
    }

    public void testReleaseAfterFlush() throws IOException, InterruptedException {
        String mimes[] = new String[] { MIME_TYPE, MIME_TYPE_AUDIO};
        for (String mime : mimes) {
            if (!MediaUtils.checkEncoder(mime)) {
                continue;
            }
            testReleaseAfterFlush(mime);
        }
    }

    private void testReleaseAfterFlush(String mime) throws IOException, InterruptedException {
        CountDownLatch buffersExhausted = null;
        CountDownLatch codecFlushed = null;
        AtomicInteger numBuffers = null;

        // sync flush from same thread
        MediaCodec encoder = MediaCodec.createEncoderByType(mime);
        runReleaseAfterFlush(mime, encoder, buffersExhausted, codecFlushed, numBuffers);

        // sync flush from different thread
        encoder = MediaCodec.createEncoderByType(mime);
        buffersExhausted = new CountDownLatch(1);
        codecFlushed = new CountDownLatch(1);
        numBuffers = new AtomicInteger();
        Thread flushThread = new FlushThread(encoder, buffersExhausted, codecFlushed);
        flushThread.start();
        runReleaseAfterFlush(mime, encoder, buffersExhausted, codecFlushed, numBuffers);
        flushThread.join();

        // async
        // This value is calculated in getOutputBufferIndices by calling dequeueOutputBuffer
        // with a fixed timeout until buffers are exhausted; it is possible that random timing
        // in dequeueOutputBuffer can result in a smaller `nBuffs` than the max possible value.
        int nBuffs = numBuffers.get();
        HandlerThread callbackThread = new HandlerThread("ReleaseAfterFlushCallbackThread");
        callbackThread.start();
        Handler handler = new Handler(callbackThread.getLooper());

        // async flush from same thread
        encoder = MediaCodec.createEncoderByType(mime);
        buffersExhausted = null;
        codecFlushed = null;
        ReleaseAfterFlushCallback callback =
                new ReleaseAfterFlushCallback(mime, encoder, buffersExhausted, codecFlushed, nBuffs);
        encoder.setCallback(callback, handler); // setCallback before configure, which is called in run
        callback.run(); // drive input on main thread

        // async flush from different thread
        encoder = MediaCodec.createEncoderByType(mime);
        buffersExhausted = new CountDownLatch(1);
        codecFlushed = new CountDownLatch(1);
        callback = new ReleaseAfterFlushCallback(mime, encoder, buffersExhausted, codecFlushed, nBuffs);
        encoder.setCallback(callback, handler);
        flushThread = new FlushThread(encoder, buffersExhausted, codecFlushed);
        flushThread.start();
        callback.run();
        flushThread.join();

        callbackThread.quitSafely();
        callbackThread.join();
    }

    public void testAsyncFlushAndReset() throws Exception, InterruptedException {
        testAsyncReset(false /* testStop */);
    }

    public void testAsyncStopAndReset() throws Exception, InterruptedException {
        testAsyncReset(true /* testStop */);
    }

    private void testAsyncReset(boolean testStop) throws Exception, InterruptedException {
        // Test video and audio 10x each
        for (int i = 0; i < 10; i++) {
            testAsyncReset(false /* audio */, (i % 2) == 0 /* swap */, testStop);
        }
        for (int i = 0; i < 10; i++) {
            testAsyncReset(true /* audio */, (i % 2) == 0 /* swap */, testStop);
        }
    }

    /*
     * This method simulates a race between flush (or stop) and reset() called from
     * two threads. Neither call should get stuck. This should be run multiple rounds.
     */
    private void testAsyncReset(boolean audio, boolean swap, final boolean testStop)
            throws Exception, InterruptedException {
        String mimeTypePrefix  = audio ? "audio/" : "video/";
        final MediaExtractor mediaExtractor = getMediaExtractorForMimeType(
                INPUT_RESOURCE_ID, mimeTypePrefix);
        MediaFormat mediaFormat = mediaExtractor.getTrackFormat(
                mediaExtractor.getSampleTrackIndex());
        if (!MediaUtils.checkDecoderForFormat(mediaFormat)) {
            return; // skip
        }

        OutputSurface outputSurface = audio ? null : new OutputSurface(1, 1);
        final Surface surface = outputSurface == null ? null : outputSurface.getSurface();

        String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
        final MediaCodec mediaCodec = MediaCodec.createDecoderByType(mimeType);

        try {
            mediaCodec.configure(mediaFormat, surface, null /* crypto */, 0 /* flags */);

            mediaCodec.start();

            assertTrue(runDecodeTillFirstOutput(mediaCodec, mediaExtractor));

            Thread flushingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (testStop) {
                            mediaCodec.stop();
                        } else {
                            mediaCodec.flush();
                        }
                    } catch (IllegalStateException e) {
                        // This is okay, since we're simulating a race between flush and reset.
                        // If reset executed first, flush could fail.
                    }
                }
            });

            Thread resettingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    mediaCodec.reset();
                }
            });

            // start flushing (or stopping) and resetting in two threads
            if (swap) {
                flushingThread.start();
                resettingThread.start();
            } else {
                resettingThread.start();
                flushingThread.start();
            }

            // wait for at most 5 sec, and check if the thread exits properly
            flushingThread.join(5000);
            assertFalse(flushingThread.isAlive());

            resettingThread.join(5000);
            assertFalse(resettingThread.isAlive());
        } finally {
            if (mediaCodec != null) {
                mediaCodec.release();
            }
            if (mediaExtractor != null) {
                mediaExtractor.release();
            }
            if (outputSurface != null) {
                outputSurface.release();
            }
        }
    }

    private static class FlushThread extends Thread {
        final MediaCodec mEncoder;
        final CountDownLatch mBuffersExhausted;
        final CountDownLatch mCodecFlushed;

        FlushThread(MediaCodec encoder, CountDownLatch buffersExhausted,
                CountDownLatch codecFlushed) {
            mEncoder = encoder;
            mBuffersExhausted = buffersExhausted;
            mCodecFlushed = codecFlushed;
        }

        @Override
        public void run() {
            try {
                mBuffersExhausted.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "buffersExhausted wait interrupted; flushing immediately.", e);
            }
            mEncoder.flush();
            mCodecFlushed.countDown();
        }
    }

    private static class ReleaseAfterFlushCallback extends MediaCodec.Callback implements Runnable {
        final String mMime;
        final MediaCodec mEncoder;
        final CountDownLatch mBuffersExhausted, mCodecFlushed;
        final int mNumBuffersBeforeFlush;

        CountDownLatch mStopInput = new CountDownLatch(1);
        List<Integer> mInputBufferIndices = new ArrayList<>();
        List<Integer> mOutputBufferIndices = new ArrayList<>();

        ReleaseAfterFlushCallback(String mime,
                MediaCodec encoder,
                CountDownLatch buffersExhausted,
                CountDownLatch codecFlushed,
                int numBuffersBeforeFlush) {
            mMime = mime;
            mEncoder = encoder;
            mBuffersExhausted = buffersExhausted;
            mCodecFlushed = codecFlushed;
            mNumBuffersBeforeFlush = numBuffersBeforeFlush;
        }

        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            assertTrue("video onInputBufferAvailable " + index, mMime.startsWith("audio/"));
            synchronized (mInputBufferIndices) {
                mInputBufferIndices.add(index);
            };
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index, BufferInfo info) {
            mOutputBufferIndices.add(index);
            if (mOutputBufferIndices.size() == mNumBuffersBeforeFlush) {
                releaseAfterFlush(codec, mOutputBufferIndices, mBuffersExhausted, mCodecFlushed);
                mStopInput.countDown();
            }
        }

        @Override
        public void onError(MediaCodec codec, CodecException e) {
            Log.e(TAG, codec + " onError", e);
            fail(codec + " onError " + e.getMessage());
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            Log.v(TAG, codec + " onOutputFormatChanged " + format);
        }

        @Override
        public void run() {
            InputSurface inputSurface = null;
            try {
                inputSurface = initCodecAndSurface(mMime, mEncoder);
                do {
                    int inputIndex = -1;
                    if (inputSurface == null) {
                        // asynchronous audio codec
                        synchronized (mInputBufferIndices) {
                            if (mInputBufferIndices.isEmpty()) {
                                continue;
                            } else {
                                inputIndex = mInputBufferIndices.remove(0);
                            }
                        }
                    }
                    feedEncoder(mEncoder, inputSurface, inputIndex);
                } while (!mStopInput.await(TIMEOUT_USEC, TimeUnit.MICROSECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "mEncoder input frames interrupted/stopped", e);
            } finally {
                cleanupCodecAndSurface(mEncoder, inputSurface);
            }
        }
    }

    private static void runReleaseAfterFlush(
            String mime,
            MediaCodec encoder,
            CountDownLatch buffersExhausted,
            CountDownLatch codecFlushed,
            AtomicInteger numBuffers) {
        InputSurface inputSurface = null;
        try {
            inputSurface = initCodecAndSurface(mime, encoder);
            List<Integer> outputBufferIndices = getOutputBufferIndices(encoder, inputSurface);
            if (numBuffers != null) {
                numBuffers.set(outputBufferIndices.size());
            }
            releaseAfterFlush(encoder, outputBufferIndices, buffersExhausted, codecFlushed);
        } finally {
            cleanupCodecAndSurface(encoder, inputSurface);
        }
    }

    private static InputSurface initCodecAndSurface(String mime, MediaCodec encoder) {
        MediaFormat format;
        InputSurface inputSurface = null;
        if (mime.startsWith("audio/")) {
            format = MediaFormat.createAudioFormat(mime, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, AUDIO_AAC_PROFILE);
            format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } else if (MIME_TYPE.equals(mime)) {
            CodecInfo info = getAvcSupportedFormatInfo();
            format = MediaFormat.createVideoFormat(mime, info.mMaxW, info.mMaxH);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, info.mBitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, info.mFps);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
            OutputSurface outputSurface = new OutputSurface(1, 1);
            encoder.configure(format, outputSurface.getSurface(), null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
        } else {
            throw new IllegalArgumentException("unsupported mime type: " + mime);
        }
        encoder.start();
        return inputSurface;
    }

    private static void cleanupCodecAndSurface(MediaCodec encoder, InputSurface inputSurface) {
        if (encoder != null) {
            encoder.stop();
            encoder.release();
        }

        if (inputSurface != null) {
            inputSurface.release();
        }
    }

    private static List<Integer> getOutputBufferIndices(MediaCodec encoder, InputSurface inputSurface) {
        boolean feedMoreFrames;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        List<Integer> indices = new ArrayList<>();
        do {
            feedMoreFrames = indices.isEmpty();
            feedEncoder(encoder, inputSurface, -1);
            // dequeue buffers until not available
            int index = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
            while (index >= 0) {
                indices.add(index);
                index = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC_SHORT);
            }
        } while (feedMoreFrames);
        assertFalse(indices.isEmpty());
        return indices;
    }

    /**
     * @param encoder audio/video encoder
     * @param inputSurface null for and only for audio encoders
     * @param inputIndex only used for audio; if -1 the function would attempt to dequeue from encoder;
     * do not use -1 for asynchronous encoders
     */
    private static void feedEncoder(MediaCodec encoder, InputSurface inputSurface, int inputIndex) {
        if (inputSurface == null) {
            // audio
            while (inputIndex == -1) {
                inputIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
            }
            ByteBuffer inputBuffer = encoder.getInputBuffer(inputIndex);;
            for (int i = 0; i < inputBuffer.capacity() / 2; i++) {
                inputBuffer.putShort((short)i);
            }
            encoder.queueInputBuffer(inputIndex, 0, inputBuffer.limit(), 0, 0);
        } else {
            // video
            GLES20.glClearColor(0.0f, 0.5f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            inputSurface.swapBuffers();
        }
    }

    private static void releaseAfterFlush(
            MediaCodec encoder,
            List<Integer> outputBufferIndices,
            CountDownLatch buffersExhausted,
            CountDownLatch codecFlushed) {
        if (buffersExhausted == null) {
            // flush from same thread
            encoder.flush();
        } else {
            assertNotNull(codecFlushed);
            buffersExhausted.countDown();
            try {
                codecFlushed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "codecFlushed wait interrupted; releasing buffers immediately.", e);
            }
        }

        for (int index : outputBufferIndices) {
            try {
                encoder.releaseOutputBuffer(index, true);
                fail("MediaCodec releaseOutputBuffer after flush() does not throw exception");
            } catch (MediaCodec.CodecException e) {
                // Expected
            }
        }
    }

    /**
     * Tests:
     * <br> dequeueInputBuffer() fails when encoder configured with an input Surface
     */
    public void testDequeueSurface() {
        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE);
            return;
        }

        MediaFormat format = createMediaFormat();
        MediaCodec encoder = null;
        Surface surface = null;

        try {
            try {
                encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            } catch (IOException e) {
                fail("failed to create " + MIME_TYPE + " encoder");
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = encoder.createInputSurface();
            encoder.start();

            try {
                encoder.dequeueInputBuffer(-1);
                fail("dequeueInputBuffer should fail on encoder with input surface");
            } catch (IllegalStateException ise) {
                // good
            }

            PersistableBundle metrics = encoder.getMetrics();
            if (metrics == null) {
                fail("getMetrics() returns null");
            } else if (metrics.isEmpty()) {
                fail("getMetrics() returns empty results");
            }
            int encoding = metrics.getInt(MediaCodec.MetricsConstants.ENCODER, -1);
            if (encoding != 1) {
                fail("getMetrics() returns bad encoder value " + encoding);
            }
            String theCodec = metrics.getString(MediaCodec.MetricsConstants.CODEC, null);
            if (theCodec == null) {
                fail("getMetrics() returns null codec value ");
            }

        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (surface != null) {
                surface.release();
            }
        }
    }

    /**
     * Tests:
     * <br> configure() encoder with Surface, re-configure() without Surface works
     * <br> sending EOS with signalEndOfInputStream on non-Surface encoder fails
     */
    public void testReconfigureWithoutSurface() {
        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE);
            return;
        }

        MediaFormat format = createMediaFormat();
        MediaCodec encoder = null;
        Surface surface = null;

        try {
            try {
                encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            } catch (IOException e) {
                fail("failed to create " + MIME_TYPE + " encoder");
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = encoder.createInputSurface();
            encoder.start();

            encoder.getOutputBuffers();

            // re-configure, this time without an input surface
            if (VERBOSE) Log.d(TAG, "reconfiguring");
            encoder.stop();
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            if (VERBOSE) Log.d(TAG, "reconfigured");

            encoder.getOutputBuffers();
            encoder.dequeueInputBuffer(-1);

            try {
                encoder.signalEndOfInputStream();
                fail("signalEndOfInputStream only works on surface input");
            } catch (IllegalStateException ise) {
                // good
            }

            PersistableBundle metrics = encoder.getMetrics();
            if (metrics == null) {
                fail("getMetrics() returns null");
            } else if (metrics.isEmpty()) {
                fail("getMetrics() returns empty results");
            }
            int encoding = metrics.getInt(MediaCodec.MetricsConstants.ENCODER, -1);
            if (encoding != 1) {
                fail("getMetrics() returns bad encoder value " + encoding);
            }
            String theCodec = metrics.getString(MediaCodec.MetricsConstants.CODEC, null);
            if (theCodec == null) {
                fail("getMetrics() returns null codec value ");
            }

        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (surface != null) {
                surface.release();
            }
        }
    }

    public void testDecodeAfterFlush() throws InterruptedException {
        testDecodeAfterFlush(true /* audio */);
        testDecodeAfterFlush(false /* audio */);
    }

    private void testDecodeAfterFlush(final boolean audio) throws InterruptedException {
        final AtomicBoolean completed = new AtomicBoolean(false);
        Thread decodingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                OutputSurface outputSurface = null;
                MediaExtractor mediaExtractor = null;
                MediaCodec mediaCodec = null;
                try {
                    String mimeTypePrefix  = audio ? "audio/" : "video/";
                    if (!audio) {
                        outputSurface = new OutputSurface(1, 1);
                    }
                    mediaExtractor = getMediaExtractorForMimeType(INPUT_RESOURCE_ID, mimeTypePrefix);
                    MediaFormat mediaFormat =
                            mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex());
                    if (!MediaUtils.checkDecoderForFormat(mediaFormat)) {
                        completed.set(true);
                        return; // skip
                    }
                    String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
                    mediaCodec = MediaCodec.createDecoderByType(mimeType);
                    mediaCodec.configure(mediaFormat, outputSurface == null ? null : outputSurface.getSurface(),
                            null /* crypto */, 0 /* flags */);
                    mediaCodec.start();

                    if (!runDecodeTillFirstOutput(mediaCodec, mediaExtractor)) {
                        throw new RuntimeException("decoder does not generate non-empty output.");
                    }

                    PersistableBundle metrics = mediaCodec.getMetrics();
                    if (metrics == null) {
                        fail("getMetrics() returns null");
                    } else if (metrics.isEmpty()) {
                        fail("getMetrics() returns empty results");
                    }
                    int encoder = metrics.getInt(MediaCodec.MetricsConstants.ENCODER, -1);
                    if (encoder != 0) {
                        fail("getMetrics() returns bad encoder value " + encoder);
                    }
                    String theCodec = metrics.getString(MediaCodec.MetricsConstants.CODEC, null);
                    if (theCodec == null) {
                        fail("getMetrics() returns null codec value ");
                    }


                    // simulate application flush.
                    mediaExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    mediaCodec.flush();

                    completed.set(runDecodeTillFirstOutput(mediaCodec, mediaExtractor));
                    metrics = mediaCodec.getMetrics();
                    if (metrics == null) {
                        fail("getMetrics() returns null");
                    } else if (metrics.isEmpty()) {
                        fail("getMetrics() returns empty results");
                    }
                    int encoding = metrics.getInt(MediaCodec.MetricsConstants.ENCODER, -1);
                    if (encoding != 0) {
                        fail("getMetrics() returns bad encoder value " + encoding);
                    }
                    String theCodec2 = metrics.getString(MediaCodec.MetricsConstants.CODEC, null);
                    if (theCodec2 == null) {
                        fail("getMetrics() returns null codec value ");
                    }

                } catch (IOException e) {
                    throw new RuntimeException("error setting up decoding", e);
                } finally {
                    if (mediaCodec != null) {
                        mediaCodec.stop();

                        PersistableBundle metrics = mediaCodec.getMetrics();
                        if (metrics == null) {
                            fail("getMetrics() returns null");
                        } else if (metrics.isEmpty()) {
                            fail("getMetrics() returns empty results");
                        }
                        int encoder = metrics.getInt(MediaCodec.MetricsConstants.ENCODER, -1);
                        if (encoder != 0) {
                            fail("getMetrics() returns bad encoder value " + encoder);
                        }
                        String theCodec = metrics.getString(MediaCodec.MetricsConstants.CODEC, null);
                        if (theCodec == null) {
                            fail("getMetrics() returns null codec value ");
                        }

                        mediaCodec.release();
                    }
                    if (mediaExtractor != null) {
                        mediaExtractor.release();
                    }
                    if (outputSurface != null) {
                        outputSurface.release();
                    }
                }
            }
        });
        decodingThread.start();
        decodingThread.join(DECODING_TIMEOUT_MS);
        // In case it's timed out, need to stop the thread and have all resources released.
        decodingThread.interrupt();
        if (!completed.get()) {
            throw new RuntimeException("timed out decoding to end-of-stream");
        }
    }

    // Run the decoder till it generates an output buffer.
    // Return true when that output buffer is not empty, false otherwise.
    private static boolean runDecodeTillFirstOutput(
            MediaCodec mediaCodec, MediaExtractor mediaExtractor) {
        final int TIME_OUT_US = 10000;

        assertTrue("Wrong test stream which has no data.",
                mediaExtractor.getSampleTrackIndex() != -1);
        boolean signaledEos = false;
        MediaCodec.BufferInfo outputBufferInfo = new MediaCodec.BufferInfo();
        while (!Thread.interrupted()) {
            // Try to feed more data into the codec.
            if (!signaledEos) {
                int bufferIndex = mediaCodec.dequeueInputBuffer(TIME_OUT_US /* timeoutUs */);
                if (bufferIndex != -1) {
                    ByteBuffer buffer = mediaCodec.getInputBuffer(bufferIndex);
                    int size = mediaExtractor.readSampleData(buffer, 0 /* offset */);
                    long timestampUs = mediaExtractor.getSampleTime();
                    mediaExtractor.advance();
                    signaledEos = mediaExtractor.getSampleTrackIndex() == -1;
                    mediaCodec.queueInputBuffer(bufferIndex,
                            0 /* offset */,
                            size,
                            timestampUs,
                            signaledEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    Log.i("DEBUG", "queue with " + signaledEos);
                }
            }

            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(
                    outputBufferInfo, TIME_OUT_US /* timeoutUs */);

            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
                    || outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                    || outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                continue;
            }
            assertTrue("Wrong output buffer index", outputBufferIndex >= 0);

            PersistableBundle metrics = mediaCodec.getMetrics();
            Log.d(TAG, "getMetrics after first buffer metrics says: " + metrics);

            int encoder = metrics.getInt(MediaCodec.MetricsConstants.ENCODER, -1);
            if (encoder != 0) {
                fail("getMetrics() returns bad encoder value " + encoder);
            }
            String theCodec = metrics.getString(MediaCodec.MetricsConstants.CODEC, null);
            if (theCodec == null) {
                fail("getMetrics() returns null codec value ");
            }

            mediaCodec.releaseOutputBuffer(outputBufferIndex, false /* render */);
            boolean eos = (outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            Log.i("DEBUG", "Got a frame with eos=" + eos);
            if (eos && outputBufferInfo.size == 0) {
                return false;
            } else {
                return true;
            }
        }

        return false;
    }

    /**
     * Tests whether decoding a short group-of-pictures succeeds. The test queues a few video frames
     * then signals end-of-stream. The test fails if the decoder doesn't output the queued frames.
     */
    public void testDecodeShortInput() throws InterruptedException {
        // Input buffers from this input video are queued up to and including the video frame with
        // timestamp LAST_BUFFER_TIMESTAMP_US.
        final int INPUT_RESOURCE_ID =
                R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
        final long LAST_BUFFER_TIMESTAMP_US = 166666;

        // The test should fail if the decoder never produces output frames for the truncated input.
        // Time out decoding, as we have no way to query whether the decoder will produce output.
        final int DECODING_TIMEOUT_MS = 2000;

        final AtomicBoolean completed = new AtomicBoolean();
        Thread videoDecodingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                completed.set(runDecodeShortInput(INPUT_RESOURCE_ID, LAST_BUFFER_TIMESTAMP_US));
            }
        });
        videoDecodingThread.start();
        videoDecodingThread.join(DECODING_TIMEOUT_MS);
        if (!completed.get()) {
            throw new RuntimeException("timed out decoding to end-of-stream");
        }
    }

    private boolean runDecodeShortInput(int inputResourceId, long lastBufferTimestampUs) {
        final int NO_BUFFER_INDEX = -1;

        OutputSurface outputSurface = null;
        MediaExtractor mediaExtractor = null;
        MediaCodec mediaCodec = null;
        try {
            outputSurface = new OutputSurface(1, 1);
            mediaExtractor = getMediaExtractorForMimeType(inputResourceId, "video/");
            MediaFormat mediaFormat =
                    mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex());
            String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (!supportsCodec(mimeType, false)) {
                Log.i(TAG, "No decoder found for mimeType= " + MIME_TYPE);
                return true;
            }
            mediaCodec =
                    MediaCodec.createDecoderByType(mimeType);
            mediaCodec.configure(mediaFormat, outputSurface.getSurface(), null, 0);
            mediaCodec.start();
            boolean eos = false;
            boolean signaledEos = false;
            MediaCodec.BufferInfo outputBufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = NO_BUFFER_INDEX;
            while (!eos && !Thread.interrupted()) {
                // Try to feed more data into the codec.
                if (mediaExtractor.getSampleTrackIndex() != -1 && !signaledEos) {
                    int bufferIndex = mediaCodec.dequeueInputBuffer(0);
                    if (bufferIndex != NO_BUFFER_INDEX) {
                        ByteBuffer buffer = mediaCodec.getInputBuffers()[bufferIndex];
                        int size = mediaExtractor.readSampleData(buffer, 0);
                        long timestampUs = mediaExtractor.getSampleTime();
                        mediaExtractor.advance();
                        signaledEos = mediaExtractor.getSampleTrackIndex() == -1
                                || timestampUs == lastBufferTimestampUs;
                        mediaCodec.queueInputBuffer(bufferIndex,
                                0,
                                size,
                                timestampUs,
                                signaledEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    }
                }

                // If we don't have an output buffer, try to get one now.
                if (outputBufferIndex == NO_BUFFER_INDEX) {
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(outputBufferInfo, 0);
                }

                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
                        || outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                        || outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    outputBufferIndex = NO_BUFFER_INDEX;
                } else if (outputBufferIndex != NO_BUFFER_INDEX) {
                    eos = (outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    boolean render = outputBufferInfo.size > 0;
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, render);
                    if (render) {
                        outputSurface.awaitNewImage();
                    }

                    outputBufferIndex = NO_BUFFER_INDEX;
                }
            }

            return eos;
        } catch (IOException e) {
            throw new RuntimeException("error reading input resource", e);
        } finally {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            if (mediaExtractor != null) {
                mediaExtractor.release();
            }
            if (outputSurface != null) {
                outputSurface.release();
            }
        }
    }

    /**
     * Tests creating two decoders for {@link #MIME_TYPE_AUDIO} at the same time.
     */
    public void testCreateTwoAudioDecoders() {
        final MediaFormat format = MediaFormat.createAudioFormat(
                MIME_TYPE_AUDIO, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT);

        MediaCodec audioDecoderA = null;
        MediaCodec audioDecoderB = null;
        try {
            try {
                audioDecoderA = MediaCodec.createDecoderByType(MIME_TYPE_AUDIO);
            } catch (IOException e) {
                fail("failed to create first " + MIME_TYPE_AUDIO + " decoder");
            }
            audioDecoderA.configure(format, null, null, 0);
            audioDecoderA.start();

            try {
                audioDecoderB = MediaCodec.createDecoderByType(MIME_TYPE_AUDIO);
            } catch (IOException e) {
                fail("failed to create second " + MIME_TYPE_AUDIO + " decoder");
            }
            audioDecoderB.configure(format, null, null, 0);
            audioDecoderB.start();
        } finally {
            if (audioDecoderB != null) {
                try {
                    audioDecoderB.stop();
                    audioDecoderB.release();
                } catch (RuntimeException e) {
                    Log.w(TAG, "exception stopping/releasing codec", e);
                }
            }

            if (audioDecoderA != null) {
                try {
                    audioDecoderA.stop();
                    audioDecoderA.release();
                } catch (RuntimeException e) {
                    Log.w(TAG, "exception stopping/releasing codec", e);
                }
            }
        }
    }

    /**
     * Tests creating an encoder and decoder for {@link #MIME_TYPE_AUDIO} at the same time.
     */
    public void testCreateAudioDecoderAndEncoder() {
        if (!supportsCodec(MIME_TYPE_AUDIO, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE_AUDIO);
            return;
        }

        if (!supportsCodec(MIME_TYPE_AUDIO, false)) {
            Log.i(TAG, "No decoder found for mimeType= " + MIME_TYPE_AUDIO);
            return;
        }

        final MediaFormat encoderFormat = MediaFormat.createAudioFormat(
                MIME_TYPE_AUDIO, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT);
        encoderFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, AUDIO_AAC_PROFILE);
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
        final MediaFormat decoderFormat = MediaFormat.createAudioFormat(
                MIME_TYPE_AUDIO, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT);

        MediaCodec audioEncoder = null;
        MediaCodec audioDecoder = null;
        try {
            try {
                audioEncoder = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO);
            } catch (IOException e) {
                fail("failed to create " + MIME_TYPE_AUDIO + " encoder");
            }
            audioEncoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();

            try {
                audioDecoder = MediaCodec.createDecoderByType(MIME_TYPE_AUDIO);
            } catch (IOException e) {
                fail("failed to create " + MIME_TYPE_AUDIO + " decoder");
            }
            audioDecoder.configure(decoderFormat, null, null, 0);
            audioDecoder.start();
        } finally {
            if (audioDecoder != null) {
                try {
                    audioDecoder.stop();
                    audioDecoder.release();
                } catch (RuntimeException e) {
                    Log.w(TAG, "exception stopping/releasing codec", e);
                }
            }

            if (audioEncoder != null) {
                try {
                    audioEncoder.stop();
                    audioEncoder.release();
                } catch (RuntimeException e) {
                    Log.w(TAG, "exception stopping/releasing codec", e);
                }
            }
        }
    }

    public void testConcurrentAudioVideoEncodings() throws InterruptedException {
        if (!supportsCodec(MIME_TYPE_AUDIO, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE_AUDIO);
            return;
        }

        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No decoder found for mimeType= " + MIME_TYPE);
            return;
        }

        final int VIDEO_NUM_SWAPS = 100;
        // audio only checks this and stop
        mVideoEncodingOngoing = true;
        final CodecInfo info = getAvcSupportedFormatInfo();
        long start = System.currentTimeMillis();
        Thread videoEncodingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runVideoEncoding(VIDEO_NUM_SWAPS, info);
            }
        });
        Thread audioEncodingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runAudioEncoding();
            }
        });
        videoEncodingThread.start();
        audioEncodingThread.start();
        videoEncodingThread.join();
        mVideoEncodingOngoing = false;
        audioEncodingThread.join();
        assertFalse("Video encoding error. Chekc logcat", mVideoEncoderHadError);
        assertFalse("Audio encoding error. Chekc logcat", mAudioEncoderHadError);
        long end = System.currentTimeMillis();
        Log.w(TAG, "Concurrent AV encoding took " + (end - start) + " ms for " + VIDEO_NUM_SWAPS +
                " video frames");
    }

    private static class CodecInfo {
        public int mMaxW;
        public int mMaxH;
        public int mFps;
        public int mBitRate;
    };

    public void testCryptoInfoPattern() {
        CryptoInfo info = new CryptoInfo();
        Pattern pattern = new Pattern(1 /*blocksToEncrypt*/, 2 /*blocksToSkip*/);
        if (pattern.getEncryptBlocks() != 1) {
            fail("Incorrect number of encrypt blocks in pattern");
        }
        if (pattern.getSkipBlocks() != 2) {
            fail("Incorrect number of skip blocks in pattern");
        }
        pattern.set(3 /*blocksToEncrypt*/, 4 /*blocksToSkip*/);
        if (pattern.getEncryptBlocks() != 3) {
            fail("Incorrect number of encrypt blocks in pattern");
        }
        if (pattern.getSkipBlocks() != 4) {
            fail("Incorrect number of skip blocks in pattern");
        }
        info.setPattern(pattern);
    }

    private static CodecInfo getAvcSupportedFormatInfo() {
        MediaCodecInfo mediaCodecInfo = selectCodec(MIME_TYPE);
        CodecCapabilities cap = mediaCodecInfo.getCapabilitiesForType(MIME_TYPE);
        if (cap == null) { // not supported
            return null;
        }
        CodecInfo info = new CodecInfo();
        int highestLevel = 0;
        for (CodecProfileLevel lvl : cap.profileLevels) {
            if (lvl.level > highestLevel) {
                highestLevel = lvl.level;
            }
        }
        int maxW = 0;
        int maxH = 0;
        int bitRate = 0;
        int fps = 0; // frame rate for the max resolution
        switch(highestLevel) {
            // Do not support Level 1 to 2.
            case CodecProfileLevel.AVCLevel1:
            case CodecProfileLevel.AVCLevel11:
            case CodecProfileLevel.AVCLevel12:
            case CodecProfileLevel.AVCLevel13:
            case CodecProfileLevel.AVCLevel1b:
            case CodecProfileLevel.AVCLevel2:
                return null;
            case CodecProfileLevel.AVCLevel21:
                maxW = 352;
                maxH = 576;
                bitRate = 4000000;
                fps = 25;
                break;
            case CodecProfileLevel.AVCLevel22:
                maxW = 720;
                maxH = 480;
                bitRate = 4000000;
                fps = 15;
                break;
            case CodecProfileLevel.AVCLevel3:
                maxW = 720;
                maxH = 480;
                bitRate = 10000000;
                fps = 30;
                break;
            case CodecProfileLevel.AVCLevel31:
                maxW = 1280;
                maxH = 720;
                bitRate = 14000000;
                fps = 30;
                break;
            case CodecProfileLevel.AVCLevel32:
                maxW = 1280;
                maxH = 720;
                bitRate = 20000000;
                fps = 60;
                break;
            case CodecProfileLevel.AVCLevel4: // only try up to 1080p
            default:
                maxW = 1920;
                maxH = 1080;
                bitRate = 20000000;
                fps = 30;
                break;
        }
        info.mMaxW = maxW;
        info.mMaxH = maxH;
        info.mFps = fps;
        info.mBitRate = bitRate;
        Log.i(TAG, "AVC Level 0x" + Integer.toHexString(highestLevel) + " bit rate " + bitRate +
                " fps " + info.mFps + " w " + maxW + " h " + maxH);

        return info;
    }

    private void runVideoEncoding(int numSwap, CodecInfo info) {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, info.mMaxW, info.mMaxH);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, info.mBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, info.mFps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        MediaCodec encoder = null;
        InputSurface inputSurface = null;
        mVideoEncoderHadError = false;
        try {
            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            encoder.start();
            for (int i = 0; i < numSwap; i++) {
                GLES20.glClearColor(0.0f, 0.5f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                inputSurface.swapBuffers();
                // dequeue buffers until not available
                int index = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                while (index >= 0) {
                    encoder.releaseOutputBuffer(index, false);
                    // just throw away output
                    // allow shorter wait for 2nd round to move on quickly.
                    index = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC_SHORT);
                }
            }
            encoder.signalEndOfInputStream();
        } catch (Throwable e) {
            Log.w(TAG, "runVideoEncoding got error: " + e);
            mVideoEncoderHadError = true;
        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (inputSurface != null) {
                inputSurface.release();
            }
        }
    }

    private void runAudioEncoding() {
        MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE_AUDIO, AUDIO_SAMPLE_RATE,
                AUDIO_CHANNEL_COUNT);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, AUDIO_AAC_PROFILE);
        format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
        MediaCodec encoder = null;
        mAudioEncoderHadError = false;
        try {
            encoder = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            encoder.start();
            ByteBuffer[] inputBuffers = encoder.getInputBuffers();
            ByteBuffer source = ByteBuffer.allocate(inputBuffers[0].capacity());
            for (int i = 0; i < source.capacity()/2; i++) {
                source.putShort((short)i);
            }
            source.rewind();
            int currentInputBufferIndex = 0;
            long encodingLatencySum = 0;
            int totalEncoded = 0;
            int numRepeat = 0;
            while (mVideoEncodingOngoing) {
                numRepeat++;
                int inputIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                while (inputIndex == -1) {
                    inputIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                }
                ByteBuffer inputBuffer = inputBuffers[inputIndex];
                inputBuffer.rewind();
                inputBuffer.put(source);
                long start = System.currentTimeMillis();
                totalEncoded += inputBuffers[inputIndex].limit();
                encoder.queueInputBuffer(inputIndex, 0, inputBuffer.limit(), 0, 0);
                source.rewind();
                int index = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                long end = System.currentTimeMillis();
                encodingLatencySum += (end - start);
                while (index >= 0) {
                    encoder.releaseOutputBuffer(index, false);
                    // just throw away output
                    // allow shorter wait for 2nd round to move on quickly.
                    index = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC_SHORT);
                }
            }
            Log.w(TAG, "Audio encoding average latency " + encodingLatencySum / numRepeat +
                    " ms for average write size " + totalEncoded / numRepeat +
                    " total latency " + encodingLatencySum + " ms for total bytes " + totalEncoded);
        } catch (Throwable e) {
            Log.w(TAG, "runAudioEncoding got error: " + e);
            mAudioEncoderHadError = true;
        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
        }
    }

    /**
     * Creates a MediaFormat with the basic set of values.
     */
    private static MediaFormat createMediaFormat() {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        return format;
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        // FIXME: select codecs based on the complete use-case, not just the mime
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo info : mcl.getCodecInfos()) {
            if (!info.isEncoder()) {
                continue;
            }

            String[] types = info.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return info;
                }
            }
        }
        return null;
    }

    /**
     * Returns a color format that is supported by the codec and isn't COLOR_FormatSurface.  Throws
     * an exception if none found.
     */
    private static int findNonSurfaceColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (colorFormat != MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                return colorFormat;
            }
        }
        fail("couldn't find a good color format for " + codecInfo.getName() + " / " + MIME_TYPE);
        return 0;   // not reached
    }

    private MediaExtractor getMediaExtractorForMimeType(int resourceId, String mimeTypePrefix)
            throws IOException {
        MediaExtractor mediaExtractor = new MediaExtractor();
        AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(resourceId);
        try {
            mediaExtractor.setDataSource(
                    afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } finally {
            afd.close();
        }
        int trackIndex;
        for (trackIndex = 0; trackIndex < mediaExtractor.getTrackCount(); trackIndex++) {
            MediaFormat trackMediaFormat = mediaExtractor.getTrackFormat(trackIndex);
            if (trackMediaFormat.getString(MediaFormat.KEY_MIME).startsWith(mimeTypePrefix)) {
                mediaExtractor.selectTrack(trackIndex);
                break;
            }
        }
        if (trackIndex == mediaExtractor.getTrackCount()) {
            throw new IllegalStateException("couldn't get a video track");
        }

        return mediaExtractor;
    }

    private static boolean supportsCodec(String mimeType, boolean encoder) {
        MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo info : list.getCodecInfos()) {
            if (encoder && !info.isEncoder()) {
                continue;
            }
            if (!encoder && info.isEncoder()) {
                continue;
            }

            for (String type : info.getSupportedTypes()) {
                if (type.equalsIgnoreCase(mimeType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final UUID CLEARKEY_SCHEME_UUID =
            new UUID(0x1077efecc0b24d02L, 0xace33c1e52e2fb4bL);

    /**
     * Tests:
     * <br> queueSecureInputBuffer() with erroneous input throws CryptoException
     * <br> getInputBuffer() after the failed queueSecureInputBuffer() succeeds.
     */
    public void testCryptoError() throws Exception {
        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE);
            return;
        }

        MediaDrm drm = new MediaDrm(CLEARKEY_SCHEME_UUID);
        byte[] sessionId = drm.openSession();
        MediaCrypto crypto = new MediaCrypto(CLEARKEY_SCHEME_UUID, new byte[0]);
        MediaCodec codec = MediaCodec.createDecoderByType(MIME_TYPE);

        try {
            crypto.setMediaDrmSession(sessionId);

            MediaCodec.CryptoInfo cryptoInfo = new MediaCodec.CryptoInfo();
            MediaFormat format = createMediaFormat();

            codec.configure(format, null, crypto, 0);
            codec.start();
            int index = codec.dequeueInputBuffer(-1);
            assertTrue(index >= 0);
            ByteBuffer buffer = codec.getInputBuffer(index);
            cryptoInfo.set(
                    1,
                    new int[] { 0 },
                    new int[] { buffer.capacity() },
                    new byte[16],
                    new byte[16],
                    // Trying to decrypt encrypted data in unencrypted mode
                    MediaCodec.CRYPTO_MODE_UNENCRYPTED);
            try {
                codec.queueSecureInputBuffer(index, 0, cryptoInfo, 0, 0);
                fail("queueSecureInputBuffer should fail when trying to decrypt " +
                        "encrypted data in unencrypted mode.");
            } catch (MediaCodec.CryptoException e) {
                // expected
            }
            buffer = codec.getInputBuffer(index);
            codec.stop();
        } finally {
            codec.release();
            crypto.release();
            drm.closeSession(sessionId);
        }
    }
}
