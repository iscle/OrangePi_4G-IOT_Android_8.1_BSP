/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.compatibility.common.util.PollingCheck;

import java.util.ArrayList;
import java.util.List;

public class AudioRecord_BufferSizeTest extends AndroidTestCase {

    private static final String TAG = AudioRecord_BufferSizeTest.class.getSimpleName();
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private static final int[] SAMPLE_RATES_IN_HZ = new int[] {
        8000,
        11025,
        16000,
        44100,
    };

    private AudioRecord mAudioRecord;

    public void testGetMinBufferSize() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        List<Integer> failedSampleRates = new ArrayList<Integer>();
        for (int i = 0; i < SAMPLE_RATES_IN_HZ.length; i++) {
            try {
                record(SAMPLE_RATES_IN_HZ[i]);
            } catch (Throwable e) {
                Log.e(TAG, "Sample rate: " + SAMPLE_RATES_IN_HZ[i], e);
                failedSampleRates.add(SAMPLE_RATES_IN_HZ[i]);
                if (mAudioRecord != null) {
                    // clean up.  AudioRecords are in scarce supply.
                    mAudioRecord.release();
                    mAudioRecord = null;
                }
            }
        }
        assertTrue("Failed sample rates: " + failedSampleRates + " See log for more details.",
                failedSampleRates.isEmpty());
    }

    private void record(int sampleRateInHz) {
        int bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, CHANNEL_CONFIG, AUDIO_FORMAT);
        assertTrue(bufferSize > 0);

        createAudioRecord(sampleRateInHz, bufferSize);
        // RecordingState changes are reflected synchronously (no need to poll)
        assertEquals(AudioRecord.RECORDSTATE_STOPPED, mAudioRecord.getRecordingState());

        mAudioRecord.startRecording();
        assertEquals(AudioRecord.RECORDSTATE_RECORDING, mAudioRecord.getRecordingState());

        // it is preferred to use a short array to read AudioFormat.ENCODING_PCM_16BIT data
        // but it's ok to read using using a byte array.  16 bit PCM data will be
        // stored as two bytes, native endian.
        byte[] buffer = new byte[bufferSize];
        assertTrue(mAudioRecord.read(buffer, 0, bufferSize) > 0);

        mAudioRecord.stop();
        assertEquals(AudioRecord.RECORDSTATE_STOPPED, mAudioRecord.getRecordingState());

        mAudioRecord.release();
        mAudioRecord = null;
    }

    private void createAudioRecord(final int sampleRateInHz, final int bufferSize) {
        mAudioRecord = new AudioRecord(AudioSource.DEFAULT, sampleRateInHz,
                CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
        assertNotNull(mAudioRecord);
    }

    private boolean hasMicrophone() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }
}
