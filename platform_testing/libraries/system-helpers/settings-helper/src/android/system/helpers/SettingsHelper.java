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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.widget.Switch;
import android.widget.TextView;

import junit.framework.Assert;

import java.util.regex.Pattern;

/**
 * Implement common helper methods for settings.
 */
public class SettingsHelper {
    private static final String TAG = SettingsHelper.class.getSimpleName();
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String SETTINGS_APP = "Settings";
    private static final String SWITCH_WIDGET = "switch_widget";
    private static final String WIFI = "Wi-Fi";
    private static final String BLUETOOTH = "Bluetooth";
    private static final String AIRPLANE = "Airplane mode";
    private static final String LOCATION = "Location";
    private static final String DND = "Do not disturb";
    private static final String ZEN_MODE = "zen_mode";
    private static final String FLASHLIGHT = "Flashlight";
    private static final String AUTO_ROTATE_SCREEN = "Auto-rotate screen";
    private static final BySelector SETTINGS_DASHBOARD = By.res(SETTINGS_PACKAGE,
            "dashboard_container");
    private static final UiSelector LIST_ITEM_VALUE =
            new UiSelector().className(TextView.class);
    public static final int TIMEOUT = 2000;
    private static SettingsHelper sInstance = null;
    private ActivityHelper mActivityHelper = null;
    private ContentResolver mResolver = null;
    private Context mContext = null;
    private UiDevice mDevice = null;

    public SettingsHelper() {
        mContext = InstrumentationRegistry.getTargetContext();
        mResolver = mContext.getContentResolver();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mActivityHelper = ActivityHelper.getInstance();
    }

    public static SettingsHelper getInstance() {
        if (sInstance == null) {
            sInstance = new SettingsHelper();
        }
        return sInstance;
    }

    public static enum SettingsType {
        SYSTEM, SECURE, GLOBAL
    }

    /**
     * @return Settings package name
     */
    public String getPackage() {
        return SETTINGS_PACKAGE;
    }

    /**
     * @return Settings app name
     */
    public String getLauncherName() {
        return SETTINGS_APP;
    }

    /**
     * Scroll through settings page
     * @param numberOfFlings
     * @throws Exception
     */
    public void scrollThroughSettings(int numberOfFlings) throws Exception {
        UiObject2 settingsList = loadAllSettings();
        int count = 0;
        while (count <= numberOfFlings && settingsList.fling(Direction.DOWN)) {
            count++;
        }
    }

    /**
     * Move to top of settings page
     * @throws Exception
     */
    public void flingSettingsToStart() throws Exception {
        UiObject2 settingsList = loadAllSettings();
        while (settingsList.fling(Direction.UP));
    }

