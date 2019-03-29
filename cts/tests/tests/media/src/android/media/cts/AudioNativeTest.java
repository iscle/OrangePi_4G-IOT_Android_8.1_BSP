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

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import com.android.compatibility.common.util.CtsAndroidTestCase;

public class AudioNativeTest extends CtsAndroidTestCase {
    public static final int MAX_CHANNEL_COUNT = 2;
    public static final int MAX_INDEX_MASK = (1 << MAX_CHANNEL_COUNT) - 1;

    private static final int CHANNEL_INDEX_MASK_MAGIC = 0x80000000;

    public void testAppendixBBufferQueue() {
        nativeAppendixBBufferQueue();
    }

    public void testAppendixBRecording() {
        // better to detect presence of microphone here.
        if (!hasMicrophone()) {
            return;
        }
        nativeAppendixBRecording();
    }

    public void testStereo16Playback() {
        assertTrue(AudioTrackNative.test(
                2 /* numChannels */, 48000 /* sampleRate */, false /* useFloat */,
                20 /* msecPerBuffer */, 8 /* numBuffers */));
    }

    public void testStereo16Record() {
        if (!hasMicrophone()) {
            return;
        }
        assertTrue(AudioRecordNative.test(
                2 /* numChannels */, 48000 /* sampleRate */, false /* useFloat */,
                20 /* msecPerBuffer */, 8 /* numBuffers */));
    }

    public void testPlayStreamData() throws Exception {
        final String TEST_NAME = "testPlayStreamData";
        final boolean TEST_FLOAT_ARRAY[] = {
                false,
                true,
        };
        // due to downmixer algorithmic latency, source channels greater than 2 may
        // sound shorter in duration at 4kHz sampling rate.
        final int TEST_SR_ARRAY[] = {
                /* 4000, */ // below limit of OpenSL ES
                12345, // irregular sampling rate
                44100,
                48000,
                96000,
                192000,
        };
        final int TEST_CHANNELS_ARRAY[] = {
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                // 8  // can fail due to memory issues
        };
        final float TEST_SWEEP = 0; // sine wave only
        final int TEST_TIME_IN_MSEC = 300;
        final int TOLERANCE_MSEC = 20;
        final boolean TEST_IS_LOW_RAM_DEVICE = isLowRamDevice();
        final long TEST_END_SLEEP_MSEC = TEST_IS_LOW_RAM_DEVICE ? 200 : 50;

        for (boolean TEST_FLOAT : TEST_FLOAT_ARRAY) {
            double frequency = 400; // frequency changes for each test
            for (int TEST_SR : TEST_SR_ARRAY) {
                for (int TEST_CHANNELS : TEST_CHANNELS_ARRAY) {
                    // OpenSL ES BUG: we run out of AudioTrack memory for this config on MNC
                    // Log.d(TEST_NAME, "open channels:" + TEST_CHANNELS + " sr:" + TEST_SR);
                    if (TEST_IS_LOW_RAM_DEVICE && (TEST_CHANNELS > 4 || TEST_SR > 96000)) {
                        continue;
                    }
                    if (TEST_FLOAT == true && TEST_CHANNELS >= 6 && TEST_SR >= 192000) {
                        continue;
                    }
                    AudioTrackNative track = new AudioTrackNative();
                    assertTrue(TEST_NAME,
                            track.open(TEST_CHANNELS, TEST_SR, TEST_FLOAT, 1 /* numBuffers */));
                    assertTrue(TEST_NAME, track.start());

                    final int sourceSamples =
                            (int)((long)TEST_SR * TEST_TIME_IN_MSEC * TEST_CHANNELS / 1000);
                    final double testFrequency = frequency / TEST_CHANNELS;
                    if (TEST_FLOAT) {
                        float data[] = AudioHelper.createSoundDataInFloatArray(
                                sourceSamples, TEST_SR,
                                testFrequency, TEST_SWEEP);
                        assertEquals(sourceSamples,
                                track.write(data, 0 /* offset */, sourceSamples,
                                        AudioTrackNative.WRITE_FLAG_BLOCKING));
                    } else {
                        short data[] = AudioHelper.createSoundDataInShortArray(
                                sourceSamples, TEST_SR,
                                testFrequency, TEST_SWEEP);
                        assertEquals(sourceSamples,
                                track.write(data, 0 /* offset */, sourceSamples,
                                        AudioTrackNative.WRITE_FLAG_BLOCKING));
                    }

                    while (true) {
                        // OpenSL ES BUG: getPositionInMsec returns 0 after a data underrun.

                        long position = track.getPositionInMsec();
                        //Log.d(TEST_NAME, "position: " + position[0]);
                        if (position >= (long)(TEST_TIME_IN_MSEC - TOLERANCE_MSEC)) {
                            break;
                        }

                        // It is safer to use a buffer count of 0 to determine termination
                        if (track.getBuffersPending() == 0) {
                            break;
                        }
                        Thread.sleep(5 /* millis */);
                    }
                    track.stop();
                    Thread.sleep(TEST_END_SLEEP_MSEC);
                    track.close();
                    Thread.sleep(TEST_END_SLEEP_MSEC); // put a gap in the tone sequence
                    frequency += 50; // increment test tone frequency
                }
            }
        }
    }

