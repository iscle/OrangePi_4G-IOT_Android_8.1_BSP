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
 * limitations under the License.
 */
package com.android.emergency.edit;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.view.Surface;

import com.android.emergency.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link EditMedicalInfoActivity}. */
@RunWith(AndroidJUnit4.class)
public final class EditMedicalInfoActivityTest {
    private Instrumentation mInstrumentation;
    private Context mTargetContext;
    private UiDevice mDevice;
    private int mInitialRotation;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mTargetContext = mInstrumentation.getTargetContext();
        mDevice = UiDevice.getInstance(mInstrumentation);
        mInitialRotation = mDevice.getDisplayRotation();
    }

    @After
    public void tearDown() {
        // Restore orientation prior to starting test.
        try {
            switch (mInitialRotation) {
                case Surface.ROTATION_90:
                    mDevice.setOrientationRight();
                    break;
                case Surface.ROTATION_270:
                    mDevice.setOrientationLeft();
                    break;
                default:
                    mDevice.setOrientationNatural();
                    break;
            }
        } catch (Exception e) {
            // Squelch and move along.
        }
    }

    @Test
    public void testRotate_editNameDialogOpen_shouldNotCrash() throws Exception {
        final Intent editActivityIntent = new Intent(mTargetContext, EditMedicalInfoActivity.class);
        EditMedicalInfoActivity activity =
                (EditMedicalInfoActivity) mInstrumentation.startActivitySync(editActivityIntent);
        EditMedicalInfoFragment fragment = activity.getFragment();

        // Click on the Name field to pop up the name edit dialog.
        onView(withText(R.string.name)).perform(click());

        // Rotate the device.
        mDevice.setOrientationLeft();
        mDevice.setOrientationNatural();
        mDevice.setOrientationRight();
    }
}