    /**
     * Launch specific settings page
     * @param ctx
     * @param pageName
     * @throws Exception
     */
    public static void launchSettingsPage(Context ctx, String pageName) throws Exception {
        Intent intent = new Intent(pageName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
        Thread.sleep(TIMEOUT * 2);
    }

    /**
     * Scroll vertically up or down
     * @param isUp
     */
    public void scrollVert(boolean isUp) {
        int w = mDevice.getDisplayWidth();
        int h = mDevice.getDisplayHeight();
        mDevice.swipe(w / 2, h / 2, w / 2, isUp ? h : 0, 2);
    }

    /**
     * On N, the settingsDashboard is initially collapsed, and the user can see the "See all"
     * element. On hitting "See all", the same settings dashboard element is now scrollable. For
     * pre-N, the settings Dashboard is always scrollable, hence the check in the while loop. All
     * this method does is expand the Settings list if needed, before returning the element.
     */
    public UiObject2 loadAllSettings() throws Exception {
        UiObject2 settingsDashboard = mDevice.wait(Until.findObject(SETTINGS_DASHBOARD),
                TIMEOUT * 2);
        Assert.assertNotNull("Could not find the settings dashboard object.", settingsDashboard);
        int count = 0;
        while (!settingsDashboard.isScrollable() && count <= 2) {
            mDevice.wait(Until.findObject(By.text("SEE ALL")), TIMEOUT * 2).click();
            settingsDashboard = mDevice.wait(Until.findObject(SETTINGS_DASHBOARD),
                    TIMEOUT * 2);
            count++;
        }
        return settingsDashboard;
    }

    /**
     * Performs click action on a setting when setting name is provided as exact string
     * @param settingName
     * @throws InterruptedException
     */
    public void clickSetting(String settingName) throws InterruptedException {
        int count = 5;
        while (count > 0 && mDevice.wait(Until.findObject(By.text(settingName)), TIMEOUT) == null) {
            scrollVert(false);
            count--;
        }
        mDevice.wait(Until.findObject(By.text(settingName)), TIMEOUT).click();
        Thread.sleep(TIMEOUT);
    }

    /**
     * Performs click action on a setting when setting has been found
     * @param name
     * @throws InterruptedException,UiObjectNotFoundException
     */
    public boolean selectSettingFor(String settingName)
            throws InterruptedException, UiObjectNotFoundException {
        UiScrollable settingsList = new UiScrollable(
                new UiSelector().resourceId("android:id/content"));
        UiObject appSettings = settingsList.getChildByText(LIST_ITEM_VALUE, settingName);
        if (appSettings != null) {
            return appSettings.click();
        }
        return false;
    }

    /**
     * Performs click action on a setting when setting name is provided as pattern
     *
     * @param settingName
     * @throws InterruptedException
     */
    public void clickSetting(Pattern settingName) throws InterruptedException {
        mDevice.wait(Until.findObject(By.text(settingName)), TIMEOUT).click();
        Thread.sleep(400);
    }

    /**
     * Gets string value of a setting
     * @param type
     * @param sName
     * @return
     */
    public String getStringSetting(SettingsType type, String sName) {
        switch (type) {
            case SYSTEM:
                return Settings.System.getString(mResolver, sName);
            case GLOBAL:
                return Settings.Global.getString(mResolver, sName);
            case SECURE:
                return Settings.Secure.getString(mResolver, sName);
        }
        return "";
    }

    /**
     * Get int value of a setting
     * @param type
     * @param sName
     * @return
     * @throws SettingNotFoundException
     */
    public int getIntSetting(SettingsType type, String sName) throws SettingNotFoundException {
        switch (type) {
            case SYSTEM:
                return Settings.System.getInt(mResolver, sName);
            case GLOBAL:
                return Settings.Global.getInt(mResolver, sName);
            case SECURE:
                return Settings.Secure.getInt(mResolver, sName);
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Set string value of a setting
     * @param type
     * @param sName
     * @param value
     */
    public void setStringSetting(SettingsType type, String sName, String value)
            throws InterruptedException {
        switch (type) {
            case SYSTEM:
                Settings.System.putString(mResolver, sName, value);
                break;
            case GLOBAL:
                Settings.Global.putString(mResolver, sName, value);
                break;
            case SECURE:
                Settings.Secure.putString(mResolver, sName, value);
                break;
        }
        Thread.sleep(TIMEOUT);
    }

    /**
     * Sets int value of a setting
     * @param type
     * @param sName
     * @param value
     */
    public void setIntSetting(SettingsType type, String sName, int value)
            throws InterruptedException {
        switch (type) {
            case SYSTEM:
                Settings.System.putInt(mResolver, sName, value);
                break;
            case GLOBAL:
                Settings.Global.putInt(mResolver, sName, value);
                break;
            case SECURE:
                Settings.Secure.putInt(mResolver, sName, value);
                break;
        }
        Thread.sleep(TIMEOUT);
    }

    /**
     * Toggles setting and verifies the action, when setting name is passed as string
     * @param type
     * @param settingAction
     * @param settingName
     * @param internalName
     * @return
     * @throws Exception
     */
    public boolean verifyToggleSetting(SettingsType type, String settingAction,
            String settingName, String internalName) throws Exception {
        return verifyToggleSetting(
                type, settingAction, Pattern.compile(settingName), internalName, true);
    }

    /**
     * Toggles setting and verifies the action, when setting name is passed as pattern
     * @param type
     * @param settingAction
     * @param settingName
     * @param internalName
     * @return
     * @throws Exception
     */
    public boolean verifyToggleSetting(SettingsType type, String settingAction,
            Pattern settingName, String internalName) throws Exception {
        return verifyToggleSetting(type, settingAction, settingName, internalName, true);
    }

    /**
     * Toggles setting and verifies the action, when setting name is passed as string
     * and settings page needs to be launched or not
     * @param type
     * @param settingAction
     * @param settingName
     * @param internalName
     * @param doLaunch
     * @return
     * @throws Exception
     */
    public boolean verifyToggleSetting(SettingsType type, String settingAction,
            String settingName, String internalName, boolean doLaunch) throws Exception {
        return verifyToggleSetting(
                type, settingAction, Pattern.compile(settingName), internalName, doLaunch);
    }

    /**
     * Toggles setting and verifies the action
     * @param type
     * @param settingAction
     * @param settingName
     * @param internalName
     * @param doLaunch
     * @return
     * @throws Exception
     */
    public boolean verifyToggleSetting(SettingsType type, String settingAction,
            Pattern settingName, String internalName, boolean doLaunch) throws Exception {
        String onSettingBaseVal = getStringSetting(type, internalName);
        if (onSettingBaseVal == null) {
            // Per bug b/35717943 default for charging sounds is ON
            // So if null, the value should be set to 1.
            if (settingName.matcher("Charging sounds").matches()) {
                onSettingBaseVal = "1";
            }
            else {
                onSettingBaseVal = "0";
            }
        }
        int onSetting = Integer.parseInt(onSettingBaseVal);
        Log.d(TAG, "On Setting value is : " + onSetting);
        if (doLaunch) {
            launchSettingsPage(mContext, settingAction);
        }
        clickSetting(settingName);
        Log.d(TAG, "Clicked setting : " + settingName);
        Thread.sleep(5000);
        String changedSetting = getStringSetting(type, internalName);
        Log.d(TAG, "Changed Setting value is : " + changedSetting);
        if (changedSetting == null) {
            Log.d(TAG, "Changed Setting value is : NULL");
            changedSetting = "0";
        }
        return (1 - onSetting) == Integer.parseInt(changedSetting);
    }

    /**
     * @param type
     * @param settingAction
     * @param baseName
     * @param settingName
     * @param internalName
     * @param testVal
     * @return
     * @throws Exception
     */
    public boolean verifyRadioSetting(SettingsType type, String settingAction,
            String baseName, String settingName,
            String internalName, String testVal) throws Exception {
        if (baseName != null)
            clickSetting(baseName);
        clickSetting(settingName);
        Thread.sleep(500);
        return getStringSetting(type, internalName).equals(testVal);
    }

    public void toggleWiFiOnOffAndVerify(boolean verifyOn, boolean isQuickSettings)
            throws Exception {
        String switchText = (verifyOn ? "OFF" : "ON");
        WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(!verifyOn);
        Thread.sleep(TIMEOUT * 3);
        if (isQuickSettings) {
            launchAndClickSettings(isQuickSettings, null, By.descContains(WIFI)
                    .clazz(Switch.class));
        } else {
            launchAndClickSettings(isQuickSettings, Settings.ACTION_WIFI_SETTINGS,
                    By.res(SETTINGS_PACKAGE, SWITCH_WIDGET).text(switchText));
        }
        Thread.sleep(TIMEOUT * 3);
        String wifiValue = Settings.Global.getString(mResolver, Settings.Global.WIFI_ON);
        if (verifyOn) {
            Assert.assertFalse(wifiValue == "0");
        } else {
            Assert.assertEquals("0", wifiValue);
        }
        mDevice.pressHome();
        Thread.sleep(TIMEOUT * 3);
    }

    public void toggleBTOnOffAndVerify(boolean verifyOn, boolean isQuickSettings)
            throws Exception {
        String switchText = (verifyOn ? "OFF" : "ON");
        BluetoothAdapter bluetoothAdapter = ((BluetoothManager) mContext
                .getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        boolean isEnabled = bluetoothAdapter.isEnabled();
        boolean success = (verifyOn ? bluetoothAdapter.disable() : bluetoothAdapter.enable());
        Thread.sleep(TIMEOUT * 3);
        if (isQuickSettings) {
            launchAndClickSettings(isQuickSettings, null,
                    By.descContains(BLUETOOTH).clazz(Switch.class));
        } else {
            launchAndClickSettings(isQuickSettings, Settings.ACTION_BLUETOOTH_SETTINGS,
                    By.res(SETTINGS_PACKAGE, SWITCH_WIDGET).text(switchText));
        }
        Thread.sleep(TIMEOUT * 3);
        String bluetoothValue = Settings.Global.getString(
                mResolver,
                Settings.Global.BLUETOOTH_ON);
        Assert.assertEquals((verifyOn ? "1" : "0"), bluetoothValue);
        if (isEnabled) {
            bluetoothAdapter.enable();
        } else {
            bluetoothAdapter.disable();
        }
        mDevice.pressHome();
        Thread.sleep(TIMEOUT * 3);
    }

    public void toggleAirplaneModeOnOrOffAndVerify(boolean verifyOn, boolean isQuickSettings)
            throws Exception {
        String settingValToPut = (verifyOn ? "0" : "1");
        Settings.Global.putString(mResolver, Settings.Global.AIRPLANE_MODE_ON, settingValToPut);
        if (isQuickSettings) {
            launchAndClickSettings(isQuickSettings, null, By.descContains(AIRPLANE));
        } else {
            launchAndClickSettings(isQuickSettings, Settings.ACTION_WIRELESS_SETTINGS,
                    By.text(AIRPLANE));
        }
        Thread.sleep(TIMEOUT * 3);
        String airplaneModeValue = Settings.Global
                .getString(mResolver,
                        Settings.Global.AIRPLANE_MODE_ON);
        Assert.assertEquals((verifyOn ? "1" : "0"), airplaneModeValue);
        mDevice.pressHome();
        Thread.sleep(TIMEOUT * 3);
    }

    public void toggleLocationSettingsOnOrOffAndVerify(boolean verifyOn, boolean isQuickSettings)
            throws Exception {
        // Set location flag
        int settingValToPut = (verifyOn ? Settings.Secure.LOCATION_MODE_OFF
                : Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
        Settings.Secure.putInt(mResolver, Settings.Secure.LOCATION_MODE, settingValToPut);
        // Load location settings
        if (isQuickSettings) {
            launchAndClickSettings(isQuickSettings, null, By.descContains(LOCATION));
        } else {
            launchAndClickSettings(isQuickSettings, Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                    By.res(SETTINGS_PACKAGE, SWITCH_WIDGET));
        }
        Thread.sleep(TIMEOUT * 3);
        // Verify change in setting
        int locationEnabled = Settings.Secure.getInt(mResolver,
                Settings.Secure.LOCATION_MODE);
        if (verifyOn) {
            Assert.assertFalse("Location not enabled correctly", locationEnabled == 0);
        } else {
            Assert.assertEquals("Location not disabled correctly", 0, locationEnabled);
        }
        mDevice.pressHome();
        Thread.sleep(TIMEOUT * 3);
    }

    public void launchAndClickSettings(boolean isQuickSettings, String settingsPage,
            BySelector bySelector) throws Exception {
        if (isQuickSettings) {
            launchQuickSettingsAndWait();
            UiObject2 qsTile = mDevice.wait(Until.findObject(bySelector), TIMEOUT * 3);
            qsTile.findObject(By.clazz("android.widget.FrameLayout")).click();
        } else {
            mActivityHelper.launchIntent(settingsPage);
            mDevice.wait(Until.findObject(bySelector), TIMEOUT * 3).click();
        }
    }

    /**
     * Verify Quick Setting DND can be toggled DND default value is OFF
     * @throws Exception
     */
    public void toggleQuickSettingDNDAndVerify() throws Exception {
        try {
            int onSetting = Settings.Global.getInt(mResolver, ZEN_MODE);
            launchQuickSettingsAndWait();
            mDevice.wait(Until.findObject(By.descContains(DND).clazz(Switch.class)),
                    TIMEOUT * 3).getChildren().get(0).click();
            Thread.sleep(TIMEOUT * 3);
            int changedSetting = Settings.Global.getInt(mResolver, ZEN_MODE);
            Assert.assertFalse(onSetting == changedSetting);
            mDevice.pressHome();
            Thread.sleep(TIMEOUT * 3);
        } finally {
            // change to DND default value
            int setting = Settings.Global.getInt(mResolver, ZEN_MODE);
            if (setting > 0) {
                launchQuickSettingsAndWait();
                mDevice.wait(Until.findObject(By.descContains(DND).clazz(Switch.class)),
                        TIMEOUT * 3).getChildren().get(0).click();
                Thread.sleep(TIMEOUT * 3);
            }
        }
    }

    public void toggleQuickSettingFlashLightAndVerify() throws Exception {
        String lightOn = "On";
        String lightOff = "Off";
        boolean verifyOn = false;
        launchQuickSettingsAndWait();
        UiObject2 flashLight = mDevice.wait(
                Until.findObject(By.desc(FLASHLIGHT)),
                TIMEOUT * 3);
        if (flashLight != null && flashLight.getText().equals(lightOn)) {
            verifyOn = true;
        }
        mDevice.wait(Until.findObject(By.desc(FLASHLIGHT)),
                TIMEOUT * 3).click();
        Thread.sleep(TIMEOUT * 3);
        flashLight = mDevice.wait(
                Until.findObject(By.desc(FLASHLIGHT)),
                TIMEOUT * 3);
        if (flashLight != null) {
            String txt = flashLight.getText();
            if (verifyOn) {
                Assert.assertTrue(txt.equals(lightOff));
            } else {
                Assert.assertTrue(txt.equals(lightOn));
                mDevice.wait(Until.findObject(By.textContains(FLASHLIGHT)),
                        TIMEOUT * 3).click();
            }
        }
        mDevice.pressHome();
        Thread.sleep(TIMEOUT * 3);
    }

    public void toggleQuickSettingOrientationAndVerify() throws Exception {
        launchQuickSettingsAndWait();
        mDevice.wait(Until.findObject(By.descContains(AUTO_ROTATE_SCREEN)),
                TIMEOUT * 3).click();
        Thread.sleep(TIMEOUT * 3);
        String rotation = Settings.System.getString(mResolver,
                Settings.System.ACCELEROMETER_ROTATION);
        Assert.assertEquals("1", rotation);
        mDevice.setOrientationNatural();
        mDevice.pressHome();
        Thread.sleep(TIMEOUT * 3);
    }

    public void launchQuickSettingsAndWait() throws Exception {
        mDevice.openQuickSettings();
        Thread.sleep(TIMEOUT * 2);
    }

    public void launchSettingsPageByComponentName(Context ctx, String name) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        ComponentName settingComponent = new ComponentName(SETTINGS_PACKAGE,
                String.format("%s.%s$%s", SETTINGS_PACKAGE, SETTINGS_APP, name));
        intent.setComponent(settingComponent);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }
}
