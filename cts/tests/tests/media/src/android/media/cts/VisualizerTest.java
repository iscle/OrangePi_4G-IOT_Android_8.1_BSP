/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.media.audiofx.Visualizer;
import android.media.audiofx.Visualizer.MeasurementPeakRms;
import android.os.Looper;
import android.test.AndroidTestCase;
import java.util.UUID;
import android.util.Log;

public class VisualizerTest extends PostProcTestBase {

    private String TAG = "VisualizerTest";
    private final static int MIN_CAPTURE_RATE_MAX = 10000; // 10Hz
    private final static int MIN_CAPTURE_SIZE_MAX = 1024;
    private final static int MAX_CAPTURE_SIZE_MIN = 512;
    private final static int MAX_LOOPER_WAIT_COUNT = 10;

    private Visualizer mVisualizer = null;
    private byte[] mWaveform = null;
    private byte[] mFft = null;
    private boolean mCaptureWaveform = false;
    private boolean mCaptureFft = false;
    private Thread mListenerThread;

    //-----------------------------------------------------------------
    // VISUALIZER TESTS:
    //----------------------------------

    //-----------------------------------------------------------------
    // 0 - constructor
    //----------------------------------

    //Test case 0.0: test constructor and release
    public void test0_0ConstructorAndRelease() throws Exception {
        Visualizer visualizer = null;
        try {
            visualizer = new Visualizer(0);
        } catch (IllegalArgumentException e) {
            fail("Visualizer not found");
        } catch (UnsupportedOperationException e) {
            fail("Effect library not loaded");
        } finally {
            if (visualizer != null) {
                visualizer.release();
            }
        }
    }


    //-----------------------------------------------------------------
    // 1 - get/set parameters
    //----------------------------------

    //Test case 1.0: capture rates
    public void test1_0CaptureRates() throws Exception {
        getVisualizer(0);
        try {
            int captureRate = mVisualizer.getMaxCaptureRate();
            assertTrue("insufficient max capture rate",
                    captureRate >= MIN_CAPTURE_RATE_MAX);
            int samplingRate = mVisualizer.getSamplingRate();
        } catch (IllegalArgumentException e) {
            fail("Bad parameter value");
        } catch (UnsupportedOperationException e) {
            fail("get parameter() rejected");
        } catch (IllegalStateException e) {
            fail("get parameter() called in wrong state");
        } finally {
            releaseVisualizer();
        }
    }

    //Test case 1.1: test capture size
    public void test1_1CaptureSize() throws Exception {
        getVisualizer(0);
        try {
            int[] range = mVisualizer.getCaptureSizeRange();
            assertTrue("insufficient min capture size",
                    range[0] <= MAX_CAPTURE_SIZE_MIN);
            assertTrue("insufficient min capture size",
                    range[1] >= MIN_CAPTURE_SIZE_MAX);
            mVisualizer.setCaptureSize(range[0]);
            assertEquals("insufficient min capture size",
                    range[0], mVisualizer.getCaptureSize());
            mVisualizer.setCaptureSize(range[1]);
            assertEquals("insufficient min capture size",
                    range[1], mVisualizer.getCaptureSize());
        } catch (IllegalArgumentException e) {
            fail("Bad parameter value");
        } catch (UnsupportedOperationException e) {
            fail("get parameter() rejected");
        } catch (IllegalStateException e) {
            fail("get parameter() called in wrong state");
        } finally {
            releaseVisualizer();
        }
    }

    //-----------------------------------------------------------------
    // 2 - check capture
    //----------------------------------