    private boolean isLowRamDevice() {
        return ((ActivityManager)getContext().getSystemService(Context.ACTIVITY_SERVICE)
                ).isLowRamDevice();
    }

    public void testRecordStreamData() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        final String TEST_NAME = "testRecordStreamData";
        final boolean TEST_FLOAT_ARRAY[] = {
                false,
                true,
        };
        final int TEST_SR_ARRAY[] = {
                //4000, // below limit of OpenSL ES
                12345, // irregular sampling rate
                44100,
                48000,
                96000,
                192000,
        };
        final int TEST_CHANNELS_ARRAY[] = {
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                8,
        };
        final int SEGMENT_DURATION_IN_MSEC = 20;
        final int NUMBER_SEGMENTS = 10;

        for (boolean TEST_FLOAT : TEST_FLOAT_ARRAY) {
            for (int TEST_SR : TEST_SR_ARRAY) {
                for (int TEST_CHANNELS : TEST_CHANNELS_ARRAY) {
                    // OpenSL ES BUG: we run out of AudioTrack memory for this config on MNC
                    if (TEST_FLOAT == true && TEST_CHANNELS >= 8 && TEST_SR >= 192000) {
                        continue;
                    }
                    AudioRecordNative record = new AudioRecordNative();
                    doRecordTest(record, TEST_CHANNELS, TEST_SR, TEST_FLOAT,
                            SEGMENT_DURATION_IN_MSEC, NUMBER_SEGMENTS);
                }
            }
        }
    }

    public void testRecordAudit() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        AudioRecordNative record = new AudioHelper.AudioRecordAuditNative();
        doRecordTest(record, 4 /* numChannels */, 44100 /* sampleRate */, false /* useFloat */,
                1000 /* segmentDurationMs */, 10 /* numSegments */);
    }

    public void testOutputChannelMasks() {
        if (!hasAudioOutput()) {
            return;
        }
        AudioTrackNative track = new AudioTrackNative();

        int maxOutputChannels = 2;
        int validIndexMask = (1 << maxOutputChannels) - 1;

        for (int mask = 0; mask <= MAX_INDEX_MASK; ++mask) {
            int channelCount = Long.bitCount(mask);
            boolean expectSuccess = (channelCount > 0)
                && ((mask & validIndexMask) != 0);

            // TODO: uncomment this line when b/27484181 is fixed.
            // expectSuccess &&= ((mask & ~validIndexMask) == 0);

            boolean ok = track.open(channelCount,
                mask | CHANNEL_INDEX_MASK_MAGIC, 48000, false, 2);
            track.close();
            assertEquals(expectSuccess, ok);
        }
    }

    public void testInputChannelMasks() {
        if (!hasMicrophone()) {
            return;
        }

        AudioManager audioManager =
                (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        AudioRecordNative recorder = new AudioRecordNative();

        int maxInputChannels = 0;
        for (AudioDeviceInfo deviceInfo :
                audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            for (int channels : deviceInfo.getChannelCounts()) {
                maxInputChannels = Math.max(channels, maxInputChannels);
            }
        }

        int validIndexMask = (1 << maxInputChannels) - 1;

        for (int mask = 0; mask <= MAX_INDEX_MASK; ++mask) {
            int channelCount = Long.bitCount(mask);
            boolean expectSuccess = (channelCount > 0)
                && ((mask & validIndexMask) != 0);

            // TODO: uncomment this line when b/27484181 is fixed.
            // expectSuccess &&= ((mask & ~validIndexMask) == 0);

            boolean ok = recorder.open(channelCount,
                mask | CHANNEL_INDEX_MASK_MAGIC, 48000, false, 2);
            recorder.close();
            assertEquals(expectSuccess, ok);
        }
    }

    static {
        System.loadLibrary("audio_jni");
    }

    private static final String TAG = "AudioNativeTest";

    private void doRecordTest(AudioRecordNative record,
            int numChannels, int sampleRate, boolean useFloat,
            int segmentDurationMs, int numSegments) {
        final String TEST_NAME = "doRecordTest";
        try {
            // Log.d(TEST_NAME, "open numChannels:" + numChannels + " sampleRate:" + sampleRate);
            assertTrue(TEST_NAME, record.open(numChannels, sampleRate, useFloat,
                    numSegments /* numBuffers */));
            assertTrue(TEST_NAME, record.start());

            final int sourceSamples =
                    (int)((long)sampleRate * segmentDurationMs * numChannels / 1000);

            if (useFloat) {
                float data[] = new float[sourceSamples];
                for (int i = 0; i < numSegments; ++i) {
                    assertEquals(sourceSamples,
                            record.read(data, 0 /* offset */, sourceSamples,
                                    AudioRecordNative.READ_FLAG_BLOCKING));
                }
            } else {
                short data[] = new short[sourceSamples];
                for (int i = 0; i < numSegments; ++i) {
                    assertEquals(sourceSamples,
                            record.read(data, 0 /* offset */, sourceSamples,
                                    AudioRecordNative.READ_FLAG_BLOCKING));
                }
            }
            assertTrue(TEST_NAME, record.stop());
        } finally {
            record.close();
        }
    }

    private boolean hasMicrophone() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }

    private boolean hasAudioOutput() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUDIO_OUTPUT);
    }

    private static native void nativeAppendixBBufferQueue();
    private static native void nativeAppendixBRecording();
}
