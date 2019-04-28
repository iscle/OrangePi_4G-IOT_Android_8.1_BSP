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

import android.support.test.filters.LargeTest;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Until;

import com.android.tv.R;
import com.android.tv.testing.uihelper.ByResource;
import com.android.tv.testing.uihelper.UiDeviceUtils;

/**
 * Tests for channel sources.
 */
@LargeTest
public class ChannelSourcesTest extends LiveChannelsTestCase {
    private BySelector mBySettingsSidePanel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mBySettingsSidePanel = mSidePanelHelper.bySidePanelTitled(
                R.string.side_panel_title_settings);
    }

    //TODO: create a cancelable test channel setup.

    public void testSetup_cancel() {
        mLiveChannelsHelper.assertAppStarted();
        mMenuHelper.assertPressOptionsSettings();
        assertWaitForCondition(mDevice, Until.hasObject(mBySettingsSidePanel));

        mSidePanelHelper.assertNavigateToItem(R.string.settings_channel_source_item_setup);
        mDevice.pressDPadCenter();

        assertWaitForCondition(mDevice,
                Until.hasObject(ByResource.text(mTargetResources, R.string.setup_sources_text)));
        mDevice.pressBack();
    }

    // SetupSourcesFragment should have no errors if side fragment item is clicked multiple times.
    public void testSetupTwice_cancel() {
        mLiveChannelsHelper.assertAppStarted();
        mMenuHelper.assertPressOptionsSettings();
        assertWaitForCondition(mDevice, Until.hasObject(mBySettingsSidePanel));

        mSidePanelHelper.assertNavigateToItem(R.string.settings_channel_source_item_setup);
        UiDeviceUtils.pressDPadCenter(getInstrumentation(), 2);

        assertWaitForCondition(mDevice,
                Until.hasObject(ByResource.text(mTargetResources, R.string.setup_sources_text)));
        mDevice.pressBack();
    }
}
