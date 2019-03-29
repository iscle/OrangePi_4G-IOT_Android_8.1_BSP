/*
 * Copyright (C) 2012 The Android Open Source Project
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
package android.mediastress.cts;

import android.app.Instrumentation;
import android.content.Intent;
import android.media.MediaFormat;
import android.media.MediaRecorder.AudioEncoder;
import android.media.MediaRecorder.VideoEncoder;
import android.os.Environment;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import com.android.compatibility.common.util.MediaUtils;

import junit.framework.Assert;

public class NativeMediaTest extends ActivityInstrumentationTestCase2<NativeMediaActivity> {
    private static final String TAG = "NativeMediaTest";
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int VIDEO_CODEC = VideoEncoder.H264;
    private static final int NUMBER_PLAY_PAUSE_REPEATITIONS = 10;
    private static final long PLAY_WAIT_TIME_MS = 4000;

    public NativeMediaTest() {
        super(NativeMediaActivity.class);
    }

    public void test1080pPlay() throws InterruptedException {
        runPlayTest(1920, 1080);
    }

    public void test720pPlay() throws InterruptedException {
        runPlayTest(1280, 720);
    }

    public void test480pPlay() throws InterruptedException {
        runPlayTest(720, 480);
    }

    public void testDefaultPlay() throws InterruptedException {
        runPlayTest(480, 360);
    }

    private void runPlayTest(int width, int height) throws InterruptedException {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        // Don't run the test if the codec isn't supported.
        if (!MediaUtils.canDecode(format)) {
            return; // skip
        }

        Intent intent = new Intent();
        intent.putExtra(NativeMediaActivity.EXTRA_VIDEO_HEIGHT, height);
        setActivityIntent(intent);
        final NativeMediaActivity activity = getActivity();
        final Instrumentation instrumentation = getInstrumentation();
        waitForNativeMediaLifeCycle(activity, true);
        Thread.sleep(PLAY_WAIT_TIME_MS); // let it play for some time
        for (int i = 0; i < NUMBER_PLAY_PAUSE_REPEATITIONS; i++) {
            instrumentation.runOnMainSync(() -> {
                instrumentation.callActivityOnPause(activity);
            });
            instrumentation.waitForIdleSync();
            waitForNativeMediaLifeCycle(activity, false);
            instrumentation.runOnMainSync(() -> {
                instrumentation.callActivityOnResume(activity);
            });
            waitForNativeMediaLifeCycle(activity, true);
            Thread.sleep(PLAY_WAIT_TIME_MS); // let it play for some time
        }
    }

    /**
     * wait until life cycle change and checks if the current status is in line with expectation
     * @param activity
     * @param expectAlive expected status, true if it should be alive.
     * @throws InterruptedException
     */
    private void waitForNativeMediaLifeCycle(NativeMediaActivity activity, boolean expectAlive)
            throws InterruptedException {
        Boolean status = activity.waitForNativeMediaLifeCycle();
        Assert.assertNotNull(status); // null means time-out
        Assert.assertEquals(expectAlive, status.booleanValue());
    }
}
