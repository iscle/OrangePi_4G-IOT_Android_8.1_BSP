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

package com.android.wearable.sysapp.janktests;

import android.app.Instrumentation;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.util.Log;

import junit.framework.Assert;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class WatchFaceHelper {

    public static final long LONG_TIMEOUT = TimeUnit.SECONDS.toMillis(20);
    public static final long SHORT_TIMEOUT = TimeUnit.MILLISECONDS.toMillis(500);
    private static final String WATCHFACE_PREVIEW_NAME = "preview_image";
    private static final String WATCHFACE_SHOW_ALL_BTN_NAME = "show_all_btn";
    private static final String WATCHFACE_PICKER_ALL_LIST_NAME = "watch_face_picker_all_list";
    private static final String WATCHFACE_PICKER_FAVORITE_LIST_NAME = "watch_face_picker_list";
    private static final String PERMISSION_ALLOW_BUTTON_ID =
            "com.android.packageinstaller:id/permission_allow_button";
    private static final String TAG = WatchFaceHelper.class.getSimpleName();

    private static WatchFaceHelper sWatchFaceHelperInstance;
    private SysAppTestHelper mHelper;
    private UiDevice mDevice;
    private Instrumentation mInstrumentation;

    /**
     * @param mDevice Instance to represent the current device.
     * @param instrumentation Instance for instrumentation.
     */
    private WatchFaceHelper(UiDevice device, Instrumentation instrumentation) {
        this.mDevice = device;
        this.mHelper = SysAppTestHelper.getInstance(mDevice, instrumentation);
    }

    public static WatchFaceHelper getInstance(UiDevice device, Instrumentation instrumentation) {
        if (sWatchFaceHelperInstance == null) {
            sWatchFaceHelperInstance = new WatchFaceHelper(device, instrumentation);
        }
        return sWatchFaceHelperInstance;
    }

    /**
     * Check if there is only one watch face in favorites list.
     *
     * @return True is +id/show_all_btn is on the screen. False otherwise.
     */
    public boolean isOnlyOneWatchFaceInFavorites() {
        // Make sure we are not at the last watch face.
        mHelper.swipeRight();
        return mHelper.waitForSysAppUiObject2(WATCHFACE_SHOW_ALL_BTN_NAME) != null;
    }

    public void openPicker() {
        // Try 5 times in case WFP is not ready
        for (int i = 0; i < 5; i++) {
            mHelper.swipeRight();
            if (mHelper.waitForSysAppUiObject2(WATCHFACE_PREVIEW_NAME) != null) {
                return;
            }
        }
        Assert.fail("Still cannot open WFP after several attempts");
    }

    public void openPickerAllList() {
        // Assume the screen is on WatchFace picker for favorites.
        // Swipe to the end of the list.
        while (mHelper.waitForSysAppUiObject2(WATCHFACE_SHOW_ALL_BTN_NAME) == null) {
            Log.v(TAG, "Swiping to the end of favorites list ...");
            mHelper.flingLeft();
        }

        UiObject2 showAllButton = mHelper.waitForSysAppUiObject2(WATCHFACE_SHOW_ALL_BTN_NAME);
        Assert.assertNotNull(showAllButton);
        Log.v(TAG, "Tapping to show all watchfaces ...");
        showAllButton.click();
        UiObject2 watchFacePickerAllList =
                mHelper.waitForSysAppUiObject2(WATCHFACE_PICKER_ALL_LIST_NAME);
        Assert.assertNotNull(watchFacePickerAllList);
    }

    public void selectWatchFaceFromFullList(int index) {
        // Assume the screen is on watch face picker for all faces.
        UiObject2 watchFacePickerAllList = mHelper.waitForSysAppUiObject2(
                WATCHFACE_PICKER_ALL_LIST_NAME);
        Assert.assertNotNull(watchFacePickerAllList);
        int swipes = index / 4; // Showing 4 for each scroll.
        for (int i = 0; i < swipes; ++i) {
            mHelper.swipeDown();
        }
        List<UiObject2> watchFaces = watchFacePickerAllList.getChildren();
        Assert.assertNotNull(watchFaces);
        int localIndex = index % 4;
        if (watchFaces.size() <= localIndex) {
            mDevice.pressBack();
            return;
        }

        Log.v(TAG, "Tapping the " + localIndex + " watchface on screen ...");
        watchFaces.get(localIndex).click();
        // Verify the watchface is selected properly.
        UiObject2 watchFacePickerList =
                mHelper.waitForSysAppUiObject2(WATCHFACE_PICKER_FAVORITE_LIST_NAME);
        Assert.assertNotNull(watchFacePickerList);

        dismissPermissionDialogs();
    }

    public void selectWatchFaceFromFullList(String watchFaceName) throws UiObjectNotFoundException {
        UiScrollable watchFacePickerAllList = new UiScrollable(new UiSelector().scrollable(true));
        Assert.assertNotNull(watchFacePickerAllList);
        watchFacePickerAllList.scrollTextIntoView(watchFaceName);

        // Select from full list
        UiObject2 watchFace = mDevice.wait(Until.findObject(By.text(watchFaceName)), SHORT_TIMEOUT);
        Assert.assertNotNull(watchFace);
        watchFace.click();

        // Select from favorites list
        watchFace = mDevice.wait(Until.findObject(By.desc(watchFaceName)), SHORT_TIMEOUT);
        Assert.assertNotNull(watchFace);
        watchFace.click();

        // Dismiss all permission dialogs
        dismissPermissionDialogs();
    }

    public void startFromWatchFacePicker() {
        Log.v(TAG, "Starting from watchface picker ...");
        mHelper.goBackHome();
        openPicker();
    }

    public void removeAllButOneWatchFace() {
        int count = 0;
        do {
            Log.v(TAG, "Removing all but one watch faces ...");
            startFromWatchFacePicker();
            removeWatchFaceFromFavorites();
        } while (isOnlyOneWatchFaceInFavorites() && count++ < 5);
    }

    public void removeWatchFaceFromFavorites() {
        mHelper.flingRight();

        // Assume the favorites list has at least 2 watch faces.
        UiObject2 watchFacePicker =
                mHelper.waitForSysAppUiObject2(WATCHFACE_PICKER_FAVORITE_LIST_NAME);
        Assert.assertNotNull(watchFacePicker);
        List<UiObject2> watchFaces = watchFacePicker.getChildren();
        Assert.assertNotNull(watchFaces);
        if (isOnlyOneWatchFaceInFavorites()) {
            return;
        }

        Log.v(TAG, "Removing first watch face from favorites ...");
        watchFaces.get(0).swipe(Direction.DOWN, 1.0f);
    }

    private void dismissPermissionDialogs() {
        UiObject2 permissionDialog = null;
        do {
            permissionDialog = mDevice.wait(
                    Until.findObject(By.res(PERMISSION_ALLOW_BUTTON_ID)),
                    SHORT_TIMEOUT);

            if (permissionDialog != null) {
                permissionDialog.click();
            }
        } while (permissionDialog != null);
    }
}
