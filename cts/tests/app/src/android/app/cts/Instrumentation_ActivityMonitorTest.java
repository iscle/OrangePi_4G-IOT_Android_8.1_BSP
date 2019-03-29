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

package android.app.cts;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.app.Instrumentation.ActivityResult;
import android.app.stubs.ActivityMonitorTestActivity;
import android.app.stubs.InstrumentationTestActivity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Instrumentation_ActivityMonitorTest extends InstrumentationTestCase {
    private static final String TAG = "ActivityMonitorTest";

    private static final long TIMEOUT_FOR_ACTIVITY_LAUNCH_MS = 5000; // 5 sec
    private static final long CHECK_INTERVAL_FOR_ACTIVITY_LAUNCH_MS = 100; // 0.1 sec

    /**
     * check points:
     * 1 Constructor with blocking true and false
     * 2 waitForActivity with timeout and no timeout
     * 3 get info about ActivityMonitor
     */
    public void testActivityMonitor() throws Exception {
        ActivityResult result = new ActivityResult(Activity.RESULT_OK, new Intent());
        Instrumentation instrumentation = getInstrumentation();
        ActivityMonitor am = instrumentation.addMonitor(
                InstrumentationTestActivity.class.getName(), result, false);
        Context context = instrumentation.getTargetContext();
        Intent intent = new Intent(context, InstrumentationTestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        Activity lastActivity = am.getLastActivity();
        long timeout = System.currentTimeMillis() + TIMEOUT_FOR_ACTIVITY_LAUNCH_MS;
        while (lastActivity == null && System.currentTimeMillis() < timeout) {
            Thread.sleep(CHECK_INTERVAL_FOR_ACTIVITY_LAUNCH_MS);
            lastActivity = am.getLastActivity();
        }
        Activity activity = am.waitForActivity();
        assertSame(activity, lastActivity);
        assertEquals(1, am.getHits());
        assertTrue(activity instanceof InstrumentationTestActivity);
        activity.finish();
        instrumentation.waitForIdleSync();
        context.startActivity(intent);
        timeout = System.currentTimeMillis() + TIMEOUT_FOR_ACTIVITY_LAUNCH_MS;
        activity = null;
        while (activity == null && System.currentTimeMillis() < timeout) {
            Thread.sleep(CHECK_INTERVAL_FOR_ACTIVITY_LAUNCH_MS);
            activity = am.waitForActivityWithTimeout(CHECK_INTERVAL_FOR_ACTIVITY_LAUNCH_MS);
        }
        assertNotNull(activity);
        activity.finish();
        instrumentation.removeMonitor(am);

        am = new ActivityMonitor(InstrumentationTestActivity.class.getName(), result, true);
        assertSame(result, am.getResult());
        assertTrue(am.isBlocking());
        IntentFilter which = new IntentFilter();
        am = new ActivityMonitor(which, result, false);
        assertSame(which, am.getFilter());
        assertFalse(am.isBlocking());
    }

    /**
     * Verifies that
     *   - when ActivityMonitor.onStartActivity returs non-null, then there is monitor hit.
     *   - when ActivityMonitor.onStartActivity returns null, then the activity start is not blocked.
     */
    public void testActivityMonitor_onStartActivity() throws Exception {
        final ActivityResult result = new ActivityResult(Activity.RESULT_OK, new Intent());
        final Instrumentation instrumentation = getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final Intent intent = new Intent(context, InstrumentationTestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Verify when ActivityMonitor.onStartActivity returns non-null, then there is a monitor hit.
        final CustomActivityMonitor cam1 = new CustomActivityMonitor(result);
        instrumentation.addMonitor(cam1);
        context.startActivity(intent);
        final Activity activity1 = cam1.waitForActivityWithTimeout(
                CHECK_INTERVAL_FOR_ACTIVITY_LAUNCH_MS * 2);
        try {
            assertNull("Activity should not have been started", activity1);
            assertEquals("There should be 1 monitor hit", 1, cam1.getHits());
        } finally {
            instrumentation.removeMonitor(cam1);
        }

        // Verify when ActivityMonitor.onStartActivity returns null, then activity start is not
        // blocked and there is no monitor hit.
        final CustomActivityMonitor cam2 = new CustomActivityMonitor(null);
        instrumentation.addMonitor(cam2);
        Activity activity2 = instrumentation.startActivitySync(intent);
        try {
            assertNotNull("Activity should not be null", activity2);
            assertTrue("Activity returned should be of instance InstrumentationTestActivity",
                    activity2 instanceof InstrumentationTestActivity);
            assertTrue("InstrumentationTestActivity should have been started",
                    ((InstrumentationTestActivity) activity2).isOnCreateCalled());
            assertEquals("There should be no monitor hits", 0, cam2.getHits());
        } finally {
            activity2.finish();
            instrumentation.removeMonitor(cam2);
        }
    }

    /**
     * Verifies that when ActivityMonitor.onStartActivity returns non-null, activity start is blocked.
     */
    public void testActivityMonitor_onStartActivityBlocks() throws Exception {
        final Instrumentation instrumentation = getInstrumentation();
        final Context context = instrumentation.getTargetContext();

        // Start ActivityMonitorTestActivity
        final Intent intent = new Intent(context, ActivityMonitorTestActivity.class);
        ActivityMonitorTestActivity amTestActivity =
                (ActivityMonitorTestActivity) instrumentation.startActivitySync(intent);

        // Initialize and set activity monitor.
        final int expectedResultCode = 1111;
        final String expectedAction = "matched_using_onStartActivity";
        final CustomActivityMonitor cam = new CustomActivityMonitor(
                new ActivityResult(expectedResultCode, new Intent(expectedAction)));
        instrumentation.addMonitor(cam);

        // Start InstrumentationTestActivity from ActivityMonitorTestActivity and verify
        // it is intercepted using onStartActivity as expected.
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            amTestActivity.setOnActivityResultListener(
                    new ActivityMonitorTestActivity.OnActivityResultListener() {
                        @Override
                        public void onActivityResult(int requestCode, int resultCode, Intent data) {
                            assertEquals("Result code is not same as expected",
                                    expectedResultCode, resultCode);
                            assertNotNull("Data from activity result is null", data);
                            assertEquals("Data action is not same as expected",
                                    expectedAction, data.getAction());
                            latch.countDown();
                        }
                    });
            amTestActivity.startInstrumentationTestActivity(false);
            if (!latch.await(TIMEOUT_FOR_ACTIVITY_LAUNCH_MS, TimeUnit.MILLISECONDS)) {
                fail("Timed out waiting for the activity result from "
                        + ActivityMonitorTestActivity.class.getName());
            }
            assertEquals("There should be 1 monitor hit", 1, cam.getHits());
        } finally {
            amTestActivity.finish();
            instrumentation.removeMonitor(cam);
        }
    }

    /**
     * Verifies that when the activity monitor is created using by passing IntentFilter,
     * then onStartActivity return value is ignored.
     */
    public void testActivityMonitor_onStartActivityAndIntentFilter() throws Exception {
        final Instrumentation instrumentation = getInstrumentation();
        final Context context = instrumentation.getTargetContext();

        // Start ActivityMonitorTestActivity
        final Intent intent = new Intent(context, ActivityMonitorTestActivity.class);
        ActivityMonitorTestActivity amTestActivity =
                (ActivityMonitorTestActivity) instrumentation.startActivitySync(intent);

        // Initialize and set activity monitor.
        final int expectedResultCode = 1122;
        final String expectedAction = "matched_using_intent_filter";
        final CustomActivityMonitor cam = new CustomActivityMonitor(
                new IntentFilter(InstrumentationTestActivity.START_INTENT),
                new ActivityResult(expectedResultCode, new Intent(expectedAction)),
                true);
        cam.setResultToReturn(new ActivityResult(1111, new Intent("matched_using_onStartActivity")));
        instrumentation.addMonitor(cam);

        // Start explicit InstrumentationTestActivity from ActivityMonitorTestActivity and verify
        // it is intercepted using the intentFilter as expected.
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            amTestActivity.setOnActivityResultListener(
                    new ActivityMonitorTestActivity.OnActivityResultListener() {
                        @Override
                        public void onActivityResult(int requestCode, int resultCode, Intent data) {
                            assertEquals("Result code is not same as expected",
                                    expectedResultCode, resultCode);
                            assertNotNull("Data from activity result is null", data);
                            assertEquals("Data action is not same as expected",
                                    expectedAction, data.getAction());
                            latch.countDown();
                        }
                    });
            amTestActivity.startInstrumentationTestActivity(false);
            if (!latch.await(TIMEOUT_FOR_ACTIVITY_LAUNCH_MS, TimeUnit.MILLISECONDS)) {
                fail("Timed out waiting for the activity result from "
                        + ActivityMonitorTestActivity.class.getName());
            }
            assertEquals("There should be 1 monitor hit", 1, cam.getHits());
        } finally {
            amTestActivity.finish();
            instrumentation.removeMonitor(cam);
        }
    }

    /**
     * Verifies that when the activity monitor is created using by passing activity class,
     * then onStartActivity return value is ignored.
     */
    public void testActivityMonitor_onStartActivityAndActivityClass() throws Exception {
        final Instrumentation instrumentation = getInstrumentation();
        final Context context = instrumentation.getTargetContext();

        // Start ActivityMonitorTestActivity
        final Intent intent = new Intent(context, ActivityMonitorTestActivity.class);
        ActivityMonitorTestActivity amTestActivity =
                (ActivityMonitorTestActivity) instrumentation.startActivitySync(intent);

        // Initialize and set activity monitor.
        final int expectedResultCode = 2244;
        final String expectedAction = "matched_using_activity_class";
        final CustomActivityMonitor cam = new CustomActivityMonitor(
                InstrumentationTestActivity.class.getName(),
                new ActivityResult(expectedResultCode, new Intent(expectedAction)),
                true);
        cam.setResultToReturn(new ActivityResult(2222, new Intent("matched_using_onStartActivity")));
        instrumentation.addMonitor(cam);

        // Start implicit InstrumentationTestActivity from ActivityMonitorTestActivity and verify
        // it is intercepted using the activity class as expected.
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            amTestActivity.setOnActivityResultListener(
                    new ActivityMonitorTestActivity.OnActivityResultListener() {
                        @Override
                        public void onActivityResult(int requestCode, int resultCode, Intent data) {
                            assertEquals("Result code is not same as expected",
                                    expectedResultCode, resultCode);
                            assertNotNull("Data from activity result is null", data);
                            assertEquals("Data action is not same as expected",
                                    expectedAction, data.getAction());
                            latch.countDown();
                        }
                    });
            amTestActivity.startInstrumentationTestActivity(true);
            if (!latch.await(TIMEOUT_FOR_ACTIVITY_LAUNCH_MS, TimeUnit.MILLISECONDS)) {
                fail("Timed out waiting for the activity result from "
                        + ActivityMonitorTestActivity.class.getName());
            }
            assertEquals("There should be 1 monitor hit", 1, cam.getHits());
        } finally {
            amTestActivity.finish();
            instrumentation.removeMonitor(cam);
        }
    }

    private class CustomActivityMonitor extends ActivityMonitor {
        private ActivityResult mResultToReturn;

        public CustomActivityMonitor(ActivityResult resultToReturn) {
            super();
            mResultToReturn = resultToReturn;
        }

        public CustomActivityMonitor(IntentFilter intentFilter, ActivityResult result,
                boolean blocked) {
            super(intentFilter, result, blocked);
        }

        public CustomActivityMonitor(String activityClass, ActivityResult result,
                boolean blocked) {
            super(activityClass, result, blocked);
        }

        public void setResultToReturn(ActivityResult resultToReturn) {
            mResultToReturn = resultToReturn;
        }

        @Override
        public ActivityResult onStartActivity(Intent intent) {
            final boolean implicitInstrumentationTestActivity = intent.getAction() != null &&
                    InstrumentationTestActivity.START_INTENT.equals(intent.getAction());
            final boolean explicitInstrumentationTestActivity = intent.getComponent() != null &&
                    InstrumentationTestActivity.class.getName().equals(
                            intent.getComponent().getClassName());
            if (implicitInstrumentationTestActivity || explicitInstrumentationTestActivity) {
                return mResultToReturn;
            }
            return null;
        }
    }
}
