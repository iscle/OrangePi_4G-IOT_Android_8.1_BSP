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

package com.android.tv.testing.uihelper;

import static com.android.tv.testing.uihelper.Constants.MENU;

import android.content.res.Resources;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import com.android.tv.R;

import junit.framework.Assert;

/**
 * Helper for testing {@link com.android.tv.menu.Menu}.
 */
public class MenuHelper extends BaseUiDeviceHelper {
    private final BySelector byChannels;

    public MenuHelper(UiDevice uiDevice, Resources targetResources) {
        super(uiDevice, targetResources);
        byChannels = ByResource.id(mTargetResources, R.id.item_list)
                .hasDescendant(ByResource.text(mTargetResources, R.string.menu_title_channels));
    }

    public BySelector getByChannels() {
        return byChannels;
    }


    /**
     * Navigate to the menu item with the text {@code itemTextResId} in the row with text
     * {@code rowTitleResId}.
     * <p>
     * Fails if the menu item can not be navigated to.
     *
     * @param rowTitleResId the resource id of the string in the desired row title.
     * @param itemTextResId the resource id of the string in the desired item.
     * @return the item navigated to.
     */
    public UiObject2 assertNavigateToMenuItem(int rowTitleResId, int itemTextResId) {
        UiObject2 row = assertNavigateToRow(rowTitleResId);
        BySelector byListView = ByResource.id(mTargetResources, R.id.list_view);
        UiObject2 listView = row.findObject(byListView);
        Assert.assertNotNull(
                "Menu row '" + mTargetResources.getString(rowTitleResId) + "' does not have a "
                        + byListView, listView);
        return assertNavigateToRowItem(listView, itemTextResId);
    }

    /**
     * Navigate to the menu row with the text title {@code rowTitleResId}.
     * <p>
     * Fails if the menu row can not be navigated to.
     * We can't navigate to the Play controls row with this method, because the row doesn't have the
     * title when it is selected. Use {@link #assertNavigateToPlayControlsRow} for the row instead.
     *
     * @param rowTitleResId the resource id of the string in the desired row title.
     * @return the row navigated to.
     */
    public UiObject2 assertNavigateToRow(int rowTitleResId) {
        UiDeviceAsserts.assertHas(mUiDevice, MENU, true);
        UiObject2 menu = mUiDevice.findObject(MENU);
        // TODO: handle play controls. They have a different dom structure and navigation sometimes
        // can get stuck on that row.
        return UiDeviceAsserts.assertNavigateTo(mUiDevice, menu,
                By.hasDescendant(ByResource.text(mTargetResources, rowTitleResId)), Direction.DOWN);
    }

    /**
     * Navigate to the Play controls row.
     * <p>
     * Fails if the row can not be navigated to.
     *
     * @see #assertNavigateToRow
     */
    public void assertNavigateToPlayControlsRow() {
        UiDeviceAsserts.assertHas(mUiDevice, MENU, true);
        // The play controls row doesn't have title when selected, so can't use
        // MenuHelper.assertNavigateToRow().
        assertNavigateToRow(R.string.menu_title_channels);
        mUiDevice.pressDPadUp();
    }

    /**
     * Navigate to the menu item in the given {@code row} with the text {@code itemTextResId} .
     * <p>
     * Fails if the menu item can not be navigated to.
     *
     * @param row           the container to look for menu items in.
     * @param itemTextResId the resource id of the string in the desired item.
     * @return the item navigated to.
     */
    public UiObject2 assertNavigateToRowItem(UiObject2 row, int itemTextResId) {
        return UiDeviceAsserts.assertNavigateTo(mUiDevice, row,
                By.hasDescendant(ByResource.text(mTargetResources, itemTextResId)),
                Direction.RIGHT);
    }

    public UiObject2 assertPressOptionsSettings() {
        return assertPressMenuItem(R.string.menu_title_options,
                R.string.options_item_settings);
    }

    public UiObject2 assertPressOptionsClosedCaptions() {
        return assertPressMenuItem(R.string.menu_title_options,
                R.string.options_item_closed_caption);
    }

    public UiObject2 assertPressOptionsDisplayMode() {
        return assertPressMenuItem(R.string.menu_title_options, R.string.options_item_display_mode);
    }

    public UiObject2 assertPressOptionsMultiAudio() {
        return assertPressMenuItem(R.string.menu_title_options, R.string.options_item_multi_audio);
    }

    public UiObject2 assertPressProgramGuide() {
        return assertPressMenuItem(R.string.menu_title_channels,
                R.string.channels_item_program_guide);
    }

    public UiObject2 assertPressDvrLibrary() {
        return assertPressMenuItem(R.string.menu_title_channels,
                R.string.channels_item_dvr);
    }

    /**
     * Navigate to the menu item with the text {@code itemTextResId} in the row with text
     * {@code rowTitleResId}.
     * <p>
     * Fails if the menu item can not be navigated to.
     *
     * @param rowTitleResId the resource id of the string in the desired row title.
     * @param itemTextResId the resource id of the string in the desired item.
     * @return the item navigated to.
     */
    public UiObject2 assertPressMenuItem(int rowTitleResId, int itemTextResId) {
        showMenu();
        UiObject2 item = assertNavigateToMenuItem(rowTitleResId, itemTextResId);
        mUiDevice.pressDPadCenter();
        return item;
    }

    /**
     * Waits until the menu is visible.
     */
    public void assertWaitForMenu() {
        UiDeviceAsserts.assertWaitForCondition(mUiDevice, Until.hasObject(MENU));
    }

    /**
    * Show the menu.
     * <p>
     * Fails if the menu does not appear in {@link Constants#MAX_SHOW_DELAY_MILLIS}.
     */
    public void showMenu() {
        if (!mUiDevice.hasObject(MENU)) {
            mUiDevice.pressMenu();
            if (!UiDeviceAsserts.waitForCondition(mUiDevice, Until.hasObject(MENU))) {
                // Sometimes animations might block menu key, try again to make sure it's received.
                mUiDevice.pressMenu();
                UiDeviceAsserts.assertWaitForCondition(mUiDevice, Until.hasObject(MENU));
            }
        }
    }
}
