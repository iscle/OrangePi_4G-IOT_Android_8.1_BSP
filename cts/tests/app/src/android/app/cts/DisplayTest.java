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

import android.app.Instrumentation;
import android.app.stubs.DisplayTestActivity;
import android.app.stubs.OrientationTestUtils;
import android.test.ActivityInstrumentationTestCase2;
import android.view.Display;

/**
 * Tests to verify functionality of {@link Display}.
 */
public class DisplayTest extends ActivityInstrumentationTestCase2<DisplayTestActivity> {
    private Instrumentation mInstrumentation;
    private DisplayTestActivity mActivity;

    public DisplayTest() {
        super("android.app.stubs", DisplayTestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
    }

    /**
     * Tests that the underlying {@link android.view.DisplayAdjustments} in {@link Display} updates.
     * The method {@link DisplayTestActivity#getDisplay()} fetches the Display directly from the
     * {@link android.view.WindowManager}. A display fetched before the rotation should have the
     * updated adjustments after a rotation.
     */
    public void testRotation() throws Throwable {
        // Get a {@link Display} instance before rotation.
        final Display origDisplay = mActivity.getDisplay();

        // Capture the originally reported width and heights
        final int origWidth = origDisplay.getWidth();
        final int origHeight = origDisplay.getHeight();

        // Change orientation
        mActivity.configurationChangeObserver.startObserving();
        OrientationTestUtils.switchOrientation(mActivity);
        mActivity.configurationChangeObserver.await();

        // Get a {@link Display} instance after rotation.
        final Display updatedDisplay = mActivity.getDisplay();

        // For square sreens the following assertions do not make sense and will always fail.
        if (origWidth != origHeight) {
            // Ensure that the width and height of the original instance no longer are the same. Note
            // that this will be false if the device width and height are identical.
            assertFalse("width from original display instance should have changed",
                    origWidth == origDisplay.getWidth());
            assertFalse("height from original display instance should have changed",
                    origHeight == origDisplay.getHeight());
        }

        // Ensure that the width and height of the original instance have been updated to match the
        // values that would be found in a new instance.
        assertTrue("width from original display instance should match current",
                origDisplay.getWidth() == updatedDisplay.getWidth());
        assertTrue("height from original display instance should match current",
                origDisplay.getHeight() == updatedDisplay.getHeight());
    }
}
