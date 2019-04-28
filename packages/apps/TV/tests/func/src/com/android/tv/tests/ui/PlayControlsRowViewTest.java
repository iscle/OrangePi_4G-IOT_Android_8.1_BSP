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

import static com.android.tv.testing.uihelper.Constants.CHANNEL_BANNER;
import static com.android.tv.testing.uihelper.Constants.FOCUSED_VIEW;
import static com.android.tv.testing.uihelper.Constants.MENU;
import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertWaitForCondition;

import android.support.test.filters.SmallTest;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.view.KeyEvent;

import com.android.tv.R;
import com.android.tv.testing.testinput.TvTestInputConstants;
import com.android.tv.testing.uihelper.DialogHelper;

@SmallTest
public class PlayControlsRowViewTest extends LiveChannelsTestCase {
    private static final String BUTTON_ID_PLAY_PAUSE = "com.android.tv:id/play_pause";

    private BySelector mBySettingsSidePanel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mLiveChannelsHelper.assertAppStarted();
        pressKeysForChannel(TvTestInputConstants.CH_2);
        // Wait until KeypadChannelSwitchView closes.
        assertWaitForCondition(mDevice, Until.hasObject(CHANNEL_BANNER));
        // Tune to a new channel to ensure that the channel is changed.
        mDevice.pressDPadUp();
        getInstrumentation().waitForIdleSync();
        mBySettingsSidePanel = mSidePanelHelper.bySidePanelTitled(
                R.string.side_panel_title_settings);
    }

    /**
     * Test the normal case. The play/pause button should have focus initially.
     */
    public void testFocusedViewInNormalCase() {
        mMenuHelper.showMenu();
        mMenuHelper.assertNavigateToPlayControlsRow();
        assertButtonHasFocus(BUTTON_ID_PLAY_PAUSE);
        mDevice.pressBack();
    }

    /**
     * Tests the case when the forwarding action is disabled.
     * In this case, the button corresponding to the action is disabled, so play/pause button should
     * have the focus.
     */
    public void testFocusedViewWithDisabledActionForward() {
        // Fast forward button
        mDevice.pressKeyCode(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
        mMenuHelper.assertWaitForMenu();
        assertButtonHasFocus(BUTTON_ID_PLAY_PAUSE);
        mDevice.pressBack();

        // Next button
        mDevice.pressKeyCode(KeyEvent.KEYCODE_MEDIA_NEXT);
        mMenuHelper.assertWaitForMenu();
        assertButtonHasFocus(BUTTON_ID_PLAY_PAUSE);
        mDevice.pressBack();
    }

    public void testFocusedViewInMenu() {
        mMenuHelper.showMenu();
        mDevice.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY);
        assertButtonHasFocus(BUTTON_ID_PLAY_PAUSE);
        mMenuHelper.assertNavigateToRow(R.string.menu_title_channels);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_MEDIA_NEXT);
        assertButtonHasFocus(BUTTON_ID_PLAY_PAUSE);
    }

    public void testKeepPausedWhileParentalControlChange() {
        // Pause the playback.
        mDevice.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE);
        mMenuHelper.assertWaitForMenu();
        assertButtonHasFocus(BUTTON_ID_PLAY_PAUSE);
        // Show parental controls fragment.
        mMenuHelper.assertPressOptionsSettings();
        assertWaitForCondition(mDevice, Until.hasObject(mBySettingsSidePanel));
        mSidePanelHelper.assertNavigateToItem(R.string.settings_parental_controls);
        mDevice.pressDPadCenter();
        DialogHelper dialogHelper = new DialogHelper(mDevice, mTargetResources);
        dialogHelper.assertWaitForPinDialogOpen();
        dialogHelper.enterPinCodes();
        dialogHelper.assertWaitForPinDialogClose();
        BySelector bySidePanel = mSidePanelHelper.bySidePanelTitled(
                R.string.menu_parental_controls);
        assertWaitForCondition(mDevice, Until.hasObject(bySidePanel));
        mDevice.pressEnter();
        mDevice.pressEnter();
        mDevice.pressBack();
        mDevice.pressBack();
        // Return to the main menu.
        mMenuHelper.assertWaitForMenu();
        assertButtonHasFocus(BUTTON_ID_PLAY_PAUSE);
    }

    public void testKeepPausedAfterVisitingHome() {
        // Pause the playback.
        mDevice.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE);
        mMenuHelper.assertWaitForMenu();
        assertButtonHasFocus(BUTTON_ID_PLAY_PAUSE);
        // Press HOME twice to visit the home screen and return to Live TV.
        mDevice.pressHome();
        // Wait until home screen is shown.
        mDevice.waitForIdle();
        mDevice.pressHome();
        // Wait until TV is resumed.
        mDevice.waitForIdle();
        // Return to the main menu.
        mMenuHelper.assertWaitForMenu();
        assertButtonHasFocus(BUTTON_ID_PLAY_PAUSE);
    }

    private void assertButtonHasFocus(String buttonId) {
        UiObject2 menu = mDevice.findObject(MENU);
        UiObject2 focusedView = menu.findObject(FOCUSED_VIEW);
        assertNotNull("Play controls row doesn't have a focused child.", focusedView);
        UiObject2 focusedButtonGroup = focusedView.getParent();
        assertNotNull("The focused item should have parent", focusedButtonGroup);
        assertEquals(buttonId, focusedButtonGroup.getResourceName());
    }
}
