/*
 * Copyright (C) 2009 The Android Open Source Project
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
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class AudioTrackTest extends CtsAndroidTestCase {
    private String TAG = "AudioTrackTest";
    private final long WAIT_MSEC = 200;
    private final int OFFSET_DEFAULT = 0;
    private final int OFFSET_NEGATIVE = -10;
    private static final String REPORT_LOG_NAME = "CtsMediaTestCases";

    private void log(String testName, String message) {
        Log.v(TAG, "[" + testName + "] " + message);
    }

    private void loge(String testName, String message) {
        Log.e(TAG, "[" + testName + "] " + message);
    }

    // -----------------------------------------------------------------
    // private class to hold test results
    private static class TestResults {
        public boolean mResult = false;
        public String mResultLog = "";

        public TestResults(boolean b, String s) {
            mResult = b;
            mResultLog = s;
        }
    }

    // -----------------------------------------------------------------
    // generic test methods
    public TestResults constructorTestMultiSampleRate(
    // parameters tested by this method
            int _inTest_streamType, int _inTest_mode, int _inTest_config, int _inTest_format,
            // parameter-dependent expected results
            int _expected_stateForMode) {

        int[] testSampleRates = { 8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000 };
        String failedRates = "Failure for rate(s): ";
        boolean localRes, finalRes = true;

        for (int i = 0; i < testSampleRates.length; i++) {
            AudioTrack track = null;
            try {
                track = new AudioTrack(_inTest_streamType, testSampleRates[i], _inTest_config,
                        _inTest_format, AudioTrack.getMinBufferSize(testSampleRates[i],
                                _inTest_config, _inTest_format), _inTest_mode);
            } catch (IllegalArgumentException iae) {
                Log.e("MediaAudioTrackTest", "[ constructorTestMultiSampleRate ] exception at SR "
                        + testSampleRates[i] + ": \n" + iae);
                localRes = false;
            }
            if (track != null) {
                localRes = (track.getState() == _expected_stateForMode);
                track.release();
            } else {
                localRes = false;
            }

            if (!localRes) {
                // log the error for the test runner
                failedRates += Integer.toString(testSampleRates[i]) + "Hz ";
                // log the error for logcat
                log("constructorTestMultiSampleRate", "failed to construct "
                        + "AudioTrack(streamType="
                        + _inTest_streamType
                        + ", sampleRateInHz="
                        + testSampleRates[i]
                        + ", channelConfig="
                        + _inTest_config
                        + ", audioFormat="
                        + _inTest_format
                        + ", bufferSizeInBytes="
                        + AudioTrack.getMinBufferSize(testSampleRates[i], _inTest_config,
                                AudioFormat.ENCODING_PCM_16BIT) + ", mode=" + _inTest_mode);
                // mark test as failed
                finalRes = false;
            }
        }
        return new TestResults(finalRes, failedRates);
    }

    // -----------------------------------------------------------------
    // AUDIOTRACK TESTS:
    // ----------------------------------

    // -----------------------------------------------------------------
    // AudioTrack constructor and AudioTrack.getMinBufferSize(...) for 16bit PCM
    // ----------------------------------

    // Test case 1: constructor for streaming AudioTrack, mono, 16bit at misc
    // valid sample rates
    public void testConstructorMono16MusicStream() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STREAM, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, AudioTrack.STATE_INITIALIZED);

        assertTrue("testConstructorMono16MusicStream: " + res.mResultLog, res.mResult);
    }

    // Test case 2: constructor for streaming AudioTrack, stereo, 16bit at misc
    // valid sample rates
    public void testConstructorStereo16MusicStream() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STREAM, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_16BIT, AudioTrack.STATE_INITIALIZED);

        assertTrue("testConstructorStereo16MusicStream: " + res.mResultLog, res.mResult);
    }

    // Test case 3: constructor for static AudioTrack, mono, 16bit at misc valid
    // sample rates
    public void testConstructorMono16MusicStatic() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STATIC, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, AudioTrack.STATE_NO_STATIC_DATA);

        assertTrue("testConstructorMono16MusicStatic: " + res.mResultLog, res.mResult);
    }

    // Test case 4: constructor for static AudioTrack, stereo, 16bit at misc
    // valid sample rates
    public void testConstructorStereo16MusicStatic() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STATIC, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_16BIT, AudioTrack.STATE_NO_STATIC_DATA);

        assertTrue("testConstructorStereo16MusicStatic: " + res.mResultLog, res.mResult);
    }

    // -----------------------------------------------------------------
    // AudioTrack constructor and AudioTrack.getMinBufferSize(...) for 8bit PCM
    // ----------------------------------

    // Test case 1: constructor for streaming AudioTrack, mono, 8bit at misc
    // valid sample rates
    public void testConstructorMono8MusicStream() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STREAM, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_8BIT, AudioTrack.STATE_INITIALIZED);

        assertTrue("testConstructorMono8MusicStream: " + res.mResultLog, res.mResult);
    }

    // Test case 2: constructor for streaming AudioTrack, stereo, 8bit at misc
    // valid sample rates
    public void testConstructorStereo8MusicStream() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STREAM, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_8BIT, AudioTrack.STATE_INITIALIZED);

        assertTrue("testConstructorStereo8MusicStream: " + res.mResultLog, res.mResult);
    }

    // Test case 3: constructor for static AudioTrack, mono, 8bit at misc valid
    // sample rates
    public void testConstructorMono8MusicStatic() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STATIC, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_8BIT, AudioTrack.STATE_NO_STATIC_DATA);

        assertTrue("testConstructorMono8MusicStatic: " + res.mResultLog, res.mResult);
    }

    // Test case 4: constructor for static AudioTrack, stereo, 8bit at misc
    // valid sample rates
    public void testConstructorStereo8MusicStatic() throws Exception {

        TestResults res = constructorTestMultiSampleRate(AudioManager.STREAM_MUSIC,
                AudioTrack.MODE_STATIC, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_8BIT, AudioTrack.STATE_NO_STATIC_DATA);

        assertTrue("testConstructorStereo8MusicStatic: " + res.mResultLog, res.mResult);
    }

    // -----------------------------------------------------------------
    // AudioTrack constructor for all stream types
    // ----------------------------------

    // Test case 1: constructor for all stream types
    public void testConstructorStreamType() throws Exception {
        // constants for test
        final int TYPE_TEST_SR = 22050;
        final int TYPE_TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TYPE_TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TYPE_TEST_MODE = AudioTrack.MODE_STREAM;
        final int[] STREAM_TYPES = { AudioManager.STREAM_ALARM, AudioManager.STREAM_MUSIC,
                AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_RING,
                AudioManager.STREAM_SYSTEM, AudioManager.STREAM_VOICE_CALL };
        final String[] STREAM_NAMES = { "STREAM_ALARM", "STREAM_MUSIC", "STREAM_NOTIFICATION",
                "STREAM_RING", "STREAM_SYSTEM", "STREAM_VOICE_CALL" };

        boolean localTestRes = true;
        AudioTrack track = null;
        // test: loop constructor on all stream types
        for (int i = 0; i < STREAM_TYPES.length; i++) {
            try {
                // -------- initialization --------------
                track = new AudioTrack(STREAM_TYPES[i], TYPE_TEST_SR, TYPE_TEST_CONF,
                        TYPE_TEST_FORMAT, AudioTrack.getMinBufferSize(TYPE_TEST_SR, TYPE_TEST_CONF,
                                TYPE_TEST_FORMAT), TYPE_TEST_MODE);
            } catch (IllegalArgumentException iae) {
                loge("testConstructorStreamType", "exception for stream type " + STREAM_NAMES[i]
                        + ": " + iae);
                localTestRes = false;
            }
            // -------- test --------------
            if (track != null) {
                if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                    localTestRes = false;
                    Log.e("MediaAudioTrackTest",
                            "[ testConstructorStreamType ] failed for stream type "
                                    + STREAM_NAMES[i]);
                }
                // -------- tear down --------------
                track.release();
            } else {
                localTestRes = false;
            }
        }

        assertTrue("testConstructorStreamType", localTestRes);
    }

    // -----------------------------------------------------------------
    // AudioTrack construction with Builder
    // ----------------------------------

    // Test case 1: build AudioTrack with default parameters, test documented default params
    public void testBuilderDefault() throws Exception {
        // constants for test
        final String TEST_NAME = "testBuilderDefault";
        final int expectedDefaultEncoding = AudioFormat.ENCODING_PCM_16BIT;
        final int expectedDefaultRate =
                AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        final int expectedDefaultChannels = AudioFormat.CHANNEL_OUT_STEREO;
        // use Builder
        final int buffSizeInBytes = AudioTrack.getMinBufferSize(
                expectedDefaultRate, expectedDefaultChannels, expectedDefaultEncoding);
        final AudioTrack track = new AudioTrack.Builder()
                .setBufferSizeInBytes(buffSizeInBytes)
                .build();
        // save results
        final int observedState = track.getState();
        final int observedFormat = track.getAudioFormat();
        final int observedChannelConf = track.getChannelConfiguration();
        final int observedRate = track.getSampleRate();
        // release track before the test exits (either successfully or with an exception)
        track.release();
        // compare results
        assertEquals(TEST_NAME + ": Track initialized", AudioTrack.STATE_INITIALIZED,
                observedState);
        assertEquals(TEST_NAME + ": Default track encoding", expectedDefaultEncoding,
                observedFormat);
        assertEquals(TEST_NAME + ": Default track channels", expectedDefaultChannels,
                observedChannelConf);
    }

    // Test case 2: build AudioTrack with AudioFormat, test it's used
    public void testBuilderFormat() throws Exception {
        // constants for test
        final String TEST_NAME = "testBuilderFormat";
        final int TEST_RATE = 32000;
        final int TEST_CHANNELS = AudioFormat.CHANNEL_OUT_STEREO;
        // use Builder
        final int buffSizeInBytes = AudioTrack.getMinBufferSize(
                TEST_RATE, TEST_CHANNELS, AudioFormat.ENCODING_PCM_16BIT);
        final AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().build())
                .setBufferSizeInBytes(buffSizeInBytes)
                .setAudioFormat(new AudioFormat.Builder()
                        .setChannelMask(TEST_CHANNELS).setSampleRate(TEST_RATE).build())
                .build();
        // save results
        final int observedState = track.getState();
        final int observedChannelConf = track.getChannelConfiguration();
        final int observedRate = track.getSampleRate();
        // release track before the test exits (either successfully or with an exception)
        track.release();
        // compare results
        assertEquals(TEST_NAME + ": Track initialized", AudioTrack.STATE_INITIALIZED,
                observedState);
        assertEquals(TEST_NAME + ": Track channels", TEST_CHANNELS, observedChannelConf);
        assertEquals(TEST_NAME + ": Track sample rate", TEST_RATE, observedRate);
    }

    // Test case 3: build AudioTrack with session ID, test it's used
    public void testBuilderSession() throws Exception {
        // constants for test
        final String TEST_NAME = "testBuilderSession";
        // generate a session ID
        final int expectedSessionId = new AudioManager(getContext()).generateAudioSessionId();
        // use builder
        final AudioTrack track = new AudioTrack.Builder()
                .setSessionId(expectedSessionId)
                .build();
        // save results
        final int observedSessionId = track.getAudioSessionId();
        // release track before the test exits (either successfully or with an exception)
        track.release();
        // compare results
        assertEquals(TEST_NAME + ": Assigned track session ID", expectedSessionId,
                observedSessionId);
    }

    // Test case 4: build AudioTrack with AudioAttributes built from stream type, test it's used
    public void testBuilderAttributesStream() throws Exception {
        // constants for test
        final String TEST_NAME = "testBuilderAttributesStream";
        //     use a stream type documented in AudioAttributes.Builder.setLegacyStreamType(int)
        final int expectedStreamType = AudioManager.STREAM_ALARM;
        final int expectedContentType = AudioAttributes.CONTENT_TYPE_SPEECH;
        final AudioAttributes aa = new AudioAttributes.Builder()
                .setLegacyStreamType(expectedStreamType)
                .setContentType(expectedContentType)
                .build();
        // use builder
        final AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(aa)
                .build();
        // save results
        final int observedStreamType = track.getStreamType();
        // release track before the test exits (either successfully or with an exception)
        track.release();
        // compare results
        assertEquals(TEST_NAME + ": track stream type", expectedStreamType, observedStreamType);
        //    also test content type was preserved in the attributes even though they
        //     were first configured with a legacy stream type
        assertEquals(TEST_NAME + ": attributes content type", expectedContentType,
                aa.getContentType());
    }

    // Test case 5: build AudioTrack with attributes and performance mode
    public void testBuilderAttributesPerformanceMode() throws Exception {
        // constants for test
        final String TEST_NAME = "testBuilderAttributesPerformanceMode";
        final int testPerformanceModes[] = new int[] {
            AudioTrack.PERFORMANCE_MODE_NONE,
            AudioTrack.PERFORMANCE_MODE_LOW_LATENCY,
            AudioTrack.PERFORMANCE_MODE_POWER_SAVING,
        };
        // construct various attributes with different preset performance modes.
        final AudioAttributes testAttributes[] = new AudioAttributes[] {
            new AudioAttributes.Builder().build(),
            new AudioAttributes.Builder().setFlags(AudioAttributes.FLAG_LOW_LATENCY).build(),
            new AudioAttributes.Builder().setFlags(AudioAttributes.FLAG_DEEP_BUFFER).build(),
        };
        for (int performanceMode : testPerformanceModes) {
            for (AudioAttributes attributes : testAttributes) {
                final AudioTrack track = new AudioTrack.Builder()
                    .setPerformanceMode(performanceMode)
                    .setAudioAttributes(attributes)
                    .build();
                // save results
                final int actualPerformanceMode = track.getPerformanceMode();
                // release track before the test exits
                track.release();
                final String result = "Attribute flags: " + attributes.getAllFlags()
                        + " set performance mode: " + performanceMode
                        + " actual performance mode: " + actualPerformanceMode;
                Log.d(TEST_NAME, result);
                assertTrue(TEST_NAME + ": " + result,
                        actualPerformanceMode == performanceMode  // either successful
                        || actualPerformanceMode == AudioTrack.PERFORMANCE_MODE_NONE // or none
                        || performanceMode == AudioTrack.PERFORMANCE_MODE_NONE);
            }
        }
    }

    // -----------------------------------------------------------------
    // Playback head position
    // ----------------------------------

    // Test case 1: getPlaybackHeadPosition() at 0 after initialization
    public void testPlaybackHeadPositionAfterInit() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlaybackHeadPositionAfterInit";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT), TEST_MODE);
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.getPlaybackHeadPosition() == 0);
        // -------- tear down --------------
        track.release();
    }

    // Test case 2: getPlaybackHeadPosition() increases after play()
    public void testPlaybackHeadPositionIncrease() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlaybackHeadPositionIncrease";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        Thread.sleep(100);
        log(TEST_NAME, "position =" + track.getPlaybackHeadPosition());
        assertTrue(TEST_NAME, track.getPlaybackHeadPosition() > 0);
        // -------- tear down --------------
        track.release();
    }

    // Test case 3: getPlaybackHeadPosition() is 0 after flush();
    public void testPlaybackHeadPositionAfterFlush() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlaybackHeadPositionAfterFlush";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        Thread.sleep(WAIT_MSEC);
        track.stop();
        track.flush();
        log(TEST_NAME, "position =" + track.getPlaybackHeadPosition());
        assertTrue(TEST_NAME, track.getPlaybackHeadPosition() == 0);
        // -------- tear down --------------
        track.release();
    }

    // Test case 3: getPlaybackHeadPosition() is 0 after stop();
    public void testPlaybackHeadPositionAfterStop() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlaybackHeadPositionAfterStop";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
        final int TEST_LOOP_CNT = 10;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        Thread.sleep(WAIT_MSEC);
        track.stop();
        int count = 0;
        int pos;
        do {
            Thread.sleep(WAIT_MSEC);
            pos = track.getPlaybackHeadPosition();
            count++;
        } while((pos != 0) && (count < TEST_LOOP_CNT));
        log(TEST_NAME, "position =" + pos + ", read count ="+count);
        assertTrue(TEST_NAME, pos == 0);
        // -------- tear down --------------
        track.release();
    }

    // Test case 4: getPlaybackHeadPosition() is > 0 after play(); pause();
    public void testPlaybackHeadPositionAfterPause() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlaybackHeadPositionAfterPause";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        Thread.sleep(100);
        track.pause();
        int pos = track.getPlaybackHeadPosition();
        log(TEST_NAME, "position =" + pos);
        assertTrue(TEST_NAME, pos > 0);
        // -------- tear down --------------
        track.release();
    }

    // Test case 5: getPlaybackHeadPosition() remains 0 after pause(); flush(); play();
    public void testPlaybackHeadPositionAfterFlushAndPlay() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlaybackHeadPositionAfterFlushAndPlay";
        final int TEST_CONF = AudioFormat.CHANNEL_OUT_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
        final int TEST_SR = AudioTrack.getNativeOutputSampleRate(TEST_STREAM_TYPE);

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        Thread.sleep(100);
        track.pause();

        int pos = track.getPlaybackHeadPosition();
        log(TEST_NAME, "position after pause =" + pos);
        assertTrue(TEST_NAME, pos > 0);

        track.flush();
        pos = track.getPlaybackHeadPosition();
        log(TEST_NAME, "position after flush =" + pos);
        assertTrue(TEST_NAME, pos == 0);

        track.play();
        pos = track.getPlaybackHeadPosition();
        log(TEST_NAME, "position after play =" + pos);
        assertTrue(TEST_NAME, pos == 0);

        Thread.sleep(100);
        pos = track.getPlaybackHeadPosition();
        log(TEST_NAME, "position after 100 ms sleep =" + pos);
        assertTrue(TEST_NAME, pos == 0);
        // -------- tear down --------------
        track.release();
    }

    // -----------------------------------------------------------------
    // Playback properties
    // ----------------------------------

    // Common code for the testSetStereoVolume* and testSetVolume* tests
    private void testSetVolumeCommon(String testName, float vol, boolean isStereo) throws Exception {
        // constants for test
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        if (isStereo) {
            // TODO to really test this, do a pan instead of using same value for left and right
            assertTrue(testName, track.setStereoVolume(vol, vol) == AudioTrack.SUCCESS);
        } else {
            assertTrue(testName, track.setVolume(vol) == AudioTrack.SUCCESS);
        }
        // -------- tear down --------------
        track.release();
    }

    // Test case 1: setStereoVolume() with max volume returns SUCCESS
    public void testSetStereoVolumeMax() throws Exception {
        final String TEST_NAME = "testSetStereoVolumeMax";
        float maxVol = AudioTrack.getMaxVolume();
        testSetVolumeCommon(TEST_NAME, maxVol, true /*isStereo*/);
    }

    // Test case 2: setStereoVolume() with min volume returns SUCCESS
    public void testSetStereoVolumeMin() throws Exception {
        final String TEST_NAME = "testSetStereoVolumeMin";
        float minVol = AudioTrack.getMinVolume();
        testSetVolumeCommon(TEST_NAME, minVol, true /*isStereo*/);
    }

    // Test case 3: setStereoVolume() with mid volume returns SUCCESS
    public void testSetStereoVolumeMid() throws Exception {
        final String TEST_NAME = "testSetStereoVolumeMid";
        float midVol = (AudioTrack.getMaxVolume() - AudioTrack.getMinVolume()) / 2;
        testSetVolumeCommon(TEST_NAME, midVol, true /*isStereo*/);
    }

    // Test case 4: setPlaybackRate() with half the content rate returns SUCCESS
    public void testSetPlaybackRate() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackRate";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.play();
        assertTrue(TEST_NAME, track.setPlaybackRate((int) (TEST_SR / 2)) == AudioTrack.SUCCESS);
        // -------- tear down --------------
        track.release();
    }

    // Test case 5: setPlaybackRate(0) returns bad value error
    public void testSetPlaybackRateZero() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackRateZero";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.setPlaybackRate(0) == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 6: setPlaybackRate() accepts values twice the output sample
    // rate
    public void testSetPlaybackRateTwiceOutputSR() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackRateTwiceOutputSR";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        int outputSR = AudioTrack.getNativeOutputSampleRate(TEST_STREAM_TYPE);
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.play();
        assertTrue(TEST_NAME, track.setPlaybackRate(2 * outputSR) == AudioTrack.SUCCESS);
        // -------- tear down --------------
        track.release();
    }

    // Test case 7: setPlaybackRate() and retrieve value, should be the same for
    // half the content SR
    public void testSetGetPlaybackRate() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetGetPlaybackRate";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.play();
        track.setPlaybackRate((int) (TEST_SR / 2));
        assertTrue(TEST_NAME, track.getPlaybackRate() == (int) (TEST_SR / 2));
        // -------- tear down --------------
        track.release();
    }

    // Test case 8: setPlaybackRate() invalid operation if track not initialized
    public void testSetPlaybackRateUninit() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackRateUninit";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        // -------- test --------------
        assertEquals(TEST_NAME, AudioTrack.STATE_NO_STATIC_DATA, track.getState());
        assertEquals(TEST_NAME, AudioTrack.ERROR_INVALID_OPERATION,
                track.setPlaybackRate(TEST_SR / 2));
        // -------- tear down --------------
        track.release();
    }

    // Test case 9: setVolume() with max volume returns SUCCESS
    public void testSetVolumeMax() throws Exception {
        final String TEST_NAME = "testSetVolumeMax";
        float maxVol = AudioTrack.getMaxVolume();
        testSetVolumeCommon(TEST_NAME, maxVol, false /*isStereo*/);
    }

    // Test case 10: setVolume() with min volume returns SUCCESS
    public void testSetVolumeMin() throws Exception {
        final String TEST_NAME = "testSetVolumeMin";
        float minVol = AudioTrack.getMinVolume();
        testSetVolumeCommon(TEST_NAME, minVol, false /*isStereo*/);
    }

    // Test case 11: setVolume() with mid volume returns SUCCESS
    public void testSetVolumeMid() throws Exception {
        final String TEST_NAME = "testSetVolumeMid";
        float midVol = (AudioTrack.getMaxVolume() - AudioTrack.getMinVolume()) / 2;
        testSetVolumeCommon(TEST_NAME, midVol, false /*isStereo*/);
    }

    // -----------------------------------------------------------------
    // Playback progress
    // ----------------------------------

    // Test case 1: setPlaybackHeadPosition() on playing track
    public void testSetPlaybackHeadPositionPlaying() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackHeadPositionPlaying";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        track.play();
        assertTrue(TEST_NAME,
                track.setPlaybackHeadPosition(10) == AudioTrack.ERROR_INVALID_OPERATION);
        // -------- tear down --------------
        track.release();
    }

    // Test case 2: setPlaybackHeadPosition() on stopped track
    public void testSetPlaybackHeadPositionStopped() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackHeadPositionStopped";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertEquals(TEST_NAME, AudioTrack.STATE_NO_STATIC_DATA, track.getState());
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertEquals(TEST_NAME, AudioTrack.STATE_INITIALIZED, track.getState());
        track.play();
        track.stop();
        assertEquals(TEST_NAME, AudioTrack.PLAYSTATE_STOPPED, track.getPlayState());
        assertEquals(TEST_NAME, AudioTrack.SUCCESS, track.setPlaybackHeadPosition(10));
        // -------- tear down --------------
        track.release();
    }

    // Test case 3: setPlaybackHeadPosition() on paused track
    public void testSetPlaybackHeadPositionPaused() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackHeadPositionPaused";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertEquals(TEST_NAME, AudioTrack.STATE_NO_STATIC_DATA, track.getState());
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertEquals(TEST_NAME, AudioTrack.STATE_INITIALIZED, track.getState());
        track.play();
        track.pause();
        assertEquals(TEST_NAME, AudioTrack.PLAYSTATE_PAUSED, track.getPlayState());
        assertEquals(TEST_NAME, AudioTrack.SUCCESS, track.setPlaybackHeadPosition(10));
        // -------- tear down --------------
        track.release();
    }

    // Test case 4: setPlaybackHeadPosition() beyond what has been written
    public void testSetPlaybackHeadPositionTooFar() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetPlaybackHeadPositionTooFar";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // make up a frame index that's beyond what has been written: go from
        // buffer size to frame
        // count (given the audio track properties), and add 77.
        int frameIndexTooFar = (2 * minBuffSize / 2) + 77;
        // -------- test --------------
        assertEquals(TEST_NAME, AudioTrack.STATE_NO_STATIC_DATA, track.getState());
        track.write(data, OFFSET_DEFAULT, data.length);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertEquals(TEST_NAME, AudioTrack.STATE_INITIALIZED, track.getState());
        track.play();
        track.stop();
        assertEquals(TEST_NAME, AudioTrack.PLAYSTATE_STOPPED, track.getPlayState());
        assertEquals(TEST_NAME, AudioTrack.ERROR_BAD_VALUE,
                track.setPlaybackHeadPosition(frameIndexTooFar));
        // -------- tear down --------------
        track.release();
    }

    // Test case 5: setLoopPoints() fails for MODE_STREAM
    public void testSetLoopPointsStream() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetLoopPointsStream";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.setLoopPoints(2, 50, 2) == AudioTrack.ERROR_INVALID_OPERATION);
        // -------- tear down --------------
        track.release();
    }

    // Test case 6: setLoopPoints() fails start > end
    public void testSetLoopPointsStartAfterEnd() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetLoopPointsStartAfterEnd";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.setLoopPoints(50, 0, 2) == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 6: setLoopPoints() success
    public void testSetLoopPointsSuccess() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetLoopPointsSuccess";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.setLoopPoints(0, 50, 2) == AudioTrack.SUCCESS);
        // -------- tear down --------------
        track.release();
    }

    // Test case 7: setLoopPoints() fails with loop length bigger than content
    public void testSetLoopPointsLoopTooLong() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetLoopPointsLoopTooLong";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        int dataSizeInFrames = minBuffSize / 2;
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_NO_STATIC_DATA);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.setLoopPoints(10, dataSizeInFrames + 20, 2) ==
            AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 8: setLoopPoints() fails with start beyond what can be written
    // for the track
    public void testSetLoopPointsStartTooFar() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetLoopPointsStartTooFar";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        int dataSizeInFrames = minBuffSize / 2;// 16bit data
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_NO_STATIC_DATA);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME,
                track.setLoopPoints(dataSizeInFrames + 20, dataSizeInFrames + 50, 2) ==
                    AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 9: setLoopPoints() fails with end beyond what can be written
    // for the track
    public void testSetLoopPointsEndTooFar() throws Exception {
        // constants for test
        final String TEST_NAME = "testSetLoopPointsEndTooFar";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        int dataSizeInFrames = minBuffSize / 2;// 16bit data
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_NO_STATIC_DATA);
        track.write(data, OFFSET_DEFAULT, data.length);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        int loopCount = 2;
        assertTrue(TEST_NAME,
                track.setLoopPoints(dataSizeInFrames - 10, dataSizeInFrames + 50, loopCount) ==
                    AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // -----------------------------------------------------------------
    // Audio data supply
    // ----------------------------------

    // Test case 1: write() fails when supplying less data (bytes) than declared
    public void testWriteByteOffsetTooBig() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteByteOffsetTooBig";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        int offset = 10;
        assertTrue(TEST_NAME, track.write(data, offset, data.length) == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 2: write() fails when supplying less data (shorts) than
    // declared
    public void testWriteShortOffsetTooBig() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteShortOffsetTooBig";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        short data[] = new short[minBuffSize / 2];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        int offset = 10;
        assertTrue(TEST_NAME, track.write(data, offset, data.length)
                                                            == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 3: write() fails when supplying less data (bytes) than declared
    public void testWriteByteSizeTooBig() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteByteSizeTooBig";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.write(data, OFFSET_DEFAULT, data.length + 10)
                                                        == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 4: write() fails when supplying less data (shorts) than
    // declared
    public void testWriteShortSizeTooBig() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteShortSizeTooBig";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        short data[] = new short[minBuffSize / 2];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.write(data, OFFSET_DEFAULT, data.length + 10)
                                                        == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 5: write() fails with negative offset
    public void testWriteByteNegativeOffset() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteByteNegativeOffset";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.write(data, OFFSET_NEGATIVE, data.length - 10) ==
            AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 6: write() fails with negative offset
    public void testWriteShortNegativeOffset() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteShortNegativeOffset";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        short data[] = new short[minBuffSize / 2];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME,
        track.write(data, OFFSET_NEGATIVE, data.length - 10) == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 7: write() fails with negative size
    public void testWriteByteNegativeSize() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteByteNegativeSize";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        int dataLength = -10;
        assertTrue(TEST_NAME, track.write(data, OFFSET_DEFAULT, dataLength)
                                                    == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 8: write() fails with negative size
    public void testWriteShortNegativeSize() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteShortNegativeSize";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        short data[] = new short[minBuffSize / 2];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        int dataLength = -10;
        assertTrue(TEST_NAME, track.write(data, OFFSET_DEFAULT, dataLength)
                                                        == AudioTrack.ERROR_BAD_VALUE);
        // -------- tear down --------------
        track.release();
    }

    // Test case 9: write() succeeds and returns the size that was written for
    // 16bit
    public void testWriteByte() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteByte";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.write(data, OFFSET_DEFAULT, data.length) == data.length);
        // -------- tear down --------------
        track.release();
    }

    // Test case 10: write() succeeds and returns the size that was written for
    // 16bit
    public void testWriteShort() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteShort";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        short data[] = new short[minBuffSize / 2];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertTrue(TEST_NAME, track.write(data, OFFSET_DEFAULT, data.length) == data.length);
        track.flush();
        // -------- tear down --------------
        track.release();
    }

    // Test case 11: write() succeeds and returns the size that was written for
    // 8bit
    public void testWriteByte8bit() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteByte8bit";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        byte data[] = new byte[minBuffSize];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertEquals(TEST_NAME, data.length, track.write(data, OFFSET_DEFAULT, data.length));
        // -------- tear down --------------
        track.release();
    }

    // Test case 12: write() succeeds and returns the size that was written for
    // 8bit
    public void testWriteShort8bit() throws Exception {
        // constants for test
        final String TEST_NAME = "testWriteShort8bit";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                2 * minBuffSize, TEST_MODE);
        short data[] = new short[minBuffSize / 2];
        // -------- test --------------
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        assertEquals(TEST_NAME, data.length, track.write(data, OFFSET_DEFAULT, data.length));
        // -------- tear down --------------
        track.release();
    }

    // -----------------------------------------------------------------
    // Getters
    // ----------------------------------

    // Test case 1: getMinBufferSize() return ERROR_BAD_VALUE if SR < 4000
    public void testGetMinBufferSizeTooLowSR() throws Exception {
        // constant for test
        final String TEST_NAME = "testGetMinBufferSizeTooLowSR";
        final int TEST_SR = 3999;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT;

        // -------- initialization & test --------------
        assertTrue(TEST_NAME, AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT) ==
            AudioTrack.ERROR_BAD_VALUE);
    }

    // Test case 2: getMinBufferSize() return ERROR_BAD_VALUE if sample rate too high
    public void testGetMinBufferSizeTooHighSR() throws Exception {
        // constant for test
        final String TEST_NAME = "testGetMinBufferSizeTooHighSR";
        // FIXME need an API to retrieve AudioTrack.SAMPLE_RATE_HZ_MAX
        final int TEST_SR = 192001;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT;

        // -------- initialization & test --------------
        assertTrue(TEST_NAME, AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT) ==
            AudioTrack.ERROR_BAD_VALUE);
    }

    public void testAudioTrackProperties() throws Exception {
        // constants for test
        final String TEST_NAME = "testAudioTrackProperties";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        MockAudioTrack track = new MockAudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF,
                TEST_FORMAT, 2 * minBuffSize, TEST_MODE);
        assertEquals(TEST_NAME, AudioTrack.STATE_INITIALIZED, track.getState());
        assertEquals(TEST_NAME, TEST_FORMAT, track.getAudioFormat());
        assertEquals(TEST_NAME, TEST_CONF, track.getChannelConfiguration());
        assertEquals(TEST_NAME, TEST_SR, track.getSampleRate());
        assertEquals(TEST_NAME, TEST_STREAM_TYPE, track.getStreamType());
        final int hannelCount = 1;
        assertEquals(hannelCount, track.getChannelCount());
        final int notificationMarkerPosition = 0;
        assertEquals(TEST_NAME, notificationMarkerPosition, track.getNotificationMarkerPosition());
        final int markerInFrames = 2;
        assertEquals(TEST_NAME, AudioTrack.SUCCESS,
                track.setNotificationMarkerPosition(markerInFrames));
        assertEquals(TEST_NAME, markerInFrames, track.getNotificationMarkerPosition());
        final int positionNotificationPeriod = 0;
        assertEquals(TEST_NAME, positionNotificationPeriod, track.getPositionNotificationPeriod());
        final int periodInFrames = 2;
        assertEquals(TEST_NAME, AudioTrack.SUCCESS,
                track.setPositionNotificationPeriod(periodInFrames));
        assertEquals(TEST_NAME, periodInFrames, track.getPositionNotificationPeriod());
        track.setState(AudioTrack.STATE_NO_STATIC_DATA);
        assertEquals(TEST_NAME, AudioTrack.STATE_NO_STATIC_DATA, track.getState());
        track.setState(AudioTrack.STATE_UNINITIALIZED);
        assertEquals(TEST_NAME, AudioTrack.STATE_UNINITIALIZED, track.getState());
        int frameCount = 2 * minBuffSize;
        if (TEST_CONF == AudioFormat.CHANNEL_CONFIGURATION_STEREO) {
            frameCount /= 2;
        }
        if (TEST_FORMAT == AudioFormat.ENCODING_PCM_16BIT) {
            frameCount /= 2;
        }
        assertTrue(TEST_NAME, track.getNativeFrameCount() >= frameCount);
        assertEquals(TEST_NAME, track.getNativeFrameCount(), track.getBufferSizeInFrames());
    }

    public void testReloadStaticData() throws Exception {
        // constants for test
        final String TEST_NAME = "testReloadStaticData";
        final int TEST_SR = 22050;
        final int TEST_CONF = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        // -------- initialization --------------
        int bufferSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        byte data[] = AudioHelper.createSoundDataInByteArray(
                bufferSize, TEST_SR, 1024 /* frequency */, 0 /* sweep */);
        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF, TEST_FORMAT,
                bufferSize, TEST_MODE);
        // -------- test --------------
        track.write(data, OFFSET_DEFAULT, bufferSize);
        assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);
        track.play();
        Thread.sleep(WAIT_MSEC);
        track.stop();
        Thread.sleep(WAIT_MSEC);
        assertEquals(TEST_NAME, AudioTrack.SUCCESS, track.reloadStaticData());
        track.play();
        Thread.sleep(WAIT_MSEC);
        track.stop();
        // -------- tear down --------------
        track.release();
    }

    public void testPlayStaticData() throws Exception {
        if (!hasAudioOutput()) {
            Log.w(TAG,"AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }
        // constants for test
        final String TEST_NAME = "testPlayStaticData";
        final int TEST_FORMAT_ARRAY[] = {  // 6 chirps repeated (TEST_LOOPS+1) times, 3 times
                AudioFormat.ENCODING_PCM_8BIT,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioFormat.ENCODING_PCM_FLOAT,
        };
        final int TEST_SR_ARRAY[] = {
                12055, // Note multichannel tracks will sound very short at low sample rates
                48000,
        };
        final int TEST_CONF_ARRAY[] = {
                AudioFormat.CHANNEL_OUT_MONO,    // 1.0
                AudioFormat.CHANNEL_OUT_STEREO,  // 2.0
                AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, // 7.1
        };
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
        final double TEST_SWEEP = 100;
        final int TEST_LOOPS = 1;

        for (int TEST_FORMAT : TEST_FORMAT_ARRAY) {
            double frequency = 400; // frequency changes for each test
            for (int TEST_SR : TEST_SR_ARRAY) {
                for (int TEST_CONF : TEST_CONF_ARRAY) {
                    // -------- initialization --------------
                    final int seconds = 1;
                    final int channelCount = Integer.bitCount(TEST_CONF);
                    final int bufferFrames = seconds * TEST_SR;
                    final int bufferSamples = bufferFrames * channelCount;
                    final int bufferSize = bufferSamples
                            * AudioFormat.getBytesPerSample(TEST_FORMAT);
                    final double testFrequency = frequency / channelCount;
                    final long MILLISECONDS_PER_SECOND = 1000;
                    AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR,
                            TEST_CONF, TEST_FORMAT, bufferSize, TEST_MODE);
                    assertEquals(TEST_NAME, AudioTrack.STATE_NO_STATIC_DATA, track.getState());

                    // -------- test --------------

                    // test setLoopPoints and setPosition can be called here.
                    assertEquals(TEST_NAME,
                            android.media.AudioTrack.SUCCESS,
                            track.setPlaybackHeadPosition(bufferFrames/2));
                    assertEquals(TEST_NAME,
                            android.media.AudioTrack.SUCCESS,
                            track.setLoopPoints(
                                    0 /*startInFrames*/, bufferFrames, 10 /*loopCount*/));
                    // only need to write once to the static track
                    switch (TEST_FORMAT) {
                    case AudioFormat.ENCODING_PCM_8BIT: {
                        byte data[] = AudioHelper.createSoundDataInByteArray(
                                bufferSamples, TEST_SR,
                                testFrequency, TEST_SWEEP);
                        assertEquals(TEST_NAME,
                                bufferSamples,
                                track.write(data, 0 /*offsetInBytes*/, data.length));
                        } break;
                    case AudioFormat.ENCODING_PCM_16BIT: {
                        short data[] = AudioHelper.createSoundDataInShortArray(
                                bufferSamples, TEST_SR,
                                testFrequency, TEST_SWEEP);
                        assertEquals(TEST_NAME,
                                bufferSamples,
                                track.write(data, 0 /*offsetInBytes*/, data.length));
                        } break;
                    case AudioFormat.ENCODING_PCM_FLOAT: {
                        float data[] = AudioHelper.createSoundDataInFloatArray(
                                bufferSamples, TEST_SR,
                                testFrequency, TEST_SWEEP);
                        assertEquals(TEST_NAME,
                                bufferSamples,
                                track.write(data, 0 /*offsetInBytes*/, data.length,
                                        AudioTrack.WRITE_BLOCKING));
                        } break;
                    }
                    assertEquals(TEST_NAME, AudioTrack.STATE_INITIALIZED, track.getState());
                    // test setLoopPoints and setPosition can be called here.
                    assertEquals(TEST_NAME,
                            android.media.AudioTrack.SUCCESS,
                            track.setPlaybackHeadPosition(0 /*positionInFrames*/));
                    assertEquals(TEST_NAME,
                            android.media.AudioTrack.SUCCESS,
                            track.setLoopPoints(0 /*startInFrames*/, bufferFrames, TEST_LOOPS));

                    track.play();
                    Thread.sleep(seconds * MILLISECONDS_PER_SECOND * (TEST_LOOPS + 1));
                    Thread.sleep(WAIT_MSEC);

                    // Check position after looping. AudioTrack.getPlaybackHeadPosition() returns
                    // the running count of frames played, not the actual static buffer position.
                    int position = track.getPlaybackHeadPosition();
                    assertEquals(TEST_NAME, bufferFrames * (TEST_LOOPS + 1), position);

                    track.stop();
                    Thread.sleep(WAIT_MSEC);
                    // -------- tear down --------------
                    track.release();
                    frequency += 70; // increment test tone frequency
                }
            }
        }
    }

    public void testPlayStreamData() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlayStreamData";
        final int TEST_FORMAT_ARRAY[] = {  // should hear 40 increasing frequency tones, 3 times
                AudioFormat.ENCODING_PCM_8BIT,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioFormat.ENCODING_PCM_FLOAT,
        };
        // due to downmixer algorithmic latency, source channels greater than 2 may
        // sound shorter in duration at 4kHz sampling rate.
        final int TEST_SR_ARRAY[] = {
                4000,
                44100,
                48000,
                96000,
                192000,
        };
        final int TEST_CONF_ARRAY[] = {
                AudioFormat.CHANNEL_OUT_MONO,    // 1.0
                AudioFormat.CHANNEL_OUT_STEREO,  // 2.0
                AudioFormat.CHANNEL_OUT_STEREO | AudioFormat.CHANNEL_OUT_FRONT_CENTER, // 3.0
                AudioFormat.CHANNEL_OUT_QUAD,    // 4.0
                AudioFormat.CHANNEL_OUT_QUAD | AudioFormat.CHANNEL_OUT_FRONT_CENTER,   // 5.0
                AudioFormat.CHANNEL_OUT_5POINT1, // 5.1
                AudioFormat.CHANNEL_OUT_5POINT1 | AudioFormat.CHANNEL_OUT_BACK_CENTER, // 6.1
                AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, // 7.1
        };
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
        final float TEST_SWEEP = 0; // sine wave only
        final boolean TEST_IS_LOW_RAM_DEVICE = isLowRamDevice();

        for (int TEST_FORMAT : TEST_FORMAT_ARRAY) {
            double frequency = 400; // frequency changes for each test
            for (int TEST_SR : TEST_SR_ARRAY) {
                for (int TEST_CONF : TEST_CONF_ARRAY) {
                    final int channelCount = Integer.bitCount(TEST_CONF);
                    if (TEST_IS_LOW_RAM_DEVICE
                            && (TEST_SR > 96000 || channelCount > 4)) {
                        continue; // ignore. FIXME: reenable when AF memory allocation is updated.
                    }
                    // -------- initialization --------------
                    final int minBufferSize = AudioTrack.getMinBufferSize(TEST_SR,
                            TEST_CONF, TEST_FORMAT); // in bytes
                    AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR,
                            TEST_CONF, TEST_FORMAT, minBufferSize, TEST_MODE);
                    assertTrue(TEST_NAME, track.getState() == AudioTrack.STATE_INITIALIZED);

                    // compute parameters for the source signal data.
                    AudioFormat format = track.getFormat();
                    assertEquals(TEST_NAME, TEST_SR, format.getSampleRate());
                    assertEquals(TEST_NAME, TEST_CONF, format.getChannelMask());
                    assertEquals(TEST_NAME, channelCount, format.getChannelCount());
                    assertEquals(TEST_NAME, TEST_FORMAT, format.getEncoding());
                    final int sourceSamples = channelCount
                            * AudioHelper.frameCountFromMsec(500,
                                    format); // duration of test tones
                    final double testFrequency = frequency / channelCount;

                    int written = 0;
                    // For streaming tracks, it's ok to issue the play() command
                    // before any audio is written.
                    track.play();
                    // -------- test --------------

                    // samplesPerWrite can be any positive value.
                    // We prefer this to be a multiple of channelCount so write()
                    // does not return a short count.
                    // If samplesPerWrite is very large, it is limited to the data length
                    // and we simply write (blocking) the entire source data and not even loop.
                    // We choose a value here which simulates double buffer writes.
                    final int buffers = 2; // double buffering mode
                    final int samplesPerWrite =
                            (track.getBufferSizeInFrames() / buffers) * channelCount;
                    switch (TEST_FORMAT) {
                    case AudioFormat.ENCODING_PCM_8BIT: {
                        byte data[] = AudioHelper.createSoundDataInByteArray(
                                sourceSamples, TEST_SR,
                                testFrequency, TEST_SWEEP);
                        while (written < data.length) {
                            int samples = Math.min(data.length - written, samplesPerWrite);
                            int ret = track.write(data, written, samples);
                            assertEquals(TEST_NAME, samples, ret);
                            written += ret;
                        }
                        } break;
                    case AudioFormat.ENCODING_PCM_16BIT: {
                        short data[] = AudioHelper.createSoundDataInShortArray(
                                sourceSamples, TEST_SR,
                                testFrequency, TEST_SWEEP);
                        while (written < data.length) {
                            int samples = Math.min(data.length - written, samplesPerWrite);
                            int ret = track.write(data, written, samples);
                            assertEquals(TEST_NAME, samples, ret);
                            written += ret;
                        }
                        } break;
                    case AudioFormat.ENCODING_PCM_FLOAT: {
                        float data[] = AudioHelper.createSoundDataInFloatArray(
                                sourceSamples, TEST_SR,
                                testFrequency, TEST_SWEEP);
                        while (written < data.length) {
                            int samples = Math.min(data.length - written, samplesPerWrite);
                            int ret = track.write(data, written, samples,
                                    AudioTrack.WRITE_BLOCKING);
                            assertEquals(TEST_NAME, samples, ret);
                            written += ret;
                        }
                        } break;
                    }

                    // For streaming tracks, AudioTrack.stop() doesn't immediately stop playback.
                    // Rather, it allows the remaining data in the internal buffer to drain.
                    track.stop();
                    Thread.sleep(WAIT_MSEC); // wait for the data to drain.
                    // -------- tear down --------------
                    track.release();
                    Thread.sleep(WAIT_MSEC); // wait for release to complete
                    frequency += 50; // increment test tone frequency
                }
            }
        }
    }

    public void testPlayStreamByteBuffer() throws Exception {
        // constants for test
        final String TEST_NAME = "testPlayStreamByteBuffer";
        final int TEST_FORMAT_ARRAY[] = {  // should hear 4 tones played 3 times
                AudioFormat.ENCODING_PCM_8BIT,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioFormat.ENCODING_PCM_FLOAT,
        };
        final int TEST_SR_ARRAY[] = {
                48000,
        };
        final int TEST_CONF_ARRAY[] = {
                AudioFormat.CHANNEL_OUT_STEREO,
        };
        final int TEST_WRITE_MODE_ARRAY[] = {
                AudioTrack.WRITE_BLOCKING,
                AudioTrack.WRITE_NON_BLOCKING,
        };
        final int TEST_MODE = AudioTrack.MODE_STREAM;
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;
        final float TEST_SWEEP = 0; // sine wave only

        for (int TEST_FORMAT : TEST_FORMAT_ARRAY) {
            double frequency = 800; // frequency changes for each test
            for (int TEST_SR : TEST_SR_ARRAY) {
                for (int TEST_CONF : TEST_CONF_ARRAY) {
                    for (int TEST_WRITE_MODE : TEST_WRITE_MODE_ARRAY) {
                        for (int useDirect = 0; useDirect < 2; ++useDirect) {
                            // -------- initialization --------------
                            int minBufferSize = AudioTrack.getMinBufferSize(TEST_SR,
                                    TEST_CONF, TEST_FORMAT); // in bytes
                            int bufferSize = 12 * minBufferSize;
                            int bufferSamples = bufferSize
                                    / AudioFormat.getBytesPerSample(TEST_FORMAT);

                            // create audio track and confirm settings
                            AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR,
                                    TEST_CONF, TEST_FORMAT, minBufferSize, TEST_MODE);
                            assertEquals(TEST_NAME + ": state",
                                    AudioTrack.STATE_INITIALIZED, track.getState());
                            assertEquals(TEST_NAME + ": sample rate",
                                    TEST_SR, track.getSampleRate());
                            assertEquals(TEST_NAME + ": channel mask",
                                    TEST_CONF, track.getChannelConfiguration());
                            assertEquals(TEST_NAME + ": encoding",
                                    TEST_FORMAT, track.getAudioFormat());

                            ByteBuffer bb = (useDirect == 1)
                                    ? ByteBuffer.allocateDirect(bufferSize)
                                            : ByteBuffer.allocate(bufferSize);
                            bb.order(java.nio.ByteOrder.nativeOrder());

                            // -------- test --------------
                            switch (TEST_FORMAT) {
                                case AudioFormat.ENCODING_PCM_8BIT: {
                                    byte data[] = AudioHelper.createSoundDataInByteArray(
                                            bufferSamples, TEST_SR,
                                            frequency, TEST_SWEEP);
                                    bb.put(data);
                                    bb.flip();
                                } break;
                                case AudioFormat.ENCODING_PCM_16BIT: {
                                    short data[] = AudioHelper.createSoundDataInShortArray(
                                            bufferSamples, TEST_SR,
                                            frequency, TEST_SWEEP);
                                    ShortBuffer sb = bb.asShortBuffer();
                                    sb.put(data);
                                    bb.limit(sb.limit() * 2);
                                } break;
                                case AudioFormat.ENCODING_PCM_FLOAT: {
                                    float data[] = AudioHelper.createSoundDataInFloatArray(
                                            bufferSamples, TEST_SR,
                                            frequency, TEST_SWEEP);
                                    FloatBuffer fb = bb.asFloatBuffer();
                                    fb.put(data);
                                    bb.limit(fb.limit() * 4);
                                } break;
                            }

                            boolean hasPlayed = false;
                            int written = 0;
                            while (written < bufferSize) {
                                int ret = track.write(bb,
                                        Math.min(bufferSize - written, minBufferSize),
                                        TEST_WRITE_MODE);
                                assertTrue(TEST_NAME, ret >= 0);
                                written += ret;
                                if (!hasPlayed) {
                                    track.play();
                                    hasPlayed = true;
                                }
                            }

                            track.stop();
                            Thread.sleep(WAIT_MSEC);
                            // -------- tear down --------------
                            track.release();
                            frequency += 200; // increment test tone frequency
                        }
                    }
                }
            }
        }
    }

    public void testPlayChannelIndexStreamBuffer() throws Exception {
        // should hear 4 tones played 3 or 4 times depending
        // on the device output capabilities (e.g. stereo or 5.1 or otherwise)
        final String TEST_NAME = "testPlayChannelIndexStreamBuffer";
        final int TEST_FORMAT_ARRAY[] = {
                AudioFormat.ENCODING_PCM_8BIT,
                //AudioFormat.ENCODING_PCM_16BIT,
                //AudioFormat.ENCODING_PCM_FLOAT,
        };
        final int TEST_SR_ARRAY[] = {
                48000,
        };
        // The following channel index masks are iterated over and route
        // the AudioTrack channels to the output sink channels based on
        // the set bits in counting order (lsb to msb).
        //
        // For a stereo output sink, the sound may come from L and R, L only, none, or R only.
        // For a 5.1 output sink, the sound may come from a variety of outputs
        // as commented below.
        final int TEST_CONF_ARRAY[] = { // matches output sink channels:
                (1 << 0) | (1 << 1), // Stereo(L, R) 5.1(FL, FR)
                (1 << 0) | (1 << 2), // Stereo(L)    5.1(FL, FC)
                (1 << 4) | (1 << 5), // Stereo(None) 5.1(BL, BR)
                (1 << 1) | (1 << 2), // Stereo(R)    5.1(FR, FC)
        };
        final int TEST_WRITE_MODE_ARRAY[] = {
                AudioTrack.WRITE_BLOCKING,
                AudioTrack.WRITE_NON_BLOCKING,
        };
        final float TEST_SWEEP = 0;

        for (int TEST_FORMAT : TEST_FORMAT_ARRAY) {
            for (int TEST_CONF : TEST_CONF_ARRAY) {
                double frequency = 800; // frequency changes for each test
                for (int TEST_SR : TEST_SR_ARRAY) {
                    for (int TEST_WRITE_MODE : TEST_WRITE_MODE_ARRAY) {
                        for (int useDirect = 0; useDirect < 2; ++useDirect) {
                            AudioFormat format = new AudioFormat.Builder()
                                    .setEncoding(TEST_FORMAT)
                                    .setSampleRate(TEST_SR)
                                    .setChannelIndexMask(TEST_CONF)
                                    .build();
                            AudioTrack track = new AudioTrack.Builder()
                                    .setAudioFormat(format)
                                    .build();
                            assertEquals(TEST_NAME,
                                    AudioTrack.STATE_INITIALIZED, track.getState());

                            // create the byte buffer and fill with test data
                            final int frameSize = AudioHelper.frameSizeFromFormat(format);
                            final int frameCount =
                                    AudioHelper.frameCountFromMsec(300 /* ms */, format);
                            final int bufferSize = frameCount * frameSize;
                            final int bufferSamples = frameCount * format.getChannelCount();
                            ByteBuffer bb = (useDirect == 1)
                                    ? ByteBuffer.allocateDirect(bufferSize)
                                            : ByteBuffer.allocate(bufferSize);
                            bb.order(java.nio.ByteOrder.nativeOrder());

                            switch (TEST_FORMAT) {
                            case AudioFormat.ENCODING_PCM_8BIT: {
                                byte data[] = AudioHelper.createSoundDataInByteArray(
                                        bufferSamples, TEST_SR,
                                        frequency, TEST_SWEEP);
                                bb.put(data);
                                bb.flip();
                            } break;
                            case AudioFormat.ENCODING_PCM_16BIT: {
                                short data[] = AudioHelper.createSoundDataInShortArray(
                                        bufferSamples, TEST_SR,
                                        frequency, TEST_SWEEP);
                                ShortBuffer sb = bb.asShortBuffer();
                                sb.put(data);
                                bb.limit(sb.limit() * 2);
                            } break;
                            case AudioFormat.ENCODING_PCM_FLOAT: {
                                float data[] = AudioHelper.createSoundDataInFloatArray(
                                        bufferSamples, TEST_SR,
                                        frequency, TEST_SWEEP);
                                FloatBuffer fb = bb.asFloatBuffer();
                                fb.put(data);
                                bb.limit(fb.limit() * 4);
                            } break;
                            }

                            // start the AudioTrack
                            // This can be done before or after the first write.
                            // Current behavior for streaming tracks is that
                            // actual playback does not begin before the internal
                            // data buffer is completely full.
                            track.play();

                            // write data
                            final long startTime = System.currentTimeMillis();
                            final long maxDuration = frameCount * 1000 / TEST_SR + 1000;
                            for (int written = 0; written < bufferSize; ) {
                                // ret may return a short count if write
                                // is non blocking or even if write is blocking
                                // when a stop/pause/flush is issued from another thread.
                                final int kBatchFrames = 1000;
                                int ret = track.write(bb,
                                        Math.min(bufferSize - written, frameSize * kBatchFrames),
                                        TEST_WRITE_MODE);
                                // for non-blocking mode, this loop may spin quickly
                                assertTrue(TEST_NAME + ": write error " + ret, ret >= 0);
                                assertTrue(TEST_NAME + ": write timeout",
                                        (System.currentTimeMillis() - startTime) <= maxDuration);
                                written += ret;
                            }

                            // for streaming tracks, stop will allow the rest of the data to
                            // drain out, but we don't know how long to wait unless
                            // we check the position before stop. if we check position
                            // after we stop, we read 0.
                            final int position = track.getPlaybackHeadPosition();
                            final int remainingTimeMs = (int)((double)(frameCount - position)
                                    * 1000 / TEST_SR);
                            track.stop();
                            Thread.sleep(remainingTimeMs);
                            // tear down
                            track.release();
                            // add a gap to make tones distinct
                            Thread.sleep(100 /* millis */);
                            frequency += 200; // increment test tone frequency
                        }
                    }
                }
            }
        }
    }

    private boolean hasAudioOutput() {
        return getContext().getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
    }

    private boolean isLowRamDevice() {
        return ((ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE))
                .isLowRamDevice();
    }

    public void testGetTimestamp() throws Exception {
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }
        String streamName = "test_get_timestamp";
        doTestTimestamp(
                22050 /* sampleRate */,
                AudioFormat.CHANNEL_OUT_MONO ,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioTrack.MODE_STREAM,
                streamName);
    }

    public void testFastTimestamp() throws Exception {
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }
        String streamName = "test_fast_timestamp";
        doTestTimestamp(
                AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC),
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioTrack.MODE_STREAM,
                streamName);
    }

    private void doTestTimestamp(int sampleRate, int channelMask, int encoding, int transferMode,
            String streamName) throws Exception {
        // constants for test
        final String TEST_NAME = "testGetTimestamp";
        final int TEST_LOOP_CNT = 10;
        final int TEST_BUFFER_MS = 100;
        final int TEST_USAGE = AudioAttributes.USAGE_MEDIA;
        // For jitter we allow 30 msec in frames.  This is a large margin.
        // Often this is just 0 or 1 frames, but that can depend on hardware.
        final int TEST_JITTER_FRAMES_ALLOWED = sampleRate * 30 / 1000;

        // -------- initialization --------------
        final int frameSize =
                AudioFormat.getBytesPerSample(encoding)
                * AudioFormat.channelCountFromOutChannelMask(channelMask);
        // see whether we can use fast mode
        final int nativeOutputSampleRate =
                AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        Log.d(TAG, "Native output sample rate " + nativeOutputSampleRate);
        final boolean fast = (sampleRate == nativeOutputSampleRate);

        AudioAttributes attributes = (fast ? new AudioAttributes.Builder()
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY) : new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                //.setChannelIndexMask((1 << AudioFormat.channelCountFromOutChannelMask(channelMask)) - 1)
                .setChannelMask(channelMask)
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .build();
        // not specifying the buffer size in the builder should get us the minimum buffer size.
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(transferMode)
                .build();
        assertEquals(AudioTrack.STATE_INITIALIZED, track.getState());
        // We generally use a transfer size of 100ms for testing, but in rare cases
        // (e.g. Bluetooth) this needs to be larger to exceed the internal track buffer.
        final int frameCount =
                Math.max(track.getBufferCapacityInFrames(), sampleRate * TEST_BUFFER_MS / 1000);
        track.play();

        ByteBuffer data = ByteBuffer.allocate(frameCount * frameSize);
        data.order(java.nio.ByteOrder.nativeOrder()).limit(frameCount * frameSize);
        AudioTimestamp timestamp = new AudioTimestamp();
        long framesWritten = 0, lastFramesPresented = 0, lastFramesPresentedAt = 0;
        int cumulativeJitterCount = 0;
        int differentials = 0;
        float cumulativeJitter = 0;
        float maxJitter = 0;
        for (int i = 0; i < TEST_LOOP_CNT; i++) {
            final long writeTime = System.nanoTime();

            data.position(0);
            assertEquals(data.limit(),
                    track.write(data, data.limit(), AudioTrack.WRITE_BLOCKING));
            assertEquals(data.position(), data.limit());
            framesWritten += data.limit() / frameSize;

            // track.getTimestamp may return false if there are no physical HAL outputs.
            // This may occur on TV devices without connecting an HDMI monitor.
            // It may also be true immediately after start-up, as the mixing thread could
            // be idle, but since we've already pushed much more than the minimum buffer size,
            // that is unlikely.
            // Nevertheless, we don't want to have unnecessary failures, so we ignore the
            // first iteration if we don't get a timestamp.
            final boolean result = track.getTimestamp(timestamp);
            assertTrue("timestamp could not be read", result || i == 0);
            if (!result) {
                continue;
            }

            final long framesPresented = timestamp.framePosition;
            final long framesPresentedAt = timestamp.nanoTime;

            // We read timestamp here to ensure that seen is greater than presented.
            // This is an "on-the-fly" read without pausing because pausing may cause the
            // timestamp to become stale and affect our jitter measurements.
            final int framesSeen = track.getPlaybackHeadPosition();
            assertTrue("server frames ahead of client frames", framesWritten >= framesSeen);
            assertTrue("presented frames ahead of server frames", framesSeen >= framesPresented);

            if (i > 0) { // need delta info from previous iteration (skipping first)
                final long deltaFrames = framesPresented - lastFramesPresented;
                final long deltaTime = framesPresentedAt - lastFramesPresentedAt;
                final long NANOSECONDS_PER_SECOND = 1000000000;
                final long expectedFrames = deltaTime * sampleRate / NANOSECONDS_PER_SECOND;
                final long jitterFrames = Math.abs(deltaFrames - expectedFrames);

                Log.d(TAG, "framesWritten(" + framesWritten
                        + ") framesSeen(" + framesSeen
                        + ") framesPresented(" + framesPresented
                        + ") framesPresentedAt(" + framesPresentedAt
                        + ") lastframesPresented(" + lastFramesPresented
                        + ") lastFramesPresentedAt(" + lastFramesPresentedAt
                        + ") deltaFrames(" + deltaFrames
                        + ") deltaTime(" + deltaTime
                        + ") expectedFrames(" + expectedFrames
                        + ") writeTime(" + writeTime
                        + ") jitter(" + jitterFrames + ")");
                assertTrue("timestamp time should be increasing", deltaTime >= 0);
                assertTrue("timestamp frames should be increasing", deltaFrames >= 0);

                // the first nonzero value may have a jump, wait for the second.
                if (lastFramesPresented != 0) {
                    if (differentials++ > 1) {
                        // We check that the timestamp position is reasonably accurate.
                        assertTrue("jitterFrames(" + jitterFrames + ") < "
                                + TEST_JITTER_FRAMES_ALLOWED,
                                jitterFrames < TEST_JITTER_FRAMES_ALLOWED);
                        cumulativeJitter += jitterFrames;
                        cumulativeJitterCount++;
                        if (jitterFrames > maxJitter) {
                            maxJitter = jitterFrames;
                        }
                    }
                    final long NANOS_PER_SECOND = 1000000000;
                    final long closeTimeNs = frameCount * 2 * NANOS_PER_SECOND / sampleRate;
                    // We check that the timestamp time is reasonably current.
                    assertTrue("framesPresentedAt(" + framesPresentedAt
                            + ") close to writeTime(" + writeTime
                            + ") tolerance(" + closeTimeNs
                            + ")", Math.abs(framesPresentedAt - writeTime) <= closeTimeNs);
                    assertTrue("timestamps must have causal time",
                            writeTime >= lastFramesPresentedAt);
                }
            }
            lastFramesPresented = framesPresented;
            lastFramesPresentedAt = framesPresentedAt;
        }
        // Full drain.
        Thread.sleep(1000 /* millis */);
        // check that we are really at the end of playback.
        assertTrue("timestamp should be valid while draining", track.getTimestamp(timestamp));
        // fast tracks and sw emulated tracks may not fully drain.  we log the status here.
        if (framesWritten != timestamp.framePosition) {
            Log.d(TAG, "timestamp should fully drain.  written: "
                    + framesWritten + " position: " + timestamp.framePosition);
        }
        assertTrue("sufficient nonzero timestamps", differentials > 2);

        track.release();
        // Log the average jitter
        if (cumulativeJitterCount > 0) {
            DeviceReportLog log = new DeviceReportLog(REPORT_LOG_NAME, streamName);
            final float averageJitterInFrames = cumulativeJitter / cumulativeJitterCount;
            final float averageJitterInMs = averageJitterInFrames * 1000 / sampleRate;
            final float maxJitterInMs = maxJitter * 1000 / sampleRate;
            // ReportLog needs at least one Value and Summary.
            log.addValue("maximum_jitter", maxJitterInMs,
                    ResultType.LOWER_BETTER, ResultUnit.MS);
            log.setSummary("average_jitter", averageJitterInMs,
                    ResultType.LOWER_BETTER, ResultUnit.MS);
            log.submit(getInstrumentation());
        }
    }

    public void testVariableRatePlayback() throws Exception {
        final String TEST_NAME = "testVariableRatePlayback";
        final int TEST_SR = 24000;
        final int TEST_FINAL_SR = 96000;
        final int TEST_CONF = AudioFormat.CHANNEL_OUT_MONO;
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_8BIT; // required for test
        final int TEST_MODE = AudioTrack.MODE_STATIC; // required for test
        final int TEST_STREAM_TYPE = AudioManager.STREAM_MUSIC;

        final int minBuffSize = AudioTrack.getMinBufferSize(TEST_SR, TEST_CONF, TEST_FORMAT);
        final int bufferSizeInBytes = minBuffSize * 100;
        final int numChannels =  AudioFormat.channelCountFromOutChannelMask(TEST_CONF);
        final int bytesPerSample = AudioFormat.getBytesPerSample(TEST_FORMAT);
        final int bytesPerFrame = numChannels * bytesPerSample;
        final int frameCount = bufferSizeInBytes / bytesPerFrame;

        AudioTrack track = new AudioTrack(TEST_STREAM_TYPE, TEST_SR, TEST_CONF,
                TEST_FORMAT, bufferSizeInBytes, TEST_MODE);

        // create byte array and write it
        byte[] vai = AudioHelper.createSoundDataInByteArray(bufferSizeInBytes, TEST_SR,
                600 /* frequency */, 0 /* sweep */);
        assertEquals(vai.length, track.write(vai, 0 /* offsetInBytes */, vai.length));

        // sweep up test and sweep down test
        int[] sampleRates = {TEST_SR, TEST_FINAL_SR};
        int[] deltaMss = {10, 10};
        int[] deltaFreqs = {200, -200};

        for (int i = 0; i < 2; ++i) {
            int remainingTime;
            int sampleRate = sampleRates[i];
            final int deltaMs = deltaMss[i];
            final int deltaFreq = deltaFreqs[i];
            final int lastCheckMs = 500; // check the last 500 ms

            assertEquals(TEST_NAME, AudioTrack.SUCCESS, track.setPlaybackRate(sampleRate));
            track.play();
            do {
                Thread.sleep(deltaMs);
                final int position = track.getPlaybackHeadPosition();
                sampleRate += deltaFreq;
                sampleRate = Math.min(TEST_FINAL_SR, Math.max(TEST_SR, sampleRate));
                assertEquals(TEST_NAME, AudioTrack.SUCCESS, track.setPlaybackRate(sampleRate));
                remainingTime = (int)((double)(frameCount - position) * 1000
                        / sampleRate / bytesPerFrame);
            } while (remainingTime >= lastCheckMs + deltaMs);

            // ensure the final frequency set is constant and plays frames as expected
            final int position1 = track.getPlaybackHeadPosition();
            Thread.sleep(lastCheckMs);
            final int position2 = track.getPlaybackHeadPosition();

            final int tolerance60MsInFrames = sampleRate * 60 / 1000;
            final int expected = lastCheckMs * sampleRate / 1000;
            final int actual = position2 - position1;

            // Log.d(TAG, "Variable Playback: expected(" + expected + ")  actual(" + actual
            //        + ")  diff(" + (expected - actual) + ")");
            assertEquals(expected, actual, tolerance60MsInFrames);
            track.stop();
        }
        track.release();
    }

    public void testVariableSpeedPlayback() throws Exception {
        if (!hasAudioOutput()) {
            Log.w(TAG,"AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        final String TEST_NAME = "testVariableSpeedPlayback";
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_FLOAT; // required for test
        final int TEST_MODE = AudioTrack.MODE_STATIC;           // required for test
        final int TEST_SR = 48000;

        AudioFormat format = new AudioFormat.Builder()
                //.setChannelIndexMask((1 << 0))  // output to first channel, FL
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(TEST_FORMAT)
                .setSampleRate(TEST_SR)
                .build();

        // create track
        final int frameCount = AudioHelper.frameCountFromMsec(100 /*ms*/, format);
        final int frameSize = AudioHelper.frameSizeFromFormat(format);
        AudioTrack track = new AudioTrack.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(frameCount * frameSize)
                .setTransferMode(TEST_MODE)
                .build();

        // create float array and write it
        final int sampleCount = frameCount * format.getChannelCount();
        float[] vaf = AudioHelper.createSoundDataInFloatArray(
                sampleCount, TEST_SR, 600 /* frequency */, 0 /* sweep */);
        assertEquals(vaf.length, track.write(vaf, 0 /* offsetInFloats */, vaf.length,
                AudioTrack.WRITE_NON_BLOCKING));

        // sweep speed and pitch
        final float[][][] speedAndPitch = {
             // { {speedStart, pitchStart} {speedEnd, pitchEnd} }
                { {0.5f, 0.5f}, {2.0f, 2.0f} },  // speed by SR conversion (chirp)
                { {0.5f, 1.0f}, {2.0f, 1.0f} },  // speed by time stretch (constant pitch)
                { {1.0f, 0.5f}, {1.0f, 2.0f} },  // pitch by SR conversion (chirp)
        };

        // sanity test that playback params works as expected
        PlaybackParams params = new PlaybackParams().allowDefaults();
        assertEquals(TEST_NAME, 1.0f, params.getSpeed());
        assertEquals(TEST_NAME, 1.0f, params.getPitch());
        assertEquals(TEST_NAME,
                params.AUDIO_FALLBACK_MODE_DEFAULT,
                params.getAudioFallbackMode());
        track.setPlaybackParams(params); // OK
        params.setAudioFallbackMode(params.AUDIO_FALLBACK_MODE_FAIL);
        assertEquals(TEST_NAME,
                params.AUDIO_FALLBACK_MODE_FAIL, params.getAudioFallbackMode());
        params.setPitch(0.0f);
        try {
            track.setPlaybackParams(params);
            fail("IllegalArgumentException should be thrown on out of range data");
        } catch (IllegalArgumentException e) {
            ; // expect this is invalid
        }
        // on failure, the AudioTrack params should not change.
        PlaybackParams paramCheck = track.getPlaybackParams();
        assertEquals(TEST_NAME,
                paramCheck.AUDIO_FALLBACK_MODE_DEFAULT, paramCheck.getAudioFallbackMode());
        assertEquals(TEST_NAME,
                1.0f, paramCheck.getPitch());

        // now try to see if we can do extreme pitch correction that should probably be muted.
        params.setAudioFallbackMode(params.AUDIO_FALLBACK_MODE_MUTE);
        assertEquals(TEST_NAME,
                params.AUDIO_FALLBACK_MODE_MUTE, params.getAudioFallbackMode());
        params.setPitch(0.1f);
        track.setPlaybackParams(params); // OK

        // now do our actual playback
        final int TEST_TIME_MS = 2000;
        final int TEST_DELTA_MS = 100;
        final int testSteps = TEST_TIME_MS / TEST_DELTA_MS;

        for (int i = 0; i < speedAndPitch.length; ++i) {
            final float speedStart = speedAndPitch[i][0][0];
            final float pitchStart = speedAndPitch[i][0][1];
            final float speedEnd = speedAndPitch[i][1][0];
            final float pitchEnd = speedAndPitch[i][1][1];
            final float speedInc = (speedEnd - speedStart) / testSteps;
            final float pitchInc = (pitchEnd - pitchStart) / testSteps;

            PlaybackParams playbackParams = new PlaybackParams()
                    .setPitch(pitchStart)
                    .setSpeed(speedStart)
                    .allowDefaults();

            // set track in infinite loop to be a sine generator
            track.setLoopPoints(0, frameCount, -1 /* loopCount */); // cleared by stop()
            track.play();

            Thread.sleep(300 /* millis */); // warm up track

            int anticipatedPosition = track.getPlaybackHeadPosition();
            for (int j = 0; j < testSteps; ++j) {
                // set playback settings
                final float pitch = playbackParams.getPitch();
                final float speed = playbackParams.getSpeed();

                track.setPlaybackParams(playbackParams);

                // verify that settings have changed
                PlaybackParams checkParams = track.getPlaybackParams();
                assertEquals(TAG, pitch, checkParams.getPitch());
                assertEquals(TAG, speed, checkParams.getSpeed());

                // sleep for playback
                Thread.sleep(TEST_DELTA_MS);
                // Log.d(TAG, "position[" + j + "] " + track.getPlaybackHeadPosition());
                anticipatedPosition +=
                        playbackParams.getSpeed() * TEST_DELTA_MS * TEST_SR / 1000;
                playbackParams.setPitch(playbackParams.getPitch() + pitchInc);
                playbackParams.setSpeed(playbackParams.getSpeed() + speedInc);
            }
            final int endPosition = track.getPlaybackHeadPosition();
            final int tolerance100MsInFrames = 100 * TEST_SR / 1000;
            assertEquals(TAG, anticipatedPosition, endPosition, tolerance100MsInFrames);
            track.stop();

            Thread.sleep(100 /* millis */); // distinct pause between each test
        }
        track.release();
    }

    // Test AudioTrack to ensure we can build after a failure.
    public void testAudioTrackBufferSize() throws Exception {
        // constants for test
        final String TEST_NAME = "testAudioTrackBufferSize";

        // use builder with parameters that should fail
        final int superBigBufferSize = 1 << 28;
        try {
            final AudioTrack track = new AudioTrack.Builder()
                .setBufferSizeInBytes(superBigBufferSize)
                .build();
            track.release();
            fail(TEST_NAME + ": should throw exception on failure");
        } catch (UnsupportedOperationException e) {
            ;
        }

        // we should be able to create again with minimum buffer size
        final int verySmallBufferSize = 2 * 3 * 4; // frame size multiples
        final AudioTrack track2 = new AudioTrack.Builder()
                .setBufferSizeInBytes(verySmallBufferSize)
                .build();

        final int observedState2 = track2.getState();
        final int observedBufferSize2 = track2.getBufferSizeInFrames();
        track2.release();

        // succeeds for minimum buffer size
        assertEquals(TEST_NAME + ": state", AudioTrack.STATE_INITIALIZED, observedState2);
        // should force the minimum size buffer which is > 0
        assertTrue(TEST_NAME + ": buffer frame count", observedBufferSize2 > 0);
    }

/* Do not run in JB-MR1. will be re-opened in the next platform release.
    public void testResourceLeakage() throws Exception {
        final int BUFFER_SIZE = 600 * 1024;
        ByteBuffer data = ByteBuffer.allocate(BUFFER_SIZE);
        for (int i = 0; i < 10; i++) {
            Log.i(TAG, "testResourceLeakage round " + i);
            data.rewind();
            AudioTrack track = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
                                              44100,
                                              AudioFormat.CHANNEL_OUT_STEREO,
                                              AudioFormat.ENCODING_PCM_16BIT,
                                              data.capacity(),
                                              AudioTrack.MODE_STREAM);
            assertTrue(track != null);
            track.write(data.array(), 0, data.capacity());
            track.play();
            Thread.sleep(100);
            track.stop();
            track.release();
        }
    }
*/

    /* MockAudioTrack allows testing of protected getNativeFrameCount() and setState(). */
    private class MockAudioTrack extends AudioTrack {

        public MockAudioTrack(int streamType, int sampleRateInHz, int channelConfig,
                int audioFormat, int bufferSizeInBytes, int mode) throws IllegalArgumentException {
            super(streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, mode);
        }

        public void setState(int state) {
            super.setState(state);
        }

        public int getNativeFrameCount() {
            return super.getNativeFrameCount();
        }
    }

}
