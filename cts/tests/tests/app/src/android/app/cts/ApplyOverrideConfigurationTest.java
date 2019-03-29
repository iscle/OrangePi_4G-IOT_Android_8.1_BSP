/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package android.app.cts;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.support.test.filters.SmallTest;
import android.test.ActivityInstrumentationTestCase2;

import java.util.concurrent.Future;

/**
 * Tests the {@link android.view.ContextThemeWrapper#applyOverrideConfiguration(Configuration)}
 * method and how it affects the Activity's resources and lifecycle callbacks.
 */
public class ApplyOverrideConfigurationTest extends
        ActivityInstrumentationTestCase2<ApplyOverrideConfigurationActivity> {
    public ApplyOverrideConfigurationTest() {
        super(ApplyOverrideConfigurationActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        getInstrumentation().getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_0);
    }

    @Override
    public void tearDown() throws Exception {
        getInstrumentation().getUiAutomation().setRotation(UiAutomation.ROTATION_UNFREEZE);
        super.tearDown();
    }

    @SmallTest
    public void testOverriddenConfigurationIsPassedIntoCallback() throws Exception {
        // This test instruments display rotation; disable it on devices that do not
        // support auto-rotation.
        if (!isRotationSupported(getActivity())) {
            return;
        }
        final Configuration config = getActivity().getResources().getConfiguration();
        final int originalOrientation = config.orientation;
        assertEquals(ApplyOverrideConfigurationActivity.OVERRIDE_SMALLEST_WIDTH,
                config.smallestScreenWidthDp);

        Future<Configuration> callback =
                getActivity().watchForSingleOnConfigurationChangedCallback();

        getInstrumentation().getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_90);

        final Configuration callbackConfig = callback.get();
        assertNotNull(callbackConfig);

        final Configuration newConfig = getActivity().getResources().getConfiguration();
        assertTrue(newConfig.orientation != originalOrientation);
        assertEquals(ApplyOverrideConfigurationActivity.OVERRIDE_SMALLEST_WIDTH,
                newConfig.smallestScreenWidthDp);

        assertEquals(newConfig, callbackConfig);
    }

    /**
     * Gets whether the device supports rotation. In general such a
     * device has both portrait and landscape features.
     *
     * @param context Context for accessing system resources.
     * @return Whether the device supports rotation.
     */
    public static boolean isRotationSupported(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT)
                && pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE);
    }
}
