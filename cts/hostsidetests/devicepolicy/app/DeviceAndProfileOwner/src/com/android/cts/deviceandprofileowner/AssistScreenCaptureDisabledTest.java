package com.android.cts.deviceandprofileowner;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * Testing
 * {@link android.app.admin.DevicePolicyManager#setScreenCaptureDisabled(ComponentName, boolean)}
 * is enforced in {@link android.service.voice.VoiceInteractionSession#onHandleScreenshot(Bitmap)}.
 */
@RunWith(AndroidJUnit4.class)
public class AssistScreenCaptureDisabledTest {
    private static final String TAG = "DevicePolicyAssistTest";
    
    private static final String ACTION_CHECK_IS_READY = "voice_interaction_service.is_ready";
    private static final String ACTION_SHOW_SESSION = "voice_interaction_service.show_session";
    private static final String ACTION_HANDLE_SCREENSHOT =
            "voice_interaction_session_service.handle_screenshot";
    private static final String KEY_HAS_SCREENSHOT = "has_screenshot";
    private static final String ASSIST_PACKAGE = "com.android.cts.devicepolicy.assistapp";

    private static final int MAX_ATTEMPTS_COUNT = 5;
    private static final int WAIT_IN_SECOND = 5;
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getContext();
    }

    @Test
    public void testScreenCaptureImpossible_assist() throws Exception {
        assertScreenCapturePossible(false);
    }

    @Test
    public void testScreenCapturePossible_assist() throws Exception {
        assertScreenCapturePossible(true);
    }

    private void assertScreenCapturePossible(boolean possible) throws InterruptedException {
        // Wait until voice interaction service is ready by sending broadcast to ask for status.
        Intent checkIsReadyIntent = new Intent(ACTION_CHECK_IS_READY);
        checkIsReadyIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        checkIsReadyIntent.setPackage(ASSIST_PACKAGE);
        boolean isAssistReady = false;
        for (int i = 0; i < MAX_ATTEMPTS_COUNT && !isAssistReady; i++) {
            Log.d(TAG, "assertScreenCapturePossible: wait for assist service ready, attempt " + i);
            final LinkedBlockingQueue<Boolean> q = new LinkedBlockingQueue<>();
            mContext.sendOrderedBroadcast(checkIsReadyIntent, null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    q.offer(getResultCode() == Activity.RESULT_OK);
                }
            }, null, Activity.RESULT_CANCELED, null, null);
            Boolean result = q.poll(WAIT_IN_SECOND, TimeUnit.SECONDS);
            isAssistReady = result != null && result;
        }
        Assert.assertTrue(isAssistReady);

        // Send broadcast to voice interaction service and ask for screnshot.
        BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(
                mContext, ACTION_HANDLE_SCREENSHOT);
        try {
            receiver.register();
            Intent showSessionIntent = new Intent(ACTION_SHOW_SESSION);
            showSessionIntent.setPackage(ASSIST_PACKAGE);
            mContext.sendBroadcast(showSessionIntent);
            Intent screenShotIntent = receiver.awaitForBroadcast();
            Assert.assertNotNull(screenShotIntent);
            Assert.assertTrue(screenShotIntent.hasExtra(KEY_HAS_SCREENSHOT));
            assertEquals(possible, screenShotIntent.getBooleanExtra(KEY_HAS_SCREENSHOT, false));
        } finally {
            receiver.unregisterQuietly();
        }
    }
}
