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

package android.permission2.cts;

import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Verify the capture system video output permission requirements.
 */
public class NoCaptureAudioOutputPermissionTest extends AndroidTestCase {
    /**
     * Verify that the AudioRecord constructor fails to create a recording object
     * when the app does not have permission to capture audio output.
     * For the purposes of this test, the app must already have the normal audio
     * record permission, just not the capture audio output permission.
     * <p>Requires permission:
     *    {@link android.Manifest.permission#RECORD_AUDIO} and
     *    {@link android.Manifest.permission#CAPTURE_VIDEO_OUTPUT}.
     */
    @SmallTest
    public void testCreateAudioRecord() {
        int bufferSize = AudioRecord.getMinBufferSize(44100,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);

        if (bufferSize <= 0)
        {
            // getMinBufferSize() returns an invalid buffer size.
            // That could be because there is no microphone.  In that case,
            // use this buffer size to test AudioRecord creation.
            PackageManager packageManager = mContext.getPackageManager();
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
                bufferSize = 44100;
            }
        }

        // The attempt to create the AudioRecord object succeeds even if the
        // app does not have permission, but the object is not usable.
        // The API should probably throw SecurityException but it was not originally
        // designed to do that and it's not clear we can change it now.
        AudioRecord record = new AudioRecord(AudioSource.REMOTE_SUBMIX, 44100,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        try {
            assertTrue("AudioRecord state should not be INITIALIZED because the application"
                    + "does not have permission to access the remote submix source",
                    record.getState() != AudioRecord.STATE_INITIALIZED);
        } finally {
            record.release();
        }
    }
}
