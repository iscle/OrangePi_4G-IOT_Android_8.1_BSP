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

import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import com.google.android.collect.Lists;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

public class Utils {
    private static final String TAG = "CtsMediaTestUtil";
    private static final int TEST_TIMING_TOLERANCE_MS = 50;

    public static void enableAppOps(String packageName, String operation,
            Instrumentation instrumentation) {
        setAppOps(packageName, operation, instrumentation, true);
    }

    public static void disableAppOps(String packageName, String operation,
            Instrumentation instrumentation) {
        setAppOps(packageName, operation, instrumentation, false);
    }

    public static String convertStreamToString(InputStream is) {
        try (Scanner scanner = new Scanner(is).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private static void setAppOps(String packageName, String operation,
            Instrumentation instrumentation, boolean enable) {
        StringBuilder cmd = new StringBuilder();
        cmd.append("appops set ");
        cmd.append(packageName);
        cmd.append(" ");
        cmd.append(operation);
        cmd.append(enable ? " allow" : " deny");
        instrumentation.getUiAutomation().executeShellCommand(cmd.toString());

        StringBuilder query = new StringBuilder();
        query.append("appops get ");
        query.append(packageName);
        query.append(" ");
        query.append(operation);
        String queryStr = query.toString();

        String expectedResult = enable ? "allow" : "deny";
        String result = "";
        while(!result.contains(expectedResult)) {
            ParcelFileDescriptor pfd = instrumentation.getUiAutomation().executeShellCommand(
                                                            queryStr);
            InputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
            result = convertStreamToString(inputStream);
        }
    }

    protected static void toggleNotificationPolicyAccess(String packageName,
            Instrumentation instrumentation, boolean on) throws IOException {

        String command = " cmd notification " + (on ? "allow_dnd " : "disallow_dnd ") + packageName;

        // Get permission to enable accessibility
        UiAutomation uiAutomation = instrumentation.getUiAutomation();
        // Execute command
        try (ParcelFileDescriptor fd = uiAutomation.executeShellCommand(command)) {
            Assert.assertNotNull("Failed to execute shell command: " + command, fd);
            // Wait for the command to finish by reading until EOF
            try (InputStream in = new FileInputStream(fd.getFileDescriptor())) {
                byte[] buffer = new byte[4096];
                while (in.read(buffer) > 0) {}
            } catch (IOException e) {
                throw new IOException("Could not read stdout of command: " + command, e);
            }
        } finally {
            uiAutomation.destroy();
        }

        NotificationManager nm = (NotificationManager) instrumentation.getContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        Assert.assertEquals("Wrote setting should be the same as the read one", on,
                nm.isNotificationPolicyAccessGranted());
    }

    /**
     * Assert that a media playback is started and an active {@link AudioPlaybackConfiguration}
     * is created once. The playback will be stopped immediately after that.
     * <p>For a media session to receive media button events, an actual playback is needed.
     */
    static void assertMediaPlaybackStarted(Context context) {
        final AudioManager am = new AudioManager(context);
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final TestAudioPlaybackCallback callback = new TestAudioPlaybackCallback();
        MediaPlayer mediaPlayer = null;

        try {
            final int activeConfigSizeBeforeStart = am.getActivePlaybackConfigurations().size();
            final Handler handler = new Handler(handlerThread.getLooper());

            am.registerAudioPlaybackCallback(callback, handler);
            mediaPlayer = MediaPlayer.create(context, R.raw.sine1khzs40dblong);
            mediaPlayer.start();
            if (!callback.mCountDownLatch.await(TEST_TIMING_TOLERANCE_MS, TimeUnit.MILLISECONDS)
                    || callback.mActiveConfigSize != activeConfigSizeBeforeStart + 1) {
                Assert.fail("Failed to create an active AudioPlaybackConfiguration");
            }
        } catch (InterruptedException e) {
            Assert.fail("Failed to create an active AudioPlaybackConfiguration");
        } finally {
            am.unregisterAudioPlaybackCallback(callback);
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            handlerThread.quitSafely();
        }
    }

    private static class TestAudioPlaybackCallback extends AudioManager.AudioPlaybackCallback {
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);
        private int mActiveConfigSize;

        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            // For non-framework apps, only anonymized active AudioPlaybackCallbacks will be
            // notified.
            mActiveConfigSize = configs.size();
            mCountDownLatch.countDown();
        }
    }
}