    //Test case 2.0: test capture in polling mode
    public void test2_0PollingCapture() throws Exception {
        if (!hasAudioOutput()) {
            Log.w(TAG,"AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }
        try {
            getVisualizer(0);
            mVisualizer.setEnabled(true);
            assertTrue("visualizer not enabled", mVisualizer.getEnabled());
            Thread.sleep(100);
            // check capture on silence
            byte[] data = new byte[mVisualizer.getCaptureSize()];
            mVisualizer.getWaveForm(data);
            int energy = computeEnergy(data, true);
            assertEquals("getWaveForm reports energy for silence",
                    0, energy);
            mVisualizer.getFft(data);
            energy = computeEnergy(data, false);
            assertEquals("getFft reports energy for silence",
                    0, energy);

        } catch (IllegalStateException e) {
            fail("method called in wrong state");
        } catch (InterruptedException e) {
            fail("sleep() interrupted");
        } finally {
            releaseVisualizer();
        }
    }

    //Test case 2.1: test capture with listener
    public void test2_1ListenerCapture() throws Exception {
        if (!hasAudioOutput()) {
            Log.w(TAG,"AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }
        try {
            getVisualizer(0);
            synchronized(mLock) {
                mInitialized = false;
                createListenerLooper();
                waitForLooperInitialization_l();
            }
            mVisualizer.setEnabled(true);
            assertTrue("visualizer not enabled", mVisualizer.getEnabled());

            Thread.sleep(100);

            // check capture on silence
            synchronized(mLock) {
                mCaptureWaveform = true;
                int looperWaitCount = MAX_LOOPER_WAIT_COUNT;
                while ((mWaveform == null) && (looperWaitCount-- > 0)) {
                    try {
                        mLock.wait();
                    } catch(Exception e) {
                    }
                }
                mCaptureWaveform = false;
            }
            assertNotNull("waveform capture failed", mWaveform);
            int energy = computeEnergy(mWaveform, true);
            assertEquals("getWaveForm reports energy for silence",
                    0, energy);

            synchronized(mLock) {
                mCaptureFft = true;
                int looperWaitCount = MAX_LOOPER_WAIT_COUNT;
                while ((mFft == null) && (looperWaitCount-- > 0)) {
                    try {
                        mLock.wait();
                    } catch(Exception e) {
                    }
                }
                mCaptureFft = false;
            }
            assertNotNull("FFT capture failed", mFft);
            energy = computeEnergy(mFft, false);
            assertEquals("getFft reports energy for silence",
                    0, energy);

        } catch (IllegalStateException e) {
            fail("method called in wrong state");
        } catch (InterruptedException e) {
            fail("sleep() interrupted");
        } finally {
            terminateListenerLooper();
            releaseVisualizer();
        }
    }

    //-----------------------------------------------------------------
    // 3 - check measurement mode MEASUREMENT_MODE_NONE
    //----------------------------------

    //Test case 3.0: test setting NONE measurement mode
    public void test3_0MeasurementModeNone() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        try {
            getVisualizer(0);
            mVisualizer.setEnabled(true);
            assertTrue("visualizer not enabled", mVisualizer.getEnabled());
            Thread.sleep(100);

            int status = mVisualizer.setMeasurementMode(Visualizer.MEASUREMENT_MODE_NONE);
            assertEquals("setMeasurementMode for NONE doesn't report success",
                    Visualizer.SUCCESS, status);

            int mode = mVisualizer.getMeasurementMode();
            assertEquals("getMeasurementMode reports NONE",
                    Visualizer.MEASUREMENT_MODE_NONE, mode);

        } catch (IllegalStateException e) {
            fail("method called in wrong state");
        } catch (InterruptedException e) {
            fail("sleep() interrupted");
        } finally {
            releaseVisualizer();
        }
    }

    //-----------------------------------------------------------------
    // 4 - check measurement mode MEASUREMENT_MODE_PEAK_RMS
    //----------------------------------

    //Test case 4.0: test setting peak / RMS measurement mode
    public void test4_0MeasurementModePeakRms() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        try {
            getVisualizer(0);
            mVisualizer.setEnabled(true);
            assertTrue("visualizer not enabled", mVisualizer.getEnabled());
            Thread.sleep(100);

            int status = mVisualizer.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS);
            assertEquals("setMeasurementMode for PEAK_RMS doesn't report success",
                    Visualizer.SUCCESS, status);

            int mode = mVisualizer.getMeasurementMode();
            assertEquals("getMeasurementMode doesn't report PEAK_RMS",
                    Visualizer.MEASUREMENT_MODE_PEAK_RMS, mode);

        } catch (IllegalStateException e) {
            fail("method called in wrong state");
        } catch (InterruptedException e) {
            fail("sleep() interrupted");
        } finally {
            releaseVisualizer();
        }
    }

    //Test case 4.1: test measurement of peak / RMS
    public void test4_1MeasurePeakRms() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        AudioEffect vc = null;
        try {
            // this test will play a 1kHz sine wave with peaks at -40dB
            MediaPlayer mp = MediaPlayer.create(getContext(), R.raw.sine1khzm40db);
            final int EXPECTED_PEAK_MB = -4015;
            final int EXPECTED_RMS_MB =  -4300;
            final int MAX_MEASUREMENT_ERROR_MB = 2000;
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
            getVisualizer(mp.getAudioSessionId());
            mp.setLooping(true);
            mp.start();

            mVisualizer.setEnabled(true);
            assertTrue("visualizer not enabled", mVisualizer.getEnabled());
            Thread.sleep(100);
            int status = mVisualizer.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS);
            assertEquals("setMeasurementMode() for PEAK_RMS doesn't report success",
                    Visualizer.SUCCESS, status);
            // make sure we're playing long enough so the measurement is valid
            int currentPosition = mp.getCurrentPosition();
            final int maxTry = 100;
            int tryCount = 0;
            while (currentPosition < 200 && tryCount < maxTry) {
                Thread.sleep(50);
                currentPosition = mp.getCurrentPosition();
                tryCount++;
            }
            assertTrue("MediaPlayer not ready", tryCount < maxTry);

            MeasurementPeakRms measurement = new MeasurementPeakRms();
            status = mVisualizer.getMeasurementPeakRms(measurement);
            mp.stop();
            mp.release();
            am.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
            assertEquals("getMeasurementPeakRms() reports failure",
                    Visualizer.SUCCESS, status);
            Log.i("VisTest", "peak="+measurement.mPeak+"  rms="+measurement.mRms);
            int deltaPeak = Math.abs(measurement.mPeak - EXPECTED_PEAK_MB);
            int deltaRms =  Math.abs(measurement.mRms - EXPECTED_RMS_MB);
            assertTrue("peak deviation in mB=" + deltaPeak, deltaPeak < MAX_MEASUREMENT_ERROR_MB);
            assertTrue("RMS deviation in mB=" + deltaRms, deltaRms < MAX_MEASUREMENT_ERROR_MB);

        } catch (IllegalStateException e) {
            fail("method called in wrong state");
        } catch (InterruptedException e) {
            fail("sleep() interrupted");
        } finally {
            if (vc != null)
                vc.release();
            releaseVisualizer();
        }
    }

    //Test case 4.2: test measurement of peak / RMS in Long MP3
    public void test4_2MeasurePeakRmsLongMP3() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        AudioEffect vc = null;
        try {
            // this test will play a 1kHz sine wave with peaks at -40dB
            MediaPlayer mp = MediaPlayer.create(getContext(), R.raw.sine1khzs40dblong);
            final int EXPECTED_PEAK_MB = -4015;
            final int EXPECTED_RMS_MB =  -4300;
            final int MAX_MEASUREMENT_ERROR_MB = 2000;
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
            getVisualizer(mp.getAudioSessionId());
            mp.start();

            mVisualizer.setEnabled(true);
            assertTrue("visualizer not enabled", mVisualizer.getEnabled());
            Thread.sleep(100);
            int status = mVisualizer.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS);
            assertEquals("setMeasurementMode() for PEAK_RMS doesn't report success",
                    Visualizer.SUCCESS, status);
            // make sure we're playing long enough so the measurement is valid
            int currentPosition = mp.getCurrentPosition();
            final int maxTry = 100;
            int tryCount = 0;
            while (currentPosition < 400 && tryCount < maxTry) {
                Thread.sleep(50);
                currentPosition = mp.getCurrentPosition();
                tryCount++;
            }
            assertTrue("MediaPlayer not ready", tryCount < maxTry);

            MeasurementPeakRms measurement = new MeasurementPeakRms();
            status = mVisualizer.getMeasurementPeakRms(measurement);
            mp.stop();
            mp.release();
            am.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
            assertEquals("getMeasurementPeakRms() reports failure",
                    Visualizer.SUCCESS, status);
            Log.i("VisTest", "peak="+measurement.mPeak+"  rms="+measurement.mRms);
            int deltaPeak = Math.abs(measurement.mPeak - EXPECTED_PEAK_MB);
            int deltaRms =  Math.abs(measurement.mRms - EXPECTED_RMS_MB);
            assertTrue("peak deviation in mB=" + deltaPeak, deltaPeak < MAX_MEASUREMENT_ERROR_MB);
            assertTrue("RMS deviation in mB=" + deltaRms, deltaRms < MAX_MEASUREMENT_ERROR_MB);

        } catch (IllegalStateException e) {
            fail("method called in wrong state");
        } catch (InterruptedException e) {
            fail("sleep() interrupted");
        } finally {
            if (vc != null)
                vc.release();
            releaseVisualizer();
        }
    }

    //-----------------------------------------------------------------
    // private methods
    //----------------------------------

    private int computeEnergy(byte[] data, boolean pcm) {
        int energy = 0;
        if (data.length != 0) {
            if (pcm) {
                for (int i = 0; i < data.length; i++) {
                    int tmp = ((int)data[i] & 0xFF) - 128;
                    energy += tmp*tmp;
                }
            } else {
                energy = (int)data[0] * (int)data[0];
                for (int i = 2; i < data.length; i += 2) {
                    int real = (int)data[i];
                    int img = (int)data[i + 1];
                    energy += real * real + img * img;
                }
            }
        }
        return energy;
    }

    private void getVisualizer(int session) {
         if (mVisualizer == null || session != mSession) {
             if (session != mSession && mVisualizer != null) {
                 mVisualizer.release();
                 mVisualizer = null;
             }
             try {
                mVisualizer = new Visualizer(session);
                mSession = session;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "getVisualizer() Visualizer not found exception: "+e);
            } catch (UnsupportedOperationException e) {
                Log.e(TAG, "getVisualizer() Effect library not loaded exception: "+e);
            }
         }
         assertNotNull("could not create mVisualizer", mVisualizer);
    }

    private void releaseVisualizer() {
        if (mVisualizer != null) {
            mVisualizer.release();
            mVisualizer = null;
        }
    }

    private void waitForLooperInitialization_l() {
        int looperWaitCount = MAX_LOOPER_WAIT_COUNT;
        while (!mInitialized && (looperWaitCount-- > 0)) {
            try {
                mLock.wait();
            } catch(Exception e) {
            }
        }
        assertTrue(mInitialized);
    }

    private void createListenerLooper() {
        mListenerThread = new Thread() {
            @Override
            public void run() {
                // Set up a looper to be used by mEffect.
                Looper.prepare();

                // Save the looper so that we can terminate this thread
                // after we are done with it.
                mLooper = Looper.myLooper();

                synchronized(mLock) {
                    if (mVisualizer != null) {
                        mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                            public void onWaveFormDataCapture(
                                    Visualizer visualizer, byte[] waveform, int samplingRate) {
                                synchronized(mLock) {
                                    if (visualizer == mVisualizer) {
                                        if (mCaptureWaveform) {
                                            mWaveform = waveform;
                                            mLock.notify();
                                        }
                                    }
                                }
                            }

                            public void onFftDataCapture(
                                    Visualizer visualizer, byte[] fft, int samplingRate) {
                                synchronized(mLock) {
                                    Log.e(TAG, "onFftDataCapture 2 mCaptureFft: "+mCaptureFft);
                                    if (visualizer == mVisualizer) {
                                        if (mCaptureFft) {
                                            mFft = fft;
                                            mLock.notify();
                                        }
                                    }
                                }
                            }
                        },
                        10000,
                        true,
                        true);
                    }
                    mInitialized = true;
                    mLock.notify();
                }
                Looper.loop();  // Blocks forever until Looper.quit() is called.
            }
        };
        mListenerThread.start();
    }
    /*
     * Terminates the listener looper thread.
     */
    private void terminateListenerLooper() {
        if (mListenerThread != null) {
            if (mLooper != null) {
                mLooper.quit();
                mLooper = null;
            }
            try {
                mListenerThread.join();
            } catch(InterruptedException e) {
            }
            mListenerThread = null;
        }
    }
}
