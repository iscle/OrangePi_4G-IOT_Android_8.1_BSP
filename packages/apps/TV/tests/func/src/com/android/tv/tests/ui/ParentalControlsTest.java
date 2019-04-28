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

package com.android.tv.tests.ui;

import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertWaitForCondition;

import android.support.test.filters.SmallTest;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import com.android.tv.R;
import com.android.tv.testing.uihelper.ByResource;
import com.android.tv.testing.uihelper.DialogHelper;

@SmallTest
public class ParentalControlsTest extends LiveChannelsTestCase {

    private BySelector mBySettingsSidePanel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mLiveChannelsHelper.assertAppStarted();
        mBySettingsSidePanel = mSidePanelHelper.bySidePanelTitled(
                R.string.side_panel_title_settings);
        prepareParentalControl();
    }

    @Override
    protected void tearDown() throws Exception {
        switchParentalControl(R.string.option_toggle_parental_controls_on);
        super.tearDown();
    }

    public void testRatingDependentSelect() {
        // Show ratings fragment.
        BySelector bySidePanel = mSidePanelHelper.bySidePanelTitled(
                R.string.option_program_restrictions);
        assertWaitForCondition(mDevice, Until.hasObject(bySidePanel));
        mSidePanelHelper.assertNavigateToItem(R.string.option_ratings);
        mDevice.pressDPadCenter();
        // Block rating 6 and rating 12. Check if dependent select works well.
        bySidePanel = mSidePanelHelper.bySidePanelTitled(R.string.option_ratings);
        assertWaitForCondition(mDevice, Until.hasObject(bySidePanel));
        // Test on blocking and unblocking Japanese rating.
        int blockAge = 6;
        int unBlockAge = 12;
        int maxAge = 20;
        int minAge = 4;
        for (int age = minAge; age <= maxAge; age++) {
            UiObject2 ratingCheckBox = mSidePanelHelper.assertNavigateToItem(String.valueOf(age))
                    .findObject(ByResource.id(mTargetResources, R.id.check_box));
            if (ratingCheckBox.isChecked()) {
                mDevice.pressDPadCenter();
            }
        }
        mSidePanelHelper.assertNavigateToItem(String.valueOf(blockAge));
        mDevice.pressDPadCenter();
        assertRatingViewIsChecked(minAge, maxAge, blockAge, true);
        mSidePanelHelper.assertNavigateToItem(String.valueOf(unBlockAge));
        mDevice.pressDPadCenter();
        assertRatingViewIsChecked(minAge, maxAge, unBlockAge, false);
        mDevice.pressBack();
        mDevice.pressBack();
        getInstrumentation().waitForIdleSync();
    }

    private void assertRatingViewIsChecked(int minAge, int maxAge, int selectedAge,
            boolean expectedValue) {
        for (int age = minAge; age <= maxAge; age++) {
            UiObject2 ratingCheckBox = mSidePanelHelper.assertNavigateToItem(String.valueOf(age))
                    .findObject(ByResource.id(mTargetResources, R.id.check_box));
            if (age < selectedAge) {
                assertTrue("The lower rating age should be unblocked", !ratingCheckBox.isChecked());
            } else if (age > selectedAge) {
                assertTrue("The higher rating age should be blocked", ratingCheckBox.isChecked());
            } else {
                assertEquals("The rating for age " + selectedAge + " isBlocked ", expectedValue,
                        ratingCheckBox.isChecked());
            }
        }
    }

    /**
     * Prepare the need for testRatingDependentSelect.
     * 1. Turn on parental control if it's off.
     * 2. Make sure Japan rating system is selected.
     */
    private void prepareParentalControl() {
        showParentalControl();
        switchParentalControl(R.string.option_toggle_parental_controls_off);
        // Show all rating systems.
        mSidePanelHelper.assertNavigateToItem(R.string.option_program_restrictions);
        mDevice.pressDPadCenter();
        BySelector bySidePanel = mSidePanelHelper.bySidePanelTitled(
                R.string.option_program_restrictions);
        assertWaitForCondition(mDevice, Until.hasObject(bySidePanel));
        mSidePanelHelper.assertNavigateToItem(R.string.option_country_rating_systems);
        mDevice.pressDPadCenter();
        bySidePanel = mSidePanelHelper.bySidePanelTitled(R.string.option_country_rating_systems);
        assertWaitForCondition(mDevice,Until.hasObject(bySidePanel));
        mSidePanelHelper.assertNavigateToItem(R.string.option_see_all_rating_systems);
        mDevice.pressDPadCenter();
        // Make sure Japan rating system is selected.
        UiObject2 ratingSystemCheckBox = mSidePanelHelper.assertNavigateToItem("Japan")
                .findObject(ByResource.id(mTargetResources, R.id.check_box));
        if (!ratingSystemCheckBox.isChecked()) {
            mDevice.pressDPadCenter();
            getInstrumentation().waitForIdleSync();
        }
        mDevice.pressBack();
    }

    private void switchParentalControl(int oppositeStateResId) {
        BySelector bySidePanel = mSidePanelHelper.byViewText(oppositeStateResId);
        if (mDevice.hasObject(bySidePanel)) {
            mSidePanelHelper.assertNavigateToItem(oppositeStateResId);
            mDevice.pressDPadCenter();
            getInstrumentation().waitForIdleSync();
        }
    }

    private void showParentalControl() {
        // Show menu and select parental controls.
        mMenuHelper.showMenu();
        mMenuHelper.assertPressOptionsSettings();
        assertWaitForCondition(mDevice, Until.hasObject(mBySettingsSidePanel));
        mSidePanelHelper.assertNavigateToItem(R.string.settings_parental_controls);
        mDevice.pressDPadCenter();
        // Enter pin code.
        DialogHelper dialogHelper = new DialogHelper(mDevice, mTargetResources);
        dialogHelper.assertWaitForPinDialogOpen();
        dialogHelper.enterPinCodes();
        dialogHelper.assertWaitForPinDialogClose();
        BySelector bySidePanel = mSidePanelHelper.bySidePanelTitled(
                R.string.menu_parental_controls);
        assertWaitForCondition(mDevice, Until.hasObject(bySidePanel));
    }
}
