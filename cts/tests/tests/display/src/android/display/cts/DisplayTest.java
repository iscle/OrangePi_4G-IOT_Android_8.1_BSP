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

package android.display.cts;

import org.junit.Rule;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Presentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.Presubmit;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.test.InstrumentationTestCase;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Display.HdrCapabilities;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DisplayTest extends InstrumentationTestCase {
    // The CTS package brings up an overlay display on the target device (see AndroidTest.xml).
    // The overlay display parameters must match the ones defined there which are
    // 181x161/214 (wxh/dpi).  It only matters that these values are different from any real
    // display.

    private static final int SECONDARY_DISPLAY_WIDTH = 181;
    private static final int SECONDARY_DISPLAY_HEIGHT = 161;
    private static final int SECONDARY_DISPLAY_DPI = 214;
    private static final float SCALE_DENSITY_LOWER_BOUND =
            (float)(SECONDARY_DISPLAY_DPI - 1) / DisplayMetrics.DENSITY_DEFAULT;
    private static final float SCALE_DENSITY_UPPER_BOUND =
            (float)(SECONDARY_DISPLAY_DPI + 1) / DisplayMetrics.DENSITY_DEFAULT;
    // Matches com.android.internal.R.string.display_manager_overlay_display_name.
    private static final String OVERLAY_DISPLAY_NAME_PREFIX = "Overlay #";
    private static final String OVERLAY_DISPLAY_TYPE = "type OVERLAY";

    private DisplayManager mDisplayManager;
    private WindowManager mWindowManager;
    private Context mContext;

    // To test display mode switches.
    private TestPresentation mPresentation;

    private Activity mScreenOnActivity;

    @Rule
    public ActivityTestRule<DisplayTestActivity> mDisplayTestActivity =
            new ActivityTestRule<>(DisplayTestActivity.class,
                    false /* initialTouchMode */, false /* launchActivity */);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mScreenOnActivity = launchScreenOnActivity();
        mContext = getInstrumentation().getContext();
        mDisplayManager = (DisplayManager)mContext.getSystemService(Context.DISPLAY_SERVICE);
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mScreenOnActivity != null) {
            mScreenOnActivity.finish();
        }
    }

    private void enableAppOps() {
        StringBuilder cmd = new StringBuilder();
        cmd.append("appops set ");
        cmd.append(getInstrumentation().getContext().getPackageName());
        cmd.append(" android:system_alert_window allow");
        getInstrumentation().getUiAutomation().executeShellCommand(cmd.toString());

        StringBuilder query = new StringBuilder();
        query.append("appops get ");
        query.append(getInstrumentation().getContext().getPackageName());
        query.append(" android:system_alert_window");
        String queryStr = query.toString();

        String result = "No operations.";
        while (result.contains("No operations")) {
            ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation().executeShellCommand(
                    queryStr);
            InputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
            result = convertStreamToString(inputStream);
        }
    }

    private String convertStreamToString(InputStream is) {
        try (java.util.Scanner s = new Scanner(is).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }

    /** Check if the display is an overlay display, created by this test. */
    private boolean isSecondaryDisplay(Display display) {
        return display.toString().contains(OVERLAY_DISPLAY_TYPE);
    }

    /** Get the overlay display, created by this test. */
    private Display getSecondaryDisplay(Display[] displays) {
        for (Display display : displays) {
            if (isSecondaryDisplay(display)) {
                return display;
            }
        }
        return null;
    }

    /**
     * Verify that the getDisplays method returns both a default and an overlay display.
     */
    public void testGetDisplays() {
        Display[] displays = mDisplayManager.getDisplays();
        assertNotNull(displays);
        assertTrue(2 <= displays.length);
        boolean hasDefaultDisplay = false;
        boolean hasSecondaryDisplay = false;
        for (Display display : displays) {
            if (display.getDisplayId() == Display.DEFAULT_DISPLAY) {
                hasDefaultDisplay = true;
            }
            if (isSecondaryDisplay(display)) {
                hasSecondaryDisplay = true;
            }
        }
        assertTrue(hasDefaultDisplay);
        assertTrue(hasSecondaryDisplay);
    }

    /**
     * Verify that the WindowManager returns the default display.
     */
    @Presubmit
    public void testDefaultDisplay() {
        assertEquals(Display.DEFAULT_DISPLAY, mWindowManager.getDefaultDisplay().getDisplayId());
    }

    /**
     * Verify default display's HDR capability.
     */
    public void testDefaultDisplayHdrCapability() {
        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        HdrCapabilities cap = display.getHdrCapabilities();
        int[] hdrTypes = cap.getSupportedHdrTypes();
        for (int type : hdrTypes) {
            assertTrue(type >= 1 && type <= 3);
        }
        assertFalse(cap.getDesiredMaxLuminance() < -1.0f);
        assertFalse(cap.getDesiredMinLuminance() < -1.0f);
        assertFalse(cap.getDesiredMaxAverageLuminance() < -1.0f);
        if (hdrTypes.length > 0) {
            assertTrue(display.isHdr());
        } else {
            assertFalse(display.isHdr());
        }
    }

    /**
     * Verify that there is a secondary display.
     */
    public void testSecondaryDisplay() {
        Display display = getSecondaryDisplay(mDisplayManager.getDisplays());
        assertNotNull(display);
        assertTrue(Display.DEFAULT_DISPLAY != display.getDisplayId());
    }

    /**
     * Test the properties of the secondary Display.
     */
    public void testGetDisplayAttrs() {
        Display display = getSecondaryDisplay(mDisplayManager.getDisplays());

        assertEquals(SECONDARY_DISPLAY_WIDTH, display.getWidth());
        assertEquals(SECONDARY_DISPLAY_HEIGHT, display.getHeight());

        Point outSize = new Point();
        display.getSize(outSize);
        assertEquals(SECONDARY_DISPLAY_WIDTH, outSize.x);
        assertEquals(SECONDARY_DISPLAY_HEIGHT, outSize.y);

        assertEquals(0, display.getOrientation());

        assertEquals(PixelFormat.RGBA_8888, display.getPixelFormat());

        assertTrue(0 < display.getRefreshRate());

        assertTrue(display.getName().contains(OVERLAY_DISPLAY_NAME_PREFIX));

        assertFalse(display.isWideColorGamut());
    }

    /**
     * Test that the getMetrics method fills in correct values.
     */
    public void testGetMetrics() {
        testGetMetrics(mDisplayManager);
    }

    /**
     * Tests getting metrics from the Activity context.
     */
    public void testActivityContextGetMetrics() {
        final Activity activity = launchActivity(mDisplayTestActivity);
        final DisplayManager dm =
                (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
        testGetMetrics(dm);
    }

    public void testGetMetrics(DisplayManager manager) {
        Display display = getSecondaryDisplay(manager.getDisplays());

        Point outSize = new Point();
        display.getSize(outSize);

        DisplayMetrics outMetrics = new DisplayMetrics();
        outMetrics.setToDefaults();
        display.getMetrics(outMetrics);

        assertEquals(SECONDARY_DISPLAY_WIDTH, outMetrics.widthPixels);
        assertEquals(SECONDARY_DISPLAY_HEIGHT, outMetrics.heightPixels);

        // The scale is in [0.1, 3], and density is the scale factor.
        assertTrue(SCALE_DENSITY_LOWER_BOUND <= outMetrics.density
                && outMetrics.density <= SCALE_DENSITY_UPPER_BOUND);
        assertTrue(SCALE_DENSITY_LOWER_BOUND <= outMetrics.scaledDensity
                && outMetrics.scaledDensity <= SCALE_DENSITY_UPPER_BOUND);

        assertEquals(SECONDARY_DISPLAY_DPI, outMetrics.densityDpi);
        assertEquals((float)SECONDARY_DISPLAY_DPI, outMetrics.xdpi);
        assertEquals((float)SECONDARY_DISPLAY_DPI, outMetrics.ydpi);
    }

    /**
     * Test that the getFlags method returns no flag bits set for the overlay display.
     */
    public void testFlags() {
        Display display = getSecondaryDisplay(mDisplayManager.getDisplays());

        assertEquals(Display.FLAG_PRESENTATION, display.getFlags());
    }

    /**
     * Tests that the mode-related attributes and methods work as expected.
     */
    public void testMode() {
        Display display = getSecondaryDisplay(mDisplayManager.getDisplays());
        assertEquals(2, display.getSupportedModes().length);
        Display.Mode mode = display.getMode();
        assertEquals(display.getSupportedModes()[0], mode);
        assertEquals(SECONDARY_DISPLAY_WIDTH, mode.getPhysicalWidth());
        assertEquals(SECONDARY_DISPLAY_HEIGHT, mode.getPhysicalHeight());
        assertEquals(display.getRefreshRate(), mode.getRefreshRate());
    }

    /**
     * Tests that mode switch requests are correctly executed.
     */
    public void testModeSwitch() throws Exception {
        enableAppOps();
        final Display display = getSecondaryDisplay(mDisplayManager.getDisplays());
        Display.Mode[] modes = display.getSupportedModes();
        assertEquals(2, modes.length);
        Display.Mode mode = display.getMode();
        assertEquals(modes[0], mode);
        final Display.Mode newMode = modes[1];

        Handler handler = new Handler(Looper.getMainLooper());

        // Register for display events.
        final CountDownLatch changeSignal = new CountDownLatch(1);
        mDisplayManager.registerDisplayListener(new DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {}

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId == display.getDisplayId()) {
                    changeSignal.countDown();
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {}
        }, handler);

        // Show the presentation.
        final CountDownLatch presentationSignal = new CountDownLatch(1);
        handler.post(new Runnable() {
            @Override
            public void run() {
                mPresentation = new TestPresentation(
                        getInstrumentation().getContext(), display, newMode.getModeId());
                mPresentation.show();
                presentationSignal.countDown();
            }
        });
        assertTrue(presentationSignal.await(5, TimeUnit.SECONDS));

        // Wait until the display change is effective.
        assertTrue(changeSignal.await(5, TimeUnit.SECONDS));

        assertEquals(newMode, display.getMode());
        handler.post(new Runnable() {
            @Override
            public void run() {
                mPresentation.dismiss();
            }
        });
    }

    /**
     * Used to force mode changes on a display.
     * <p>
     * Note that due to limitations of the Presentation class, the modes must have the same size
     * otherwise the presentation will be automatically dismissed.
     */
    private static final class TestPresentation extends Presentation {

        private final int mModeId;

        public TestPresentation(Context context, Display display, int modeId) {
            super(context, display);
            mModeId = modeId;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            View content = new View(getContext());
            content.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            content.setBackgroundColor(Color.RED);
            setContentView(content);

            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.preferredDisplayModeId = mModeId;
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            params.setTitle("CtsTestPresentation");
            getWindow().setAttributes(params);
        }
    }

    private Activity launchScreenOnActivity() {
        Class clazz = ScreenOnActivity.class;
        String targetPackage = getInstrumentation().getContext().getPackageName();
        Instrumentation.ActivityResult result = new Instrumentation.ActivityResult(0, new Intent());
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(clazz.getName(), result, false);
        getInstrumentation().addMonitor(monitor);
        launchActivity(targetPackage, clazz, null);
        return monitor.waitForActivity();
    }

    private Activity launchActivity(ActivityTestRule activityRule) {
        final Activity activity = activityRule.launchActivity(null);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return activity;
    }
}
