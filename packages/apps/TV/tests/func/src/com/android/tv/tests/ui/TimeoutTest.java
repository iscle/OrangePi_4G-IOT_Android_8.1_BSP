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
package com.android.tv.tests.ui;

import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertHas;
import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertWaitForCondition;

import android.support.test.filters.LargeTest;
import android.support.test.uiautomator.Until;

import com.android.tv.R;
import com.android.tv.testing.uihelper.Constants;

/**
 * Test timeout events like the menu despairing after no input.
 * <p>
 * <b>WARNING</b> some of these timeouts are 60 seconds. These tests will take a long time
 * complete.
 */
@LargeTest
public class TimeoutTest extends LiveChannelsTestCase {

    public void testMenu() {
        mLiveChannelsHelper.assertAppStarted();
        mDevice.pressMenu();

        assertWaitForCondition(mDevice, Until.hasObject(Constants.MENU));
        assertWaitForCondition(mDevice, Until.gone(Constants.MENU),
                mTargetResources.getInteger(R.integer.menu_show_duration));
    }

    public void testProgramGuide() {
        mLiveChannelsHelper.assertAppStarted();
        mMenuHelper.assertPressProgramGuide();
        assertWaitForCondition(mDevice,
                Until.hasObject(Constants.PROGRAM_GUIDE));
        assertWaitForCondition(mDevice, Until.gone(Constants.PROGRAM_GUIDE),
                mTargetResources.getInteger(R.integer.program_guide_show_duration));
        assertHas(mDevice, Constants.MENU, false);
    }
}
