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
package com.android.tv.tests.jank;

import android.content.res.Resources;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.UiDevice;

import com.android.tv.testing.uihelper.LiveChannelsUiDeviceHelper;

/**
 * Base test case for LiveChannel jank tests.
 */
abstract class LiveChannelsTestCase extends JankTestBase {
    protected UiDevice mDevice;
    protected Resources mTargetResources;
    protected LiveChannelsUiDeviceHelper mLiveChannelsHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mTargetResources = getInstrumentation().getTargetContext().getResources();
        mLiveChannelsHelper = new LiveChannelsUiDeviceHelper(mDevice, mTargetResources,
                getInstrumentation().getContext());
        mLiveChannelsHelper.assertAppStarted();
    }

    @Override
    protected void tearDown() throws Exception {
        // Destroys the activity to make sure next test case's activity launch check works well.
        mLiveChannelsHelper.assertAppStopped();
        super.tearDown();
    }
}
