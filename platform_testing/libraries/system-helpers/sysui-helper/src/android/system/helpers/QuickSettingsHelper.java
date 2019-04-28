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

package android.system.helpers;

import android.app.Instrumentation;
import android.content.ContentResolver;
import android.graphics.Point;
import android.provider.Settings;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import org.junit.Assert;

/**
 * Implement common helper methods for Quick settings.
 */
public class QuickSettingsHelper {

    private UiDevice mDevice = null;
    private ContentResolver mResolver;
    private Instrumentation mInstrumentation;
    private static final int LONG_TIMEOUT = 2000;
    private static final int SHORT_TIMEOUT = 500;

    public QuickSettingsHelper(UiDevice device, Instrumentation inst, ContentResolver resolver) {
        this.mDevice = device;
        mInstrumentation = inst;
        mResolver = resolver;
    }

    public enum QuickSettingDefaultTiles {
        WIFI("Wi-Fi"), SIM("Mobile data"), DND("Do not disturb"), FLASHLIGHT("Flashlight"), SCREEN(
                "Auto-rotate screen"), BLUETOOTH("Bluetooth"), AIRPLANE("Airplane mode"),
                BRIGHTNESS("Display brightness");

        private final String name;

        private QuickSettingDefaultTiles(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    };

    public enum QuickSettingEditMenuTiles {
        LOCATION("Location"), HOTSPOT("Hotspot"), INVERTCOLORS("Invert colors"),
                DATASAVER("Data Saver"), CAST("Cast"), NEARBY("Nearby");

        private final String name;

        private QuickSettingEditMenuTiles(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    };

    public void addQuickSettingTileFromEditMenu(String quickSettingTile,
            String quickSettingTileToReplace, String quickSettingTileToCheckForInCSV)
            throws Exception {
        // Draw down quick settings
        launchQuickSetting();
        // Press Edit button
        UiObject2 quickSettingEdit = mDevice.wait(Until.findObject
                (By.descContains("Edit")), LONG_TIMEOUT);
        quickSettingEdit.click();
        // Scroll down to bottom to see all QS options on Edit
        swipeDown();
        // Drag and drop QS item onto existing QS tile to replace it
        // This is because we need specific coordinates on which to
        // drop the quick setting tile.
        UiObject2 quickSettingTileObject = mDevice.wait(Until.findObject
                (By.descContains(quickSettingTile)), LONG_TIMEOUT);
        Point destination = mDevice.wait(Until.findObject
                (By.descContains(quickSettingTileToReplace)), LONG_TIMEOUT)
                .getVisibleCenter();
        Assert.assertNotNull(quickSettingTile + " in Edit menu can't be found",
                quickSettingTileObject);
        Assert.assertNotNull(quickSettingTileToReplace + " in QS menu can't be found",
                destination);
        // Long press the icon, then drag it to the destination slowly.
        // Without the long press, it ends up scrolling down quick settings.
        quickSettingTileObject.click(2000);
        quickSettingTileObject.drag(destination, 1000);
        // Hit the back button in the QS menu to go back to quick settings.
        mDevice.wait(Until.findObject(By.descContains("Navigate up")), LONG_TIMEOUT);
        // Retrieve the quick settings CSV string and verify that the newly
        // added item is present.
        String quickSettingsList = Settings.Secure.getString
                (mInstrumentation.getContext().getContentResolver(),
                "sysui_qs_tiles");
        Assert.assertTrue(quickSettingTile + " not present in qs tiles after addition.",
                quickSettingsList.contains(quickSettingTileToCheckForInCSV));
    }

    public void setQuickSettingsDefaultTiles() throws Exception {
        modifyListOfQuickSettingsTiles
                ("wifi,cell,battery,dnd,flashlight,rotation,bt,airplane,location");
    }

    public void modifyListOfQuickSettingsTiles(String commaSeparatedList) throws Exception {
        Settings.Secure.putString(mInstrumentation.getContext().getContentResolver(),
                "sysui_qs_tiles", commaSeparatedList);
        Thread.sleep(LONG_TIMEOUT);
    }

    public void launchQuickSetting() throws Exception {
        mDevice.pressHome();
        swipeDown();
        Thread.sleep(LONG_TIMEOUT);
        swipeDown();
    }

    public void swipeUp() throws Exception {
        mDevice.swipe(mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight(),
                mDevice.getDisplayWidth() / 2, 0, 30);
        Thread.sleep(SHORT_TIMEOUT);
    }

    public void swipeDown() throws Exception {
        mDevice.swipe(mDevice.getDisplayWidth() / 2, 0, mDevice.getDisplayWidth() / 2,
                mDevice.getDisplayHeight() / 2 + 50, 20);
        Thread.sleep(SHORT_TIMEOUT);
    }

    public void swipeLeft() {
        mDevice.swipe(mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight() / 2, 0,
                mDevice.getDisplayHeight() / 2, 5);
    }
}
