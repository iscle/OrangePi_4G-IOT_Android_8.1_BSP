/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.support.test.filters.MediumTest;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;

/**
 * Jank tests for channel zapping.
 */
@MediumTest
public class ChannelZappingJankTest extends LiveChannelsTestCase {
    private static final String TAG = "ChannelZappingJankTest";

    private static final String STARTING_CHANNEL = "13";

    /**
     * The minimum number of frames expected during each jank test.
     * If there is less the test will fail. To be safe we loop the action in each test to create
     * twice this many frames under normal conditions.
     * <p>At least 100 frams should be chosen so there will be enough frame
     * for the 90th, 95th, and 98th percentile measurements are significant.
     *
     * @see <a href="http://go/janktesthelper-best-practices">Jank Test Helper Best Practices</a>
     */
    private static final int EXPECTED_FRAMES = 100;
    private static final int WARM_UP_CHANNEL_ZAPPING_COUNT = 2;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Utils.pressKeysForChannelNumber(STARTING_CHANNEL, mDevice);
    }

    @JankTest(expectedFrames = EXPECTED_FRAMES,
            beforeTest = "warmChannelZapping")
    @GfxMonitor(processName = Utils.LIVE_CHANNELS_PROCESS_NAME)
    public void testChannelZapping() {
        int frameCountForOneChannelZapping = 40;  // measured by hand
        int repeat = EXPECTED_FRAMES * 2 / frameCountForOneChannelZapping;
        for (int i = 0; i < repeat; i++) {
            mDevice.pressDPadUp();
            mDevice.waitForIdle();
            // Press BACK to close banner.
            mDevice.pressBack();
            mDevice.waitForIdle();
        }
    }

    // It's public to be used with @JankTest annotation.
    public void warmChannelZapping() {
        for (int i = 0; i < WARM_UP_CHANNEL_ZAPPING_COUNT; ++i) {
            mDevice.pressDPadUp();
            mDevice.waitForIdle();
        }
        // Press BACK to close banner.
        mDevice.pressBack();
        mDevice.waitForIdle();
    }
}
