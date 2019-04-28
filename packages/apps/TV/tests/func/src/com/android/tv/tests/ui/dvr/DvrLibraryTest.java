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

package com.android.tv.tests.ui.dvr;

import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertHas;
import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertWaitForCondition;
import static com.android.tv.testing.uihelper.UiDeviceAsserts.assertWaitUntilFocused;

import android.os.Build;
import android.support.test.filters.MediumTest;
import android.support.test.filters.SdkSuppress;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import com.android.tv.R;
import com.android.tv.testing.uihelper.ByResource;
import com.android.tv.testing.uihelper.Constants;
import com.android.tv.tests.ui.LiveChannelsTestCase;

import java.util.regex.Pattern;

@MediumTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
public class DvrLibraryTest extends LiveChannelsTestCase {
    private static final String PROGRAM_NAME_PREFIX = "Title(";

    private BySelector mRecentRow;
    private BySelector mScheduledRow;
    private BySelector mSeriesRow;
    private BySelector mFullScheduleCard;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRecentRow = By.hasDescendant(ByResource.text(mTargetResources, R.string.dvr_main_recent));
        mScheduledRow = By.hasDescendant(
                ByResource.text(mTargetResources, R.string.dvr_main_scheduled));
        mSeriesRow = By.hasDescendant(ByResource.text(mTargetResources, R.string.dvr_main_series));
        mFullScheduleCard = By.focusable(true).hasDescendant(
                ByResource.text(mTargetResources, R.string.dvr_full_schedule_card_view_title));
        mLiveChannelsHelper.assertAppStarted();
    }

    public void testCancel() {
        mMenuHelper.assertPressDvrLibrary();
        assertWaitForCondition(mDevice, Until.hasObject(Constants.DVR_LIBRARY));
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.gone(Constants.DVR_LIBRARY));
        assertHas(mDevice, Constants.MENU, false);
    }

    public void testEmptyLibrary() {
        mMenuHelper.assertPressDvrLibrary();
        assertWaitForCondition(mDevice, Until.hasObject(Constants.DVR_LIBRARY));

        // DVR Library is empty, only Scheduled row and Full schedule card should be displayed.
        assertHas(mDevice, mRecentRow, false);
        assertHas(mDevice, mScheduledRow, true);
        assertHas(mDevice, mSeriesRow, false);

        mDevice.pressDPadCenter();
        assertWaitUntilFocused(mDevice, mFullScheduleCard);
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.gone(Constants.DVR_LIBRARY));

        // Empty schedules screen should be shown.
        assertHas(mDevice, Constants.DVR_SCHEDULES, true);
        assertHas(mDevice, ByResource.text(mTargetResources, R.string.dvr_schedules_empty_state),
                true);

        // Close the DVR library.
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.hasObject(Constants.DVR_LIBRARY));
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.gone(Constants.DVR_LIBRARY));
    }

    public void testScheduleRecordings() {
        BySelector newScheduleCard = By.focusable(true).hasDescendant(
                By.textStartsWith(PROGRAM_NAME_PREFIX)).hasDescendant(By.textEndsWith("today"));
        BySelector seriesCardWithOneSchedule = By.focusable(true).hasDescendant(
                By.textStartsWith(PROGRAM_NAME_PREFIX)).hasDescendant(By.text(mTargetResources
                        .getQuantityString(R.plurals.dvr_count_scheduled_recordings, 1, 1)));
        BySelector seriesCardWithOneRecordedProgram = By.focusable(true).hasDescendant(
                By.textStartsWith(PROGRAM_NAME_PREFIX)).hasDescendant(By.text(mTargetResources
                        .getQuantityString(R.plurals.dvr_count_new_recordings, 1, 1)));
        Pattern watchButton = Pattern.compile("^" + mTargetResources
                .getString(R.string.dvr_detail_watch).toUpperCase() + "\n.*$");

        mMenuHelper.showMenu();
        mMenuHelper.assertNavigateToPlayControlsRow();
        mDevice.pressDPadRight();
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.hasObject(
                ByResource.text(mTargetResources, R.string.dvr_action_record_episode)));
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.gone(
                ByResource.text(mTargetResources, R.string.dvr_action_record_episode)));

        mMenuHelper.assertPressDvrLibrary();
        assertWaitForCondition(mDevice, Until.hasObject(Constants.DVR_LIBRARY));

        // Schedule should be automatically added to the series.
        assertHas(mDevice, mRecentRow, false);
        assertHas(mDevice, mScheduledRow, true);
        assertHas(mDevice, mSeriesRow, true);
        String programName = mDevice.findObject(By.textStartsWith(PROGRAM_NAME_PREFIX)).getText();

        // Move to scheduled row, there should be one new schedule and one full schedule card.
        mDevice.pressDPadRight();
        assertWaitUntilFocused(mDevice, newScheduleCard);
        mDevice.pressDPadRight();
        assertWaitUntilFocused(mDevice, mFullScheduleCard);

        // Enters the full schedule, there should be one schedule in the full schedule.
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.gone(Constants.DVR_LIBRARY));
        assertHas(mDevice, Constants.DVR_SCHEDULES, true);
        assertHas(mDevice, ByResource.text(mTargetResources, R.string.dvr_schedules_empty_state),
                false);
        assertHas(mDevice, By.textStartsWith(programName), true);

        // Moves to the series card, clicks it, the detail page should be shown with "View schedule"
        // button.
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.hasObject(Constants.DVR_LIBRARY));
        mDevice.pressDPadLeft();
        assertWaitUntilFocused(mDevice, newScheduleCard);
        mDevice.pressDPadDown();
        assertWaitUntilFocused(mDevice, seriesCardWithOneSchedule);
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.gone(Constants.DVR_LIBRARY));
        assertHas(mDevice, By.text(mTargetResources
                        .getString(R.string.dvr_detail_view_schedule).toUpperCase()), true);
        assertHas(mDevice, By.text(watchButton), false);
        assertHas(mDevice, By.text(mTargetResources
                        .getString(R.string.dvr_detail_series_delete).toUpperCase()), false);

        // Clicks the new schedule, the detail page should be shown with "Stop recording" button.
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.hasObject(Constants.DVR_LIBRARY));
        assertWaitUntilFocused(mDevice, seriesCardWithOneSchedule);
        mDevice.pressDPadUp();
        assertWaitUntilFocused(mDevice, newScheduleCard);
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.gone(Constants.DVR_LIBRARY));
        assertHas(mDevice, By.text(mTargetResources
                .getString(R.string.dvr_detail_stop_recording).toUpperCase()), true);

        // Stops the recording
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.hasObject(
                ByResource.text(mTargetResources, R.string.dvr_action_stop)));
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.gone(
                ByResource.text(mTargetResources, R.string.dvr_action_stop)));
        assertWaitForCondition(mDevice, Until.hasObject(Constants.DVR_LIBRARY));
        assertWaitUntilFocused(mDevice, mFullScheduleCard);

        // Moves to series' detail page again, now it should have two more buttons
        mDevice.pressDPadDown();
        assertWaitUntilFocused(mDevice, seriesCardWithOneRecordedProgram);
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.gone(Constants.DVR_LIBRARY));
        assertHas(mDevice, By.text(watchButton), true);
        assertHas(mDevice, By.text(mTargetResources
                .getString(R.string.dvr_detail_view_schedule).toUpperCase()), true);
        assertHas(mDevice, By.text(mTargetResources
                .getString(R.string.dvr_detail_series_delete).toUpperCase()), true);

        // Moves to the recent row and clicks the recent recorded program.
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.hasObject(Constants.DVR_LIBRARY));
        assertWaitUntilFocused(mDevice, seriesCardWithOneRecordedProgram);
        mDevice.pressDPadUp();
        assertWaitUntilFocused(mDevice, mFullScheduleCard);
        mDevice.pressDPadUp();
        assertWaitUntilFocused(mDevice, By.focusable(true).hasDescendant(By.text(programName)));
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.gone(Constants.DVR_LIBRARY));
        assertHas(mDevice, By.text(mTargetResources
                .getString(R.string.dvr_detail_watch).toUpperCase()), true);
        assertHas(mDevice, By.text(mTargetResources
                .getString(R.string.dvr_detail_delete).toUpperCase()), true);

        // Moves to the delete button and clicks to remove the recorded program.
        mDevice.pressDPadRight();
        assertWaitUntilFocused(mDevice, By.text(mTargetResources
                .getString(R.string.dvr_detail_delete).toUpperCase()));
        mDevice.pressDPadCenter();
        assertWaitForCondition(mDevice, Until.hasObject(Constants.DVR_LIBRARY));
        assertWaitUntilFocused(mDevice, mFullScheduleCard);

        // DVR Library should be empty now.
        assertHas(mDevice, mRecentRow, false);
        assertHas(mDevice, mScheduledRow, true);
        assertHas(mDevice, mSeriesRow, false);

        // Close the DVR library.
        mDevice.pressBack();
        assertWaitForCondition(mDevice, Until.gone(Constants.DVR_LIBRARY));
    }
}
