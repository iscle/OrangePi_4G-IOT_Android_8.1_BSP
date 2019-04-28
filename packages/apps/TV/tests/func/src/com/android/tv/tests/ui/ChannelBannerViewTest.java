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

import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertWaitForCondition;

import android.support.test.filters.SmallTest;
import android.support.test.uiautomator.Until;

import com.android.tv.R;
import com.android.tv.testing.uihelper.Constants;

@SmallTest
public class ChannelBannerViewTest extends LiveChannelsTestCase {
    // Channel banner show duration with the grace period.
    private long mShowDurationMillis;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mLiveChannelsHelper.assertAppStarted();
        mShowDurationMillis = mTargetResources.getInteger(R.integer.channel_banner_show_duration)
                + Constants.MAX_SHOW_DELAY_MILLIS;
    }

    public void testChannelBannerAppearDisappear() {
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.hasObject(Constants.CHANNEL_BANNER));
        assertWaitForCondition(mDevice, Until.gone(Constants.CHANNEL_BANNER), mShowDurationMillis);
    }

    public void testChannelBannerShownWhenTune() {
        mDevice.pressDPadDown();
        assertWaitForCondition(mDevice, Until.hasObject(Constants.CHANNEL_BANNER));
        mDevice.pressDPadUp();
        assertWaitForCondition(mDevice, Until.hasObject(Constants.CHANNEL_BANNER));
    }
}
