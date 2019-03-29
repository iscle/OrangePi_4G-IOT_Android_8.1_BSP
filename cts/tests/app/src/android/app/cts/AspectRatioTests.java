/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.app.cts;

import android.app.stubs.MetaDataMaxAspectRatioActivity;
import com.android.appSdk25.Sdk25MaxAspectRatioActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.app.Activity;
import android.app.stubs.MaxAspectRatioActivity;
import android.app.stubs.MaxAspectRatioResizeableActivity;
import android.app.stubs.MaxAspectRatioUnsetActivity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.platform.test.annotations.Presubmit;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import static android.content.Context.WINDOW_SERVICE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.PackageManager.FEATURE_WATCH;
import static org.junit.Assert.fail;

/**
 * Build: mmma -j32 cts/tests/app
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsAppTestCases android.app.cts.AspectRatioTests
 */
@RunWith(AndroidJUnit4.class)
public class AspectRatioTests {
    private static final String TAG = "AspectRatioTests";

    // The max. aspect ratio the test activities are using.
    private static final float MAX_ASPECT_RATIO = 1.0f;

    // Max supported aspect ratio for pre-O apps.
    private static final float MAX_PRE_O_ASPECT_RATIO = 1.86f;

    // The minimum supported device aspect ratio.
    private static final float MIN_DEVICE_ASPECT_RATIO = 1.333f;

    // The minimum supported device aspect ratio for watches.
    private static final float MIN_WATCH_DEVICE_ASPECT_RATIO = 1.0f;

    @Rule
    public ActivityTestRule<MaxAspectRatioActivity> mMaxAspectRatioActivity =
            new ActivityTestRule<>(MaxAspectRatioActivity.class,
                    false /* initialTouchMode */, false /* launchActivity */);

    @Rule
    public ActivityTestRule<MaxAspectRatioResizeableActivity> mMaxAspectRatioResizeableActivity =
            new ActivityTestRule<>(MaxAspectRatioResizeableActivity.class,
                    false /* initialTouchMode */, false /* launchActivity */);

    @Rule
    public ActivityTestRule<MetaDataMaxAspectRatioActivity> mMetaDataMaxAspectRatioActivity =
        new ActivityTestRule<>(MetaDataMaxAspectRatioActivity.class,
            false /* initialTouchMode */, false /* launchActivity */);

    @Rule
    public ActivityTestRule<MaxAspectRatioUnsetActivity> mMaxAspectRatioUnsetActivity =
            new ActivityTestRule<>(MaxAspectRatioUnsetActivity.class,
                    false /* initialTouchMode */, false /* launchActivity */);

    // TODO: Can't use this to start an activity in a different process...sigh.
    @Rule
    public ActivityTestRule<Sdk25MaxAspectRatioActivity> mSdk25MaxAspectRatioActivity =
            new ActivityTestRule<>(Sdk25MaxAspectRatioActivity.class, "com.android.appSdk25",
                    268435456, false /* initialTouchMode */, false /* launchActivity */);

    private interface AssertAspectRatioCallback {
        void assertAspectRatio(float actual);
    }

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {
        finishActivity(mMaxAspectRatioActivity);
        finishActivity(mMaxAspectRatioResizeableActivity);
        finishActivity(mSdk25MaxAspectRatioActivity);
        finishActivity(mMaxAspectRatioUnsetActivity);
        finishActivity(mMetaDataMaxAspectRatioActivity);
    }

