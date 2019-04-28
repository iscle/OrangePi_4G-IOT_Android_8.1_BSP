/**
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
package com.android.cellbroadcastreceiver;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CellBroadcastSettingsTest {
    private Instrumentation mInstrumentation;
    private Context mContext;
    private UiDevice mDevice;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mDevice = UiDevice.getInstance(mInstrumentation);
    }

    @Test
    public void testRotate_alertReminderDialogOpen_shouldNotCrash() {
        mInstrumentation.startActivitySync(createActivityIntent());

        openAlertReminderDialog();

        try {
            mDevice.setOrientationLeft();
            mDevice.setOrientationNatural();
            mDevice.setOrientationRight();
        } catch (Exception e) {
            Assert.fail("Exception " + e);
        }
    }

    private Intent createActivityIntent() {
        Intent intent = new Intent(mContext, CellBroadcastSettings.class);
        intent.setPackage("com.android.cellbroadcastreceiver");
        intent.setAction("android.intent.action.MAIN");
        return intent;
    }

    private void openAlertReminderDialog() {
        onView(withText(mContext.getString(R.string.alert_reminder_interval_title)))
                .perform(click());
    }
}
