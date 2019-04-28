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

import com.android.tv.guide.ProgramGuide;
import com.android.tv.testing.uihelper.Constants;

/**
 * Tests for {@link ProgramGuide}.
 */
@LargeTest
public class ProgramGuideTest extends LiveChannelsTestCase {

    public void testCancel() {
        mLiveChannelsHelper.assertAppStarted();
        mMenuHelper.assertPressProgramGuide();
        assertWaitForCondition(mDevice,
                Until.hasObject(Constants.PROGRAM_GUIDE));
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.gone(Constants.PROGRAM_GUIDE));
        assertHas(mDevice, Constants.MENU, false);
    }
}
