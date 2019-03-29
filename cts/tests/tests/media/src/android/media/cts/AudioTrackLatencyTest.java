/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.PlaybackParams;
import android.util.Log;

import com.android.compatibility.common.util.CtsAndroidTestCase;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

// Test the Java AudioTrack low latency related features:
//
// setBufferSizeInFrames()
// getBufferCapacityInFrames()
// ASSUME getMinBufferSize in frames is significantly lower than getBufferCapacityInFrames.
// This gives us room to adjust the sizes.
//
// getUnderrunCount()
// ASSUME normal track will underrun with setBufferSizeInFrames(0).
//
// AudioAttributes.FLAG_LOW_LATENCY
// ASSUME FLAG_LOW_LATENCY reduces output latency by more than 10 msec.
// Warns if not. This can happen if there is no Fast Mixer or if a FastTrack
// is not available.

public class AudioTrackLatencyTest extends CtsAndroidTestCase {
    private String TAG = "AudioTrackLatencyTest";
    private final static long NANOS_PER_MILLISECOND = 1000000L;
    private final static int MILLIS_PER_SECOND = 1000;
    private final static long NANOS_PER_SECOND = NANOS_PER_MILLISECOND * MILLIS_PER_SECOND;

    private void log(String testName, String message) {
        Log.i(TAG, "[" + testName + "] " + message);
    }

    private void logw(String testName, String message) {
        Log.w(TAG, "[" + testName + "] " + message);
    }

    private void loge(String testName, String message) {
        Log.e(TAG, "[" + testName + "] " + message);
    }

