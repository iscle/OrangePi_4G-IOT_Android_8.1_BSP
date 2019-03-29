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

import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;

import com.android.compatibility.common.util.CtsAndroidTestCase;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AudioRecordingConfigurationTest extends CtsAndroidTestCase {
    private static final String TAG = "AudioRecordingConfigurationTest";

    private static final int TEST_SAMPLE_RATE = 16000;
    private static final int TEST_AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;

    private static final int TEST_TIMING_TOLERANCE_MS = 70;
    private static final long SLEEP_AFTER_STOP_FOR_INACTIVITY_MS = 1000;

    private AudioRecord mAudioRecord;
    private Looper mLooper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!hasMicrophone()) {
            return;
        }

        /*
         * InstrumentationTestRunner.onStart() calls Looper.prepare(), which creates a looper
         * for the current thread. However, since we don't actually call loop() in the test,
         * any messages queued with that looper will never be consumed. Therefore, we must
         * create the instance in another thread, either without a looper, so the main looper is
         * used, or with an active looper.
         */
        Thread t = new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mLooper = Looper.myLooper();
                synchronized(this) {
                    mAudioRecord = new AudioRecord.Builder()
                                     .setAudioSource(TEST_AUDIO_SOURCE)
                                     .setAudioFormat(new AudioFormat.Builder()
                                             .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                             .setSampleRate(TEST_SAMPLE_RATE)
                                             .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                             .build())
                                     .build();
                    this.notify();
                }
                Looper.loop();
            }
        };
        synchronized(t) {
            t.start(); // will block until we wait
            t.wait();
        }
        assertNotNull(mAudioRecord);
        assertNotNull(mLooper);
    }

    @Override
    protected void tearDown() throws Exception {
        if (hasMicrophone()) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mLooper.quit();
            Thread.sleep(SLEEP_AFTER_STOP_FOR_INACTIVITY_MS);
        }
        super.tearDown();
    }

    // start a recording and verify it is seen as an active recording
    public void testAudioManagerGetActiveRecordConfigurations() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        List<AudioRecordingConfiguration> configs = am.getActiveRecordingConfigurations();
        assertNotNull("Invalid null array of record configurations before recording", configs);

        assertEquals(AudioRecord.STATE_INITIALIZED, mAudioRecord.getState());
        mAudioRecord.startRecording();
        assertEquals(AudioRecord.RECORDSTATE_RECORDING, mAudioRecord.getRecordingState());
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);

        // recording is active, verify there is an active record configuration
        configs = am.getActiveRecordingConfigurations();
        assertNotNull("Invalid null array of record configurations during recording", configs);
        assertTrue("no active record configurations (empty array) during recording",
                configs.size() > 0);
        final int nbConfigsDuringRecording = configs.size();

        // verify our recording shows as one of the recording configs
        assertTrue("Test source/session not amongst active record configurations",
                verifyAudioConfig(TEST_AUDIO_SOURCE, mAudioRecord.getAudioSessionId(),
                        mAudioRecord.getFormat(), mAudioRecord.getRoutedDevice(), configs));

        // testing public API here: verify no system-privileged info is exposed through reflection
        verifyPrivilegedInfoIsSafe(configs.get(0));

        // stopping recording: verify there are less active record configurations
        mAudioRecord.stop();
        Thread.sleep(SLEEP_AFTER_STOP_FOR_INACTIVITY_MS);
        configs = am.getActiveRecordingConfigurations();
        assertEquals("Unexpected number of recording configs after stop",
                configs.size(), 0);
    }

    public void testCallback() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        doCallbackTest(false /* no custom Handler for callback */);
    }

    public void testCallbackHandler() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        doCallbackTest(true /* use custom Handler for callback */);
    }

    private void doCallbackTest(boolean useHandlerInCallback) throws Exception {
        final Handler h;
        if (useHandlerInCallback) {
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            h = new Handler(handlerThread.getLooper());
        } else {
            h = null;
        }
        try {
            AudioManager am = new AudioManager(getContext());
            assertNotNull("Could not create AudioManager", am);

            MyAudioRecordingCallback callback = new MyAudioRecordingCallback(
                    mAudioRecord.getAudioSessionId(), TEST_AUDIO_SOURCE);
            am.registerAudioRecordingCallback(callback, h /*handler*/);

            assertEquals(AudioRecord.STATE_INITIALIZED, mAudioRecord.getState());
            mAudioRecord.startRecording();
            assertEquals(AudioRecord.RECORDSTATE_RECORDING, mAudioRecord.getRecordingState());
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);

            assertTrue("AudioRecordingCallback not called after start", callback.mCalled);
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);

            final AudioDeviceInfo testDevice = mAudioRecord.getRoutedDevice();
            assertTrue("AudioRecord null routed device after start", testDevice != null);
            final boolean match = verifyAudioConfig(mAudioRecord.getAudioSource(),
                    mAudioRecord.getAudioSessionId(), mAudioRecord.getFormat(),
                    testDevice, callback.mConfigs);
            assertTrue("Expected record configuration was not found", match);

            // testing public API here: verify no system-privileged info is exposed through
            // reflection
            verifyPrivilegedInfoIsSafe(callback.mConfigs.get(0));

            // stopping recording: callback is called with no match
            callback.reset();
            mAudioRecord.stop();
            Thread.sleep(SLEEP_AFTER_STOP_FOR_INACTIVITY_MS);
            assertTrue("AudioRecordingCallback not called after stop", callback.mCalled);
            assertEquals("Should not have found record configurations", callback.mConfigs.size(),
                    0);

            // unregister callback and start recording again
            am.unregisterAudioRecordingCallback(callback);
            callback.reset();
            mAudioRecord.startRecording();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);
            assertFalse("Unregistered callback was called", callback.mCalled);

            // just call the callback once directly so it's marked as tested
            final AudioManager.AudioRecordingCallback arc =
                    (AudioManager.AudioRecordingCallback) callback;
            arc.onRecordingConfigChanged(new ArrayList<AudioRecordingConfiguration>());
        } finally {
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    public void testParcel() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        assertEquals(AudioRecord.STATE_INITIALIZED, mAudioRecord.getState());
        mAudioRecord.startRecording();
        assertEquals(AudioRecord.RECORDSTATE_RECORDING, mAudioRecord.getRecordingState());
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);

        List<AudioRecordingConfiguration> configs = am.getActiveRecordingConfigurations();
        assertTrue("Empty array of record configs during recording", configs.size() > 0);
        assertEquals(0, configs.get(0).describeContents());

        // marshall a AudioRecordingConfiguration and compare to unmarshalled
        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();

        configs.get(0).writeToParcel(srcParcel, 0 /*no public flags for marshalling*/);
        final byte[] mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final AudioRecordingConfiguration unmarshalledConf =
                AudioRecordingConfiguration.CREATOR.createFromParcel(dstParcel);

        assertNotNull("Failure to unmarshall AudioRecordingConfiguration", unmarshalledConf);
        assertEquals("Source and destination AudioRecordingConfiguration not equal",
                configs.get(0), unmarshalledConf);
    }

    class MyAudioRecordingCallback extends AudioManager.AudioRecordingCallback {
        boolean mCalled = false;
        List<AudioRecordingConfiguration> mConfigs;
        final AudioManager mAM;
        final int mTestSource;
        final int mTestSession;

        void reset() {
            mCalled = false;
            mConfigs = null;
        }

        MyAudioRecordingCallback(int session, int source) {
            mAM = new AudioManager(getContext());
            mTestSource = source;
            mTestSession = session;
        }

        @Override
        public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
            mCalled = true;
            mConfigs = configs;
        }
    }

    private static boolean deviceMatch(AudioDeviceInfo devJoe, AudioDeviceInfo devJeff) {
        return ((devJoe.getId() == devJeff.getId()
                && (devJoe.getAddress() == devJeff.getAddress())
                && (devJoe.getType() == devJeff.getType())));
    }

    private static boolean verifyAudioConfig(int source, int session, AudioFormat format,
            AudioDeviceInfo device, List<AudioRecordingConfiguration> configs) {
        final Iterator<AudioRecordingConfiguration> confIt = configs.iterator();
        while (confIt.hasNext()) {
            final AudioRecordingConfiguration config = confIt.next();
            final AudioDeviceInfo configDevice = config.getAudioDevice();
            assertTrue("Current recording config has null device", configDevice != null);
            if ((config.getClientAudioSource() == source)
                    && (config.getClientAudioSessionId() == session)
                    // test the client format matches that requested (same as the AudioRecord's)
                    && (config.getClientFormat().getEncoding() == format.getEncoding())
                    && (config.getClientFormat().getSampleRate() == format.getSampleRate())
                    && (config.getClientFormat().getChannelMask() == format.getChannelMask())
                    && (config.getClientFormat().getChannelIndexMask() ==
                            format.getChannelIndexMask())
                    // test the device format is configured
                    && (config.getFormat().getEncoding() != AudioFormat.ENCODING_INVALID)
                    && (config.getFormat().getSampleRate() > 0)
                    //  for the channel mask, either the position or index-based value must be valid
                    && ((config.getFormat().getChannelMask() != AudioFormat.CHANNEL_INVALID)
                            || (config.getFormat().getChannelIndexMask() !=
                                    AudioFormat.CHANNEL_INVALID))
                    && deviceMatch(device, configDevice)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMicrophone() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }

    private static void verifyPrivilegedInfoIsSafe(AudioRecordingConfiguration config) {
        // verify "privileged" fields aren't available through reflection
        final Class<?> confClass = config.getClass();
        try {
            final Method getClientUidMethod = confClass.getDeclaredMethod("getClientUid");
            final Method getClientPackageName = confClass.getDeclaredMethod("getClientPackageName");
            Integer uid = (Integer) getClientUidMethod.invoke(config, null);
            assertEquals("client uid isn't protected", -1 /*expected*/, uid.intValue());
            String name = (String) getClientPackageName.invoke(config, null);
            assertNotNull("client package name is null", name);
            assertEquals("client package name isn't protected", 0 /*expected*/, name.length());
        } catch (Exception e) {
            fail("Exception thrown during reflection on config privileged fields" + e);
        }
    }
}
