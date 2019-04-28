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

package com.android.tv.tests.ui.sidepanel;

import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertWaitForCondition;

import android.graphics.Point;
import android.support.test.filters.LargeTest;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import com.android.tv.R;
import com.android.tv.testing.uihelper.Constants;
import com.android.tv.tests.ui.LiveChannelsTestCase;

@LargeTest
public class CustomizeChannelListFragmentTest extends LiveChannelsTestCase {
    private BySelector mBySettingsSidePanel;
    private UiObject2 mTvView;
    private Point mNormalTvViewCenter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mLiveChannelsHelper.assertAppStarted();
        mTvView = mDevice.findObject(Constants.TV_VIEW);
        mNormalTvViewCenter = mTvView.getVisibleCenter();
        assertNotNull(mNormalTvViewCenter);
        pressKeysForChannel(com.android.tv.testing.testinput.TvTestInputConstants.CH_2);
        // Wait until KeypadChannelSwitchView closes.
        assertWaitForCondition(mDevice, Until.hasObject(Constants.CHANNEL_BANNER));
        mBySettingsSidePanel = mSidePanelHelper.bySidePanelTitled(
                R.string.side_panel_title_settings);
    }

    private void assertShrunkenTvView(boolean shrunkenExpected) {
        Point currentTvViewCenter = mTvView.getVisibleCenter();
        if (shrunkenExpected) {
            assertFalse(mNormalTvViewCenter.equals(currentTvViewCenter));
        } else {
            assertTrue(mNormalTvViewCenter.equals(currentTvViewCenter));
        }
    }

    public void testCustomizeChannelList_noraml() {
        // Show customize channel list fragment
        mMenuHelper.assertPressOptionsSettings();
        assertWaitForCondition(mDevice, Until.hasObject(mBySettingsSidePanel));
        mSidePanelHelper.assertNavigateToItem(
                R.string.settings_channel_source_item_customize_channels);
        mDevice.pressDPadCenter();
        BySelector bySidePanel = mSidePanelHelper.bySidePanelTitled(
                R.string.side_panel_title_edit_channels_for_an_input);
        assertWaitForCondition(mDevice, Until.hasObject(bySidePanel));
        assertShrunkenTvView(true);

        // Show group by fragment
        mSidePanelHelper.assertNavigateToItem(R.string.edit_channels_item_group_by, Direction.UP);
        mDevice.pressDPadCenter();
        bySidePanel = mSidePanelHelper.bySidePanelTitled(R.string.side_panel_title_group_by);
        assertWaitForCondition(mDevice, Until.hasObject(bySidePanel));
        assertShrunkenTvView(true);

        // Back to customize channel list fragment
        mDevice.pressBack();
        bySidePanel = mSidePanelHelper.bySidePanelTitled(
                R.string.side_panel_title_edit_channels_for_an_input);
        assertWaitForCondition(mDevice, Until.hasObject(bySidePanel));
        assertShrunkenTvView(true);

        // Return to the main menu.
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.hasObject(mBySettingsSidePanel));
        assertShrunkenTvView(false);
    }

    public void testCustomizeChannelList_timeout() {
        // Show customize channel list fragment
        mMenuHelper.assertPressOptionsSettings();
        assertWaitForCondition(mDevice, Until.hasObject(mBySettingsSidePanel));
        mSidePanelHelper.assertNavigateToItem(
                R.string.settings_channel_source_item_customize_channels);
        mDevice.pressDPadCenter();
        BySelector bySidePanel = mSidePanelHelper.bySidePanelTitled(
                R.string.side_panel_title_edit_channels_for_an_input);
        assertWaitForCondition(mDevice, Until.hasObject(bySidePanel));
        assertShrunkenTvView(true);

        // Show group by fragment
        mSidePanelHelper.assertNavigateToItem(R.string.edit_channels_item_group_by, Direction.UP);
        mDevice.pressDPadCenter();
        bySidePanel = mSidePanelHelper.bySidePanelTitled(R.string.side_panel_title_group_by);
        assertWaitForCondition(mDevice, Until.hasObject(bySidePanel));
        assertShrunkenTvView(true);

        // Wait for time-out to return to the main menu.
        assertWaitForCondition(mDevice, Until.gone(bySidePanel),
                mTargetResources.getInteger(R.integer.side_panel_show_duration));
        assertShrunkenTvView(false);
    }
}