    public void testSetBufferSize() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetBufferSize";
        final int TEST_SR = 44100;
        final int TEST_CONF = AudioFormat.CHANNEL_OUT_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);

        // -------- test --------------
        // Initial values
        int bufferCapacity = track.getBufferCapacityInFrames();
        int initialBufferSize = track.getBufferSizeInFrames();
        assertTrue(TEST_NAME, bufferCapacity > 0);
        assertTrue(TEST_NAME, initialBufferSize > 0);
        assertTrue(TEST_NAME, initialBufferSize <= bufferCapacity);

        // set to various values
        int resultNegative = track.setBufferSizeInFrames(-1);
        assertEquals(TEST_NAME + ": negative size", AudioTrack.ERROR_BAD_VALUE, resultNegative);
        assertEquals(TEST_NAME + ": should be unchanged",
                initialBufferSize, track.getBufferSizeInFrames());

        int resultZero = track.setBufferSizeInFrames(0);
        assertTrue(TEST_NAME + ": should be >0, but got " + resultZero, resultZero > 0);
        assertTrue(TEST_NAME + ": zero size < original, but got " + resultZero,
                resultZero < initialBufferSize);
        assertEquals(TEST_NAME + ": should match resultZero",
                resultZero, track.getBufferSizeInFrames());

        int resultMax = track.setBufferSizeInFrames(Integer.MAX_VALUE);
        assertTrue(TEST_NAME + ": set MAX_VALUE, >", resultMax > resultZero);
        assertTrue(TEST_NAME + ": set MAX_VALUE, <=", resultMax <= bufferCapacity);
        assertEquals(TEST_NAME + ": should match resultMax",
                resultMax, track.getBufferSizeInFrames());

        int resultMiddle = track.setBufferSizeInFrames(bufferCapacity / 2);
        assertTrue(TEST_NAME + ": set middle, >", resultMiddle > resultZero);
        assertTrue(TEST_NAME + ": set middle, <=", resultMiddle < resultMax);
        assertEquals(TEST_NAME + ": should match resultMiddle",
                resultMiddle, track.getBufferSizeInFrames());

        // -------- tear down --------------
        track.release();
    }

    // Helper class for tests
    private static class TestSetup {
        public int sampleRate = 48000;
        public int samplesPerFrame = 2;
        public int bytesPerSample = 2;
        public int config = AudioFormat.CHANNEL_OUT_STEREO;
        public int format = AudioFormat.ENCODING_PCM_16BIT;
        public int mode = AudioTrack.MODE_STREAM;
        public int streamType = AudioManager.STREAM_MUSIC;
        public int framesPerBuffer = 256;
        public double amplitude = 0.5;

        private AudioTrack mTrack;
        private short[] mData;
        private int mActualSizeInFrames;

        AudioTrack createTrack() {
            mData = AudioHelper.createSineWavesShort(framesPerBuffer,
                    samplesPerFrame, 1, amplitude);
            int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, config, format);
            // Create a buffer that is 3/2 times bigger than the minimum.
            // This gives me room to cut it in half and play without glitching.
            // This is an arbitrary scaling factor.
            int bufferSize = (minBufferSize * 3) / 2;
            mTrack = new AudioTrack(streamType, sampleRate, config, format,
                    bufferSize, mode);

            // Calculate and use a smaller buffer size
            int smallBufferSize = bufferSize / 2; // arbitrary, smaller might underflow
            int smallBuffSizeInFrames = smallBufferSize / (samplesPerFrame * bytesPerSample);
            mActualSizeInFrames = mTrack.setBufferSizeInFrames(smallBuffSizeInFrames);
            return mTrack;

        }

        int primeAudioTrack(String testName) {
            // Prime the buffer.
            int samplesWrittenTotal = 0;
            int samplesWritten;
            do{
                samplesWritten = mTrack.write(mData, 0, mData.length);
                if (samplesWritten > 0) {
                    samplesWrittenTotal += samplesWritten;
                }
            } while (samplesWritten == mData.length);
            int framesWrittenTotal = samplesWrittenTotal / samplesPerFrame;
            assertTrue(testName + ": framesWrittenTotal = " + framesWrittenTotal
                    + ", size = " + mActualSizeInFrames,
                    framesWrittenTotal >= mActualSizeInFrames);
            return framesWrittenTotal;
        }

        /**
         * @param seconds
         */
        public void writeSeconds(double seconds) throws InterruptedException {
            long msecEnd = System.currentTimeMillis() + (long)(seconds * 1000);
            while (System.currentTimeMillis() < msecEnd) {
                // Use non-blocking mode in case the track is hung.
                int samplesWritten = mTrack.write(mData, 0, mData.length, AudioTrack.WRITE_NON_BLOCKING);
                if (samplesWritten < mData.length) {
                    int samplesRemaining = mData.length - samplesWritten;
                    int framesRemaining = samplesRemaining / samplesPerFrame;
                    int millis = (framesRemaining * 1000) / sampleRate;
                    Thread.sleep(millis);
                }
            }
        }
    }

    // Try to play an AudioTrack when the initial size is less than capacity.
    // We want to make sure the track starts properly and is not stuck.
    public void testPlaySmallBuffer() throws Exception {
        final String TEST_NAME = "testPlaySmallBuffer";
        TestSetup setup = new TestSetup();
        AudioTrack track = setup.createTrack();

        // Prime the buffer.
        int framesWrittenTotal = setup.primeAudioTrack(TEST_NAME);

        // Start playing and let it drain.
        int position1 = track.getPlaybackHeadPosition();
        assertEquals(TEST_NAME + ": initial position", 0, position1);
        track.play();

        // Make sure it starts within a reasonably short time.
        final long MAX_TIME_TO_START_MSEC =  500; // arbitrary
        long giveUpAt = System.currentTimeMillis() + MAX_TIME_TO_START_MSEC;
        int position2 = track.getPlaybackHeadPosition();
        while ((position1 == position2)
                && (System.currentTimeMillis() < giveUpAt)) {
            Thread.sleep(20); // arbitrary interval
            position2 = track.getPlaybackHeadPosition();
        }
        assertTrue(TEST_NAME + ": did it start?, position after start = " + position2,
                position2 > position1);

        // Make sure it finishes playing the data.
        // Wait several times longer than it should take to play the data.
        final int several = 3; // arbitrary
        Thread.sleep(several * framesWrittenTotal * MILLIS_PER_SECOND / setup.sampleRate);
        position2 = track.getPlaybackHeadPosition();
        assertEquals(TEST_NAME + ": did it play all the data?",
                framesWrittenTotal, position2);

        track.release();
    }

    // Try to play and pause an AudioTrack when the initial size is less than capacity.
    // We want to make sure the track starts properly and is not stuck.
    public void testPlayPauseSmallBuffer() throws Exception {
        final String TEST_NAME = "testPlayPauseSmallBuffer";
        TestSetup setup = new TestSetup();
        AudioTrack track = setup.createTrack();

        // Prime the buffer.
        setup.primeAudioTrack(TEST_NAME);

        // Start playing then pause and play in a loop.
        int position1 = track.getPlaybackHeadPosition();
        assertEquals(TEST_NAME + ": initial position", 0, position1);
        track.play();
        // try pausing several times to see it if it fails
        final int several = 4; // arbitrary
        for (int i = 0; i < several; i++) {
            // write data in non-blocking mode for a few seconds
            setup.writeSeconds(2.0); // arbitrary, long enough for audio to get to the device
            // Did position advance as we were playing? Or was the track stuck?
            int position2 = track.getPlaybackHeadPosition();
            int delta = position2 - position1; // safe from wrapping
            assertTrue(TEST_NAME + ": [" + i + "] did it advance? p1 = " + position1
                    + ", p2 = " + position2, delta > 0);
            position1 = position2;
            // pause for a second
            track.pause();
            Thread.sleep(MILLIS_PER_SECOND);
            track.play();
        }

        track.release();
    }

    // Create a track with or without FLAG_LOW_LATENCY
    private AudioTrack createCustomAudioTrack(boolean lowLatency) {
        final String TEST_NAME = "createCustomAudioTrack";
        final int TEST_SR = 48000;
        final int TEST_CONF = AudioFormat.CHANNEL_OUT_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_CONTENT_TYPE = AudioAttributes.CONTENT_TYPE_MUSIC;

        // Start with buffer twice as large as needed.
        int bufferSizeBytes = 2 * AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioAttributes.Builder attributesBuilder = new AudioAttributes.Builder()
                .setContentType(TEST_CONTENT_TYPE);
        if (lowLatency) {
            attributesBuilder.setFlags(AudioAttributes.FLAG_LOW_LATENCY);
        }
        AudioAttributes attributes = attributesBuilder.build();

        // Do not specify the sample rate so we get the optimal rate.
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(TEST_FORMAT)
                .setChannelMask(TEST_CONF)
                .build();
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSizeBytes)
                .build();

        assertTrue(track != null);
        log(TEST_NAME, "Track sample rate = " + track.getSampleRate() + " Hz");
        return track;
    }


    private int checkOutputLowLatency(boolean lowLatency) throws Exception {
        // constants for test
        final String TEST_NAME = "checkOutputLowLatency";
        final int TEST_SAMPLES_PER_FRAME = 2;
        final int TEST_BYTES_PER_SAMPLE = 2;
        final int TEST_NUM_SECONDS = 4;
        final int TEST_FRAMES_PER_BUFFER = 128;
        final double TEST_AMPLITUDE = 0.5;

        final short[] data = AudioHelper.createSineWavesShort(TEST_FRAMES_PER_BUFFER,
                TEST_SAMPLES_PER_FRAME, 1, TEST_AMPLITUDE);

        // -------- initialization --------------
        AudioTrack track = createCustomAudioTrack(lowLatency);
        assertTrue(TEST_NAME + " actual SR", track.getSampleRate() > 0);

        // -------- test --------------
        // Play some audio for a few seconds.
        int numSeconds = TEST_NUM_SECONDS;
        int numBuffers = numSeconds * track.getSampleRate() / TEST_FRAMES_PER_BUFFER;
        long framesWritten = 0;
        boolean isPlaying = false;
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
            framesWritten += TEST_FRAMES_PER_BUFFER;
            // prime the buffer a bit before playing
            if (!isPlaying) {
                track.play();
                isPlaying = true;
            }
        }

        // Estimate the latency from the timestamp.
        long timeWritten = System.nanoTime();
        AudioTimestamp timestamp = new AudioTimestamp();
        boolean result = track.getTimestamp(timestamp);
        // FIXME failing LOW_LATENCY case! b/26413951
        assertTrue(TEST_NAME + " did not get a timestamp, lowLatency = "
                + lowLatency, result);

        // Calculate when the last frame written is going to be rendered.
        long framesPending = framesWritten - timestamp.framePosition;
        long timeDelta = framesPending * NANOS_PER_SECOND / track.getSampleRate();
        long timePresented = timestamp.nanoTime + timeDelta;
        long latencyNanos = timePresented - timeWritten;
        int latencyMillis = (int) (latencyNanos / NANOS_PER_MILLISECOND);
        assertTrue(TEST_NAME + " got latencyMillis <= 0 == "
                + latencyMillis, latencyMillis > 0);

        // -------- cleanup --------------
        track.release();

        return latencyMillis;
    }

    // Compare output latency with and without FLAG_LOW_LATENCY.
    public void testOutputLowLatency() throws Exception {
        final String TEST_NAME = "testOutputLowLatency";

        int highLatencyMillis = checkOutputLowLatency(false);
        log(TEST_NAME, "High latency = " + highLatencyMillis + " msec");

        int lowLatencyMillis = checkOutputLowLatency(true);
        log(TEST_NAME, "Low latency = " + lowLatencyMillis + " msec");

        // We are not guaranteed to get a FAST track. Some platforms
        // do not even have a FastMixer. So just warn and not fail.
        if (highLatencyMillis <= (lowLatencyMillis + 10)) {
            logw(TEST_NAME, "high latency should be much higher, "
                    + highLatencyMillis
                    + " vs " + lowLatencyMillis);
        }
    }

    // Verify that no underruns when buffer is >= getMinBufferSize().
    // Verify that we get underruns with buffer at smallest possible size.
    public void testGetUnderrunCount() throws Exception {
        // constants for test
        final String TEST_NAME = "testGetUnderrunCount";
        final int TEST_SR = 44100;
        final int TEST_SAMPLES_PER_FRAME = 2;
        final int TEST_BYTES_PER_SAMPLE = 2;
        final int TEST_NUM_SECONDS = 2;
        final int TEST_CONF = AudioFormat.CHANNEL_OUT_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
        final int TEST_FRAMES_PER_BUFFER = 256;
        final int TEST_FRAMES_PER_BLIP = TEST_SR / 8;
        final int TEST_CYCLES_PER_BLIP = 700 * TEST_FRAMES_PER_BLIP / TEST_SR;
        final double TEST_AMPLITUDE = 0.5;

        final short[] data = AudioHelper.createSineWavesShort(TEST_FRAMES_PER_BUFFER,
                TEST_SAMPLES_PER_FRAME, 1, TEST_AMPLITUDE);
        final short[] blip = AudioHelper.createSineWavesShort(TEST_FRAMES_PER_BLIP,
                TEST_SAMPLES_PER_FRAME, TEST_CYCLES_PER_BLIP, TEST_AMPLITUDE);

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        // Start with buffer twice as large as needed.
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize * 2, TEST_MODE);

        // -------- test --------------
        // Initial values
        int bufferCapacity = track.getBufferCapacityInFrames();
        int initialBufferSize = track.getBufferSizeInFrames();
        int minBuffSizeInFrames = minBuffSize / (TEST_SAMPLES_PER_FRAME * TEST_BYTES_PER_SAMPLE);
        assertTrue(TEST_NAME, bufferCapacity > 0);
        assertTrue(TEST_NAME, initialBufferSize > 0);
        assertTrue(TEST_NAME, initialBufferSize <= bufferCapacity);

        // Play with initial size.
        int underrunCount1 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": initially no underruns", 0, underrunCount1);

        // Prime the buffer.
        while (track.write(data, 0, data.length) == data.length);

        // Start playing
        track.play();
        int numBuffers = TEST_SR / TEST_FRAMES_PER_BUFFER;
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        int underrunCountBase = track.getUnderrunCount();
        int numSeconds = TEST_NUM_SECONDS;
        numBuffers = numSeconds * TEST_SR / TEST_FRAMES_PER_BUFFER;
        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        underrunCount1 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": no more underruns after initial",
                underrunCountBase, underrunCount1);

        // Play with getMinBufferSize() size.
        int resultMin = track.setBufferSizeInFrames(minBuffSizeInFrames);
        assertTrue(TEST_NAME + ": set minBuff, >", resultMin > 0);
        assertTrue(TEST_NAME + ": set minBuff, <=", resultMin <= initialBufferSize);
        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        track.write(blip, 0, blip.length);
        underrunCount1 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": no more underruns at min", underrunCountBase, underrunCount1);

        // Play with ridiculously small size. We want to get underruns so we know that an app
        // can get to the edge of underrunning.
        int resultZero = track.setBufferSizeInFrames(0);
        assertTrue(TEST_NAME + ": should return > 0, got " + resultZero, resultZero > 0);
        assertTrue(TEST_NAME + ": zero size < original", resultZero < initialBufferSize);
        numSeconds = TEST_NUM_SECONDS / 2; // cuz test takes longer when underflowing
        numBuffers = numSeconds * TEST_SR / TEST_FRAMES_PER_BUFFER;
        // Play for a few seconds or until we get some new underruns.
        for (int i = 0; (i < numBuffers) && ((underrunCount1 - underrunCountBase) < 10); i++) {
            track.write(data, 0, data.length);
            underrunCount1 = track.getUnderrunCount();
        }
        assertTrue(TEST_NAME + ": underruns at zero", underrunCount1 > underrunCountBase);
        int underrunCount2 = underrunCount1;
        // Play for a few seconds or until we get some new underruns.
        for (int i = 0; (i < numBuffers) && ((underrunCount2 - underrunCount1) < 10); i++) {
            track.write(data, 0, data.length);
            underrunCount2 = track.getUnderrunCount();
        }
        assertTrue(TEST_NAME + ": underruns still accumulating", underrunCount2 > underrunCount1);

        // Restore buffer to good size
        numSeconds = TEST_NUM_SECONDS;
        numBuffers = numSeconds * TEST_SR / TEST_FRAMES_PER_BUFFER;
        int resultMax = track.setBufferSizeInFrames(bufferCapacity);
        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        // Should have stopped by now.
        underrunCount1 = track.getUnderrunCount();
        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        // Counts should match.
        underrunCount2 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": underruns should stop happening",
                underrunCount1, underrunCount2);

        // -------- tear down --------------
        track.release();
    }

    // Verify that we get underruns if we stop writing to the buffer.
    public void testGetUnderrunCountSleep() throws Exception {
        // constants for test
        final String TEST_NAME = "testGetUnderrunCountSleep";
        final int TEST_SR = 48000;
        final int TEST_SAMPLES_PER_FRAME = 2;
        final int TEST_BYTES_PER_SAMPLE = 2;
        final int TEST_NUM_SECONDS = 2;
        final int TEST_CONF = AudioFormat.CHANNEL_OUT_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
        final int TEST_FRAMES_PER_BUFFER = 256;
        final int TEST_FRAMES_PER_BLIP = TEST_SR / 8;
        final int TEST_CYCLES_PER_BLIP = 700 * TEST_FRAMES_PER_BLIP / TEST_SR;
        final double TEST_AMPLITUDE = 0.5;

        final short[] data = AudioHelper.createSineWavesShort(TEST_FRAMES_PER_BUFFER,
                TEST_SAMPLES_PER_FRAME, 1, TEST_AMPLITUDE);
        final short[] blip = AudioHelper.createSineWavesShort(TEST_FRAMES_PER_BLIP,
                TEST_SAMPLES_PER_FRAME, TEST_CYCLES_PER_BLIP, TEST_AMPLITUDE);

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        // Start with buffer twice as large as needed.
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize * 2, TEST_MODE);

        // -------- test --------------
        // Initial values
        int minBuffSizeInFrames = minBuffSize / (TEST_SAMPLES_PER_FRAME * TEST_BYTES_PER_SAMPLE);

        int underrunCount1 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": initially no underruns", 0, underrunCount1);

        // Prime the buffer.
        while (track.write(data, 0, data.length) == data.length);

        // Start playing
        track.play();
        int numBuffers = TEST_SR / TEST_FRAMES_PER_BUFFER;
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        int underrunCountBase = track.getUnderrunCount();
        int numSeconds = TEST_NUM_SECONDS;
        numBuffers = numSeconds * TEST_SR / TEST_FRAMES_PER_BUFFER;
        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        underrunCount1 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": no more underruns after initial",
                underrunCountBase, underrunCount1);

        // Sleep and force underruns.
        track.write(blip, 0, blip.length);
        for (int i = 0; i < 10; i++) {
            track.write(data, 0, data.length);
            Thread.sleep(500);  // ========================= SLEEP! ===========
        }
        track.write(blip, 0, blip.length);
        underrunCount1 = track.getUnderrunCount();
        assertTrue(TEST_NAME + ": expect underruns after sleep, #ur="
                + underrunCount1,
                underrunCountBase < underrunCount1);

        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }

        // Should have stopped by now.
        underrunCount1 = track.getUnderrunCount();
        track.write(blip, 0, blip.length);
        for (int i = 0; i < numBuffers; i++) {
            track.write(data, 0, data.length);
        }
        // Counts should match.
        int underrunCount2 = track.getUnderrunCount();
        assertEquals(TEST_NAME + ": underruns should stop happening",
                underrunCount1, underrunCount2);

        // -------- tear down --------------
        track.release();
    }

    static class TrackBufferSizeChecker {
        private final static String TEST_NAME = "testTrackBufferSize";
        private final static int TEST_SR = 48000;
        private final static int TEST_CONF = AudioFormat.CHANNEL_OUT_STEREO;
        private final static int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        private final static int TEST_MODE = AudioTrack.MODE_STREAM;
        private final static int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
        private final static int FRAME_SIZE = 2 * 2; // stereo 16-bit PCM

        public static int getFrameSize() {
            return FRAME_SIZE;
        }

        public static int getMinBufferSize() {
            return AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        }

        public static AudioTrack createAudioTrack(int bufferSize) {
            return new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                bufferSize, TEST_MODE);
        }

        public static void checkBadSize(int bufferSize) {
            AudioTrack track = null;
            try {
                track = TrackBufferSizeChecker.createAudioTrack(bufferSize);
                assertTrue(TEST_NAME + ": should not have survived size " + bufferSize, false);
            } catch(IllegalArgumentException e) {
                // expected
            } finally {
                if (track != null) {
                    track.release();
                }
            }
        }

        public static void checkSmallSize(int bufferSize) {
            AudioTrack track = null;
            try {
                track = TrackBufferSizeChecker.createAudioTrack(bufferSize);
                assertEquals(TEST_NAME + ": should still be initialized with small size " + bufferSize,
                            AudioTrack.STATE_INITIALIZED, track.getState());
            } finally {
                if (track != null) {
                    track.release();
                }
            }
        }
    }

    /**
     * Test various values for bufferSizeInBytes.
     *
     * According to the latest documentation, any positive bufferSize that is a multiple
     * of the frameSize is legal. Small sizes will be rounded up to the minimum size.
     *
     * Negative sizes, zero, or any non-multiple of the frameSize is illegal.
     *
     * @throws Exception
     */
    public void testTrackBufferSize() throws Exception {
        TrackBufferSizeChecker.checkBadSize(0);
        TrackBufferSizeChecker.checkBadSize(17);
        TrackBufferSizeChecker.checkBadSize(18);
        TrackBufferSizeChecker.checkBadSize(-9);
        int frameSize = TrackBufferSizeChecker.getFrameSize();
        TrackBufferSizeChecker.checkBadSize(-4 * frameSize);
        for (int i = 1; i < 8; i++) {
            TrackBufferSizeChecker.checkSmallSize(i * frameSize);
            TrackBufferSizeChecker.checkBadSize(3 + (i * frameSize));
        }
    }
}