    @Test
    @Presubmit
    public void testDeviceAspectRatio() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final WindowManager wm = (WindowManager) context.getSystemService(WINDOW_SERVICE);
        final Display display = wm.getDefaultDisplay();
        final DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);

        float longSide = Math.max(metrics.widthPixels, metrics.heightPixels);
        float shortSide = Math.min(metrics.widthPixels, metrics.heightPixels);
        float deviceAspectRatio = longSide / shortSide;
        float expectedMinAspectRatio = context.getPackageManager().hasSystemFeature(FEATURE_WATCH)
                ? MIN_WATCH_DEVICE_ASPECT_RATIO : MIN_DEVICE_ASPECT_RATIO;

        if (deviceAspectRatio < expectedMinAspectRatio) {
            fail("deviceAspectRatio=" + deviceAspectRatio
                    + " is less than expectedMinAspectRatio=" + expectedMinAspectRatio);
        }
    }

    @Test
    @Presubmit
    public void testMaxAspectRatio() throws Exception {
        runTest(launchActivity(mMaxAspectRatioActivity),
                actual -> {
                    if (MAX_ASPECT_RATIO >= actual) return;
                    fail("actual=" + actual + " is greater than expected=" + MAX_ASPECT_RATIO);
                });
    }

    @Test
    @Presubmit
    public void testMetaDataMaxAspectRatio() throws Exception {
        runTest(launchActivity(mMetaDataMaxAspectRatioActivity),
            actual -> {
                if (MAX_ASPECT_RATIO >= actual) return;
                fail("actual=" + actual + " is greater than expected=" + MAX_ASPECT_RATIO);
            });
    }

    @Test
    // TODO: Currently 10% flaky so not part of pre-submit for now
    public void testMaxAspectRatioResizeableActivity() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final float expected = getAspectRatio(context);

        // Since this activity is resizeable, its aspect ratio shouldn't be less than the device's
        runTest(launchActivity(mMaxAspectRatioResizeableActivity),
                actual -> {
                    if (aspectRatioEqual(expected, actual) || expected < actual) return;
                    fail("actual=" + actual + " is less than expected=" + expected);
                });
    }

    @Test
    @Presubmit
    public void testMaxAspectRatioUnsetActivity() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final float expected = getAspectRatio(context);

        // Since this activity didn't set an aspect ratio, its aspect ratio shouldn't be less than
        // the device's
        runTest(launchActivity(mMaxAspectRatioUnsetActivity),
                actual -> {
                    if (aspectRatioEqual(expected, actual) || expected < actual) return;
                    fail("actual=" + actual + " is less than expected=" + expected);
                });
    }

    @Test
    // TODO(b/35810513): Can't use rule to start an activity in a different process. Need a
    // different way to make this test happen...host side? Sigh...
    @Ignore
    public void testMaxAspectRatioPreOActivity() throws Exception {
        runTest(launchActivity(mSdk25MaxAspectRatioActivity),
                actual -> {
                    if (MAX_PRE_O_ASPECT_RATIO >= actual) return;
                    fail("actual=" + actual + " is greater than expected=" + MAX_PRE_O_ASPECT_RATIO);
                });
    }

    private void runTest(Activity activity, AssertAspectRatioCallback callback) {
        callback.assertAspectRatio(getAspectRatio(activity));

        // TODO(b/35810513): All this rotation stuff doesn't really work yet. Need to make sure
        // context is updated correctly here. Also, what does it mean to be holding a reference to
        // this activity if changing the orientation will cause a relaunch?
//        activity.setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);
//        waitForIdle();
//        callback.assertAspectRatio(getAspectRatio(activity));
//
//        activity.setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
//        waitForIdle();
//        callback.assertAspectRatio(getAspectRatio(activity));
    }

    private float getAspectRatio(Context context) {
        final Display display =
                ((WindowManager) context.getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        final Point size = new Point();
        display.getSize(size);
        final float longSide = Math.max(size.x, size.y);
        final float shortSide = Math.min(size.x, size.y);
        return longSide / shortSide;
    }

    private Activity launchActivity(ActivityTestRule activityRule) {
        final Activity activity = activityRule.launchActivity(null);
        waitForIdle();
        return activity;
    }

    private void finishActivity(ActivityTestRule activityRule) {
        final Activity activity = activityRule.getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    private void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static boolean aspectRatioEqual(float a, float b) {
        // Aspect ratios are considered equal if they ware within to significant digits.
        float diff = Math.abs(a - b);
        return diff < 0.01f;
    }
}
