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
import android.media.audiofx.AudioEffect;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.LoudnessEnhancer;
import java.util.UUID;
import android.media.audiofx.Visualizer;
import android.media.audiofx.Visualizer.MeasurementPeakRms;
import android.os.Looper;
import android.test.AndroidTestCase;
import android.util.Log;

public class LoudnessEnhancerTest extends PostProcTestBase {

    private String TAG = "LoudnessEnhancerTest";
    private LoudnessEnhancer mLE;

    //-----------------------------------------------------------------
    // LOUDNESS ENHANCER TESTS:
    //----------------------------------

    //-----------------------------------------------------------------
    // 0 - constructor
    //----------------------------------

    //Test case 0.0: test constructor and release
    public void test0_0ConstructorAndRelease() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        assertNotNull("null AudioManager", am);
        getLoudnessEnhancer(0);
        releaseLoudnessEnhancer();

        int session = am.generateAudioSessionId();
        assertTrue("cannot generate new session", session != AudioManager.ERROR);
        getLoudnessEnhancer(session);
        releaseLoudnessEnhancer();
    }

    //-----------------------------------------------------------------
    // 1 - get/set parameters
    //----------------------------------

    //Test case 1.0: test set/get target gain
    public void test1_0TargetGain() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        getLoudnessEnhancer(0);
        try {
            mLE.setTargetGain(0);
            assertEquals("target gain differs from value set", 0.0f, mLE.getTargetGain());
            mLE.setTargetGain(800);
            assertEquals("target gain differs from value set", 800.0f, mLE.getTargetGain());
        } catch (IllegalArgumentException e) {
            fail("target gain illegal argument");
        } catch (UnsupportedOperationException e) {
            fail("target gain unsupported operation");
        } catch (IllegalStateException e) {
            fail("target gain operation called in wrong state");
        } finally {
            releaseLoudnessEnhancer();
        }
    }

    //-----------------------------------------------------------------
    // 2 - Effect enable/disable
    //----------------------------------

    //Test case 2.0: test setEnabled() and getEnabled() in valid state
    public void test2_0SetEnabledGetEnabled() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        getLoudnessEnhancer(getSessionId());
        try {
            mLE.setEnabled(true);
            assertTrue("invalid state from getEnabled", mLE.getEnabled());
            mLE.setEnabled(false);
            assertFalse("invalid state to getEnabled", mLE.getEnabled());
            // test passed
        } catch (IllegalStateException e) {
            fail("setEnabled() in wrong state");
        } finally {
            releaseLoudnessEnhancer();
        }
    }

    //Test case 2.1: test setEnabled() throws exception after release
    public void test2_1SetEnabledAfterRelease() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        getLoudnessEnhancer(getSessionId());
        mLE.release();
        try {
            mLE.setEnabled(true);
            fail("setEnabled() processed after release()");
        } catch (IllegalStateException e) {
            // test passed
        } finally {
            releaseLoudnessEnhancer();
        }
    }

    //-----------------------------------------------------------------
    // 3 - check effect using visualizer effect
    //----------------------------------

    //Test case 3.0: test loudness gain change in audio
    public void test3_0MeasureGainChange() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        AudioEffect vc = null;
        Visualizer visualizer = null;
        MediaPlayer mp = null;
        try {
            // this test will play a 1kHz sine wave with peaks at -40dB, and apply 6 db gain
            // using loudness enhancement
            mp = MediaPlayer.create(getContext(), R.raw.sine1khzm40db);
            final int LOUDNESS_GAIN = 600;
            final int MAX_MEASUREMENT_ERROR_MB = 200;
            assertNotNull("null MediaPlayer", mp);

            // creating a volume controller on output mix ensures that ro.audio.silent mutes
            // audio after the effects and not before
            vc = new AudioEffect(
                    AudioEffect.EFFECT_TYPE_NULL,
                    UUID.fromString(BUNDLE_VOLUME_EFFECT_UUID),
                    0,
                    mp.getAudioSessionId());
            vc.setEnabled(true);

            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            assertNotNull("null AudioManager", am);
            int originalVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC,
                    am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            int sessionId = mp.getAudioSessionId();

            getLoudnessEnhancer(sessionId);
            visualizer = new Visualizer(sessionId);

            mp.setLooping(true);
            mp.start();

            visualizer.setEnabled(true);
            assertTrue("visualizer not enabled", visualizer.getEnabled());
            Thread.sleep(100);
            int status = visualizer.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS);
            Thread.sleep(500);
            assertEquals("setMeasurementMode() for PEAK_RMS doesn't report success",
                    Visualizer.SUCCESS, status);
            // make sure we're playing long enough so the measurement is valid
            int currentPosition = mp.getCurrentPosition();
            int maxTry = 100;
            int tryCount = 0;
            while (currentPosition < 200 && tryCount < maxTry) {
                Thread.sleep(50);
                currentPosition = mp.getCurrentPosition();
                tryCount++;
            }
            assertTrue("MediaPlayer not ready", tryCount < maxTry);

            MeasurementPeakRms measurement = new MeasurementPeakRms();
            status = visualizer.getMeasurementPeakRms(measurement);
            assertEquals("getMeasurementPeakRms() reports failure", Visualizer.SUCCESS, status);

            //run for a new set of 3 seconds, get new measurement
            mLE.setTargetGain(LOUDNESS_GAIN);
            assertEquals("target gain differs from value set", (float)LOUDNESS_GAIN,
                    mLE.getTargetGain());

            mLE.setEnabled(true);

            visualizer.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS);
            Thread.sleep(500);

            MeasurementPeakRms measurement2 = new MeasurementPeakRms();
            status = visualizer.getMeasurementPeakRms(measurement2);
            assertEquals("getMeasurementPeakRms() reports failure", Visualizer.SUCCESS, status);

            //compare both measurements
            am.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
            assertEquals("getMeasurementPeakRms() reports failure",
                    Visualizer.SUCCESS, status);
            Log.i("LETest", "peak="+measurement.mPeak+"  rms="+measurement.mRms);
            Log.i("LETest", "peak2="+measurement2.mPeak+"  rms2="+measurement2.mRms);

            int deltaPeak = Math.abs(measurement2.mPeak - (measurement.mPeak + LOUDNESS_GAIN) );
            assertTrue("peak deviation in mB = "+deltaPeak, deltaPeak < MAX_MEASUREMENT_ERROR_MB);

        } catch (IllegalStateException e) {
            fail("method called in wrong state");
        } catch (InterruptedException e) {
            fail("sleep() interrupted");
        } finally {
            if (mp != null) {
                mp.stop();
                mp.release();
            }

            if (vc != null)
                vc.release();

            if (visualizer != null)
                visualizer.release();

            releaseLoudnessEnhancer();
        }
    }

    //-----------------------------------------------------------------
    // private methods
    //----------------------------------
    private void getLoudnessEnhancer(int session) {
        releaseLoudnessEnhancer();
        try {
            mLE = new LoudnessEnhancer(session);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "getLoudnessEnhancer() LoudnessEnhancer not found exception: ", e);
        } catch (UnsupportedOperationException e) {
            Log.e(TAG, "getLoudnessEnhancer() Effect library not loaded exception: ", e);
        }
        assertNotNull("could not create LoudnessEnhancer", mLE);
    }

    private void releaseLoudnessEnhancer() {
        if (mLE != null) {
            mLE.release();
            mLE = null;
        }
    }
}