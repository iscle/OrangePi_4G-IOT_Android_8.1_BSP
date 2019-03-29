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

import android.content.Context;
import android.content.pm.PackageManager;

import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRouting;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import android.os.Handler;
import android.os.Looper;

import android.test.AndroidTestCase;

import android.util.Log;

import java.lang.Runnable;

/**
 * AudioTrack / AudioRecord preferred device and routing listener tests.
 * The routing tests are mostly here to exercise the routing code, as an actual test would require
 * adding / removing an audio device for the listeners to be called.
 * The routing listener code is designed to run for two versions of the routing code:
 *  - the deprecated AudioTrack.OnRoutingChangedListener and AudioRecord.OnRoutingChangedListener
 *  - the N AudioRouting.OnRoutingChangedListener
 */
public class RoutingTest extends AndroidTestCase {
    private static final String TAG = "RoutingTest";

    private AudioManager mAudioManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // get the AudioManager
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        assertNotNull(mAudioManager);
    }

    private AudioTrack allocAudioTrack() {
        int bufferSize =
                AudioTrack.getMinBufferSize(
                    41000,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack audioTrack =
            new AudioTrack(
                AudioManager.STREAM_MUSIC,
                41000,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM);
        return audioTrack;
    }

    public void test_audioTrack_preferredDevice() {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
            // Can't do it so skip this test
            return;
        }

        AudioTrack audioTrack = allocAudioTrack();
        assertNotNull(audioTrack);

        // None selected (new AudioTrack), so check for default
        assertNull(audioTrack.getPreferredDevice());

        // resets to default
        assertTrue(audioTrack.setPreferredDevice(null));

        // test each device
        AudioDeviceInfo[] deviceList = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (int index = 0; index < deviceList.length; index++) {
            assertTrue(audioTrack.setPreferredDevice(deviceList[index]));
            assertTrue(audioTrack.getPreferredDevice() == deviceList[index]);
        }

        // Check defaults again
        assertTrue(audioTrack.setPreferredDevice(null));
        assertNull(audioTrack.getPreferredDevice());

        audioTrack.release();
    }

    /*
     * tests if the Looper for the current thread has been prepared,
     * If not, it makes one, prepares it and returns it.
     * If this returns non-null, the caller is reponsible for calling quit()
     * on the returned Looper.
     */
    private Looper prepareIfNeededLooper() {
        // non-null Handler
        Looper myLooper = null;
        if (Looper.myLooper() == null) {
            Looper.prepare();
            myLooper = Looper.myLooper();
            assertNotNull(myLooper);
        }
        return myLooper;
    }

    private class AudioTrackRoutingListener implements AudioTrack.OnRoutingChangedListener,
            AudioRouting.OnRoutingChangedListener
    {
        public void onRoutingChanged(AudioTrack audioTrack) {}
        public void onRoutingChanged(AudioRouting audioRouting) {}
    }


    public void test_audioTrack_RoutingListener() {
        test_audioTrack_RoutingListener(false /*usesAudioRouting*/);
    }

    public void test_audioTrack_audioRouting_RoutingListener() {
        test_audioTrack_RoutingListener(true /*usesAudioRouting*/);
    }

    private void test_audioTrack_RoutingListener(boolean usesAudioRouting) {
        AudioTrack audioTrack = allocAudioTrack();

        // null listener
        if (usesAudioRouting) {
            audioTrack.addOnRoutingChangedListener(
                    (AudioRouting.OnRoutingChangedListener) null, null);
        } else {
            audioTrack.addOnRoutingChangedListener(
                    (AudioTrack.OnRoutingChangedListener) null, null);
        }

        AudioTrackRoutingListener listener = new AudioTrackRoutingListener();
        AudioTrackRoutingListener someOtherListener = new AudioTrackRoutingListener();

        // add a listener
        if (usesAudioRouting) {
            audioTrack.addOnRoutingChangedListener(
                    (AudioRouting.OnRoutingChangedListener) listener, null);
        } else {
            audioTrack.addOnRoutingChangedListener(listener, null);
        }

        // remove listeners
        if (usesAudioRouting) {
            // remove a listener we didn't add
            audioTrack.removeOnRoutingChangedListener(
                    (AudioRouting.OnRoutingChangedListener) someOtherListener);
            // remove a valid listener
            audioTrack.removeOnRoutingChangedListener(
                    (AudioRouting.OnRoutingChangedListener) listener);
        } else {
            // remove a listener we didn't add
            audioTrack.removeOnRoutingChangedListener(
                    (AudioTrack.OnRoutingChangedListener) someOtherListener);
            // remove a valid listener
            audioTrack.removeOnRoutingChangedListener(
                    (AudioTrack.OnRoutingChangedListener) listener);
        }

        Looper myLooper = prepareIfNeededLooper();

        if (usesAudioRouting) {
            audioTrack.addOnRoutingChangedListener(
                    (AudioRouting.OnRoutingChangedListener) listener, new Handler());
            audioTrack.removeOnRoutingChangedListener(
                    (AudioRouting.OnRoutingChangedListener) listener);
        } else {
            audioTrack.addOnRoutingChangedListener(
                    (AudioTrack.OnRoutingChangedListener) listener, new Handler());
            audioTrack.removeOnRoutingChangedListener(
                    (AudioTrack.OnRoutingChangedListener) listener);
        }

        audioTrack.release();
        if (myLooper != null) {
            myLooper.quit();
        }
   }

    private AudioRecord allocAudioRecord() {
        int bufferSize =
                AudioRecord.getMinBufferSize(
                    41000,
                    AudioFormat.CHANNEL_OUT_DEFAULT,
                    AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord audioRecord =
            new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                41000, AudioFormat.CHANNEL_OUT_DEFAULT,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);
        return audioRecord;
    }

    private class AudioRecordRoutingListener implements AudioRecord.OnRoutingChangedListener,
            AudioRouting.OnRoutingChangedListener
    {
        public void onRoutingChanged(AudioRecord audioRecord) {}
        public void onRoutingChanged(AudioRouting audioRouting) {}
    }

    public void test_audioRecord_RoutingListener() {
        test_audioRecord_RoutingListener(false /*usesAudioRouting*/);
    }

    public void test_audioRecord_audioRouting_RoutingListener() {
        test_audioRecord_RoutingListener(true /*usesAudioRouting*/);
    }

    private void test_audioRecord_RoutingListener(boolean usesAudioRouting) {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            // Can't do it so skip this test
            return;
        }
        AudioRecord audioRecord = allocAudioRecord();

        // null listener
        if (usesAudioRouting) {
            audioRecord.addOnRoutingChangedListener(
                    (AudioRouting.OnRoutingChangedListener) null, null);
        } else {
            audioRecord.addOnRoutingChangedListener(
                    (AudioRecord.OnRoutingChangedListener) null, null);
        }

        AudioRecordRoutingListener listener = new AudioRecordRoutingListener();
        AudioRecordRoutingListener someOtherListener = new AudioRecordRoutingListener();

        // add a listener
        if (usesAudioRouting) {
            audioRecord.addOnRoutingChangedListener(
                    (AudioRouting.OnRoutingChangedListener) listener, null);
        } else {
            audioRecord.addOnRoutingChangedListener(
                    (AudioRecord.OnRoutingChangedListener) listener, null);
        }

        // remove listeners
        if (usesAudioRouting) {
            // remove a listener we didn't add
            audioRecord.removeOnRoutingChangedListener(
                    (AudioRouting.OnRoutingChangedListener) someOtherListener);
            // remove a valid listener
            audioRecord.removeOnRoutingChangedListener(
                    (AudioRouting.OnRoutingChangedListener) listener);
        } else {
            // remove a listener we didn't add
            audioRecord.removeOnRoutingChangedListener(
                    (AudioRecord.OnRoutingChangedListener) someOtherListener);
            // remove a valid listener
            audioRecord.removeOnRoutingChangedListener(
                    (AudioRecord.OnRoutingChangedListener) listener);
        }

        Looper myLooper = prepareIfNeededLooper();
        if (usesAudioRouting) {
            audioRecord.addOnRoutingChangedListener(
                    (AudioRouting.OnRoutingChangedListener) listener, new Handler());
            audioRecord.removeOnRoutingChangedListener(
                    (AudioRouting.OnRoutingChangedListener) listener);
        } else {
            audioRecord.addOnRoutingChangedListener(
                    (AudioRecord.OnRoutingChangedListener) listener, new Handler());
            audioRecord.removeOnRoutingChangedListener(
                    (AudioRecord.OnRoutingChangedListener) listener);
        }

        audioRecord.release();
        if (myLooper != null) {
            myLooper.quit();
        }
    }

    public void test_audioRecord_preferredDevice() {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            // Can't do it so skip this test
            return;
        }

        AudioRecord audioRecord = allocAudioRecord();
        assertNotNull(audioRecord);

        // None selected (new AudioRecord), so check for default
        assertNull(audioRecord.getPreferredDevice());

        // resets to default
        assertTrue(audioRecord.setPreferredDevice(null));

        // test each device
        AudioDeviceInfo[] deviceList = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (int index = 0; index < deviceList.length; index++) {
            assertTrue(audioRecord.setPreferredDevice(deviceList[index]));
            assertTrue(audioRecord.getPreferredDevice() == deviceList[index]);
        }

        // Check defaults again
        assertTrue(audioRecord.setPreferredDevice(null));
        assertNull(audioRecord.getPreferredDevice());

        audioRecord.release();
    }

    private class AudioTrackFiller implements Runnable {
        AudioTrack mAudioTrack;
        int mBufferSize;

        boolean mPlaying;

        short[] mAudioData;

        public AudioTrackFiller(AudioTrack audioTrack, int bufferSize) {
            mAudioTrack = audioTrack;
            mBufferSize = bufferSize;
            mPlaying = false;

            // setup audio data (silence will suffice)
            mAudioData = new short[mBufferSize];
            for (int index = 0; index < mBufferSize; index++) {
                mAudioData[index] = 0;
            }
        }

        public void start() { mPlaying = true; }
        public void stop() { mPlaying = false; }

        @Override
        public void run() {
            while (mAudioTrack != null && mPlaying) {
                mAudioTrack.write(mAudioData, 0, mBufferSize);
            }
        }
    }

    public void test_audioTrack_getRoutedDevice() {
        if (!DeviceUtils.hasOutputDevice(mAudioManager)) {
            Log.i(TAG, "No output devices. Test skipped");
            return; // nothing to test here
        }

        int bufferSize =
                AudioTrack.getMinBufferSize(
                    41000,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack audioTrack =
            new AudioTrack(
                AudioManager.STREAM_MUSIC,
                41000,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM);

        AudioTrackFiller filler = new AudioTrackFiller(audioTrack, bufferSize);
        filler.start();

        audioTrack.play();

        Thread fillerThread = new Thread(filler);
        fillerThread.start();

        try { Thread.sleep(1000); } catch (InterruptedException ex) {}

        // No explicit route
        AudioDeviceInfo routedDevice = audioTrack.getRoutedDevice();
        assertNotNull(routedDevice); // we probably can't say anything more than this

        filler.stop();
        audioTrack.stop();
        audioTrack.release();
    }

    private class AudioRecordPuller implements Runnable {
        AudioRecord mAudioRecord;
        int mBufferSize;

        boolean mRecording;

        short[] mAudioData;

        public AudioRecordPuller(AudioRecord audioRecord, int bufferSize) {
            mAudioRecord = audioRecord;
            mBufferSize = bufferSize;
            mRecording = false;
        }

        public void start() { mRecording = true; }
        public void stop() { mRecording = false; }

        @Override
        public void run() {
            while (mAudioRecord != null && mRecording) {
                mAudioRecord.read(mAudioData, 0, mBufferSize);
           }
        }
    }

    public void test_audioRecord_getRoutedDevice() {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            return;
        }

        if (!DeviceUtils.hasInputDevice(mAudioManager)) {
            Log.i(TAG, "No input devices. Test skipped");
            return; // nothing to test here
        }

        int bufferSize =
                AudioRecord.getMinBufferSize(
                    41000,
                    AudioFormat.CHANNEL_OUT_DEFAULT,
                    AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord audioRecord =
            new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                41000, AudioFormat.CHANNEL_OUT_DEFAULT,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        AudioRecordPuller puller = new AudioRecordPuller(audioRecord, bufferSize);
        puller.start();

        audioRecord.startRecording();

        Thread pullerThread = new Thread(puller);
        pullerThread.start();

        try { Thread.sleep(1000); } catch (InterruptedException ex) {}

        // No explicit route
        AudioDeviceInfo routedDevice = audioRecord.getRoutedDevice();
        assertNotNull(routedDevice); // we probably can't say anything more than this

        puller.stop();
        audioRecord.stop();
        audioRecord.release();
    }
}
