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
package android.system.helpers;

import android.app.Instrumentation;
import android.content.Context;
import android.provider.Settings;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;

/**
 * Implement common helper functions for accessibility.
 */
public class AccessibilityHelper {
    public static final String SETTINGS_PACKAGE = "com.android.settings";
    public static final String BUTTON = "android.widget.Button";
    public static final String CHECK_BOX = "android.widget.CheckBox";
    public static final String IMAGE_BUTTON = "android.widget.ImageButton";
    public static final String TEXT_VIEW = "android.widget.TextView";
    public static final String SWITCH = "android.widget.Switch";
    public static final String CHECKED_TEXT_VIEW = "android.widget.CheckedTextView";
    public static final String RADIO_BUTTON = "android.widget.RadioButton";
    public static final String SEEK_BAR = "android.widget.SeekBar";
    public static final String SPINNER = "android.widget.Spinner";
    public static final int SHORT_TIMEOUT = 2000;
    public static final int LONG_TIMEOUT = 5000;
    public static AccessibilityHelper sInstance = null;
    private Context mContext = null;
    private Instrumentation mInstrumentation = null;
    private UiDevice mDevice = null;
    private SettingsHelper mSettingsHelper = null;

    public enum SwitchStatus {
        ON,
        OFF;
    }

    private AccessibilityHelper(Instrumentation instr) {
        mInstrumentation = instr;
        mSettingsHelper = SettingsHelper.getInstance();
        mDevice = UiDevice.getInstance(instr);
        mContext = mInstrumentation.getTargetContext();
    }

    public static AccessibilityHelper getInstance(Instrumentation instr) {
        if (sInstance == null) {
            sInstance = new AccessibilityHelper(instr);
        }
        return sInstance;
    }

    /**
     * Set Talkback "ON"/"OFF".
     *
     * @param value "ON"/"OFF"
     * @throws Exception
     */
    public void setTalkBackSetting(SwitchStatus value) throws Exception {
        launchSpecificAccessibilitySetting("TalkBack");
        UiObject2 swtBar = mDevice.wait(
                Until.findObject(By.res(SETTINGS_PACKAGE, "switch_bar")), SHORT_TIMEOUT)
                .findObject(By.res(SETTINGS_PACKAGE, "switch_widget"));
        if (swtBar != null && !swtBar.getText().equals(value.toString())) {
            swtBar.click();
            UiObject2 confirmBtn = mDevice.wait(
                    Until.findObject(By.res("android:id/button1")), LONG_TIMEOUT);
            if (confirmBtn != null) {
                confirmBtn.click();
            }
            // First time enable talkback, tutorial open.
            if (mDevice.wait(Until.hasObject(By.text("TalkBack tutorial")), SHORT_TIMEOUT)) {
                mDevice.pressBack(); // back to talkback setting page
            }
        }
        mDevice.pressBack();
    }

    /**
     * Set high contrast "ON"/"OFF".
     *
     * @param value "ON"/"OFF"
     * @throws Exception
     */
    public void setHighContrast(SwitchStatus value) throws Exception {
        launchSpecificAccessibilitySetting("Accessibility");
        setSettingSwitchValue("High contrast text", value);
    }

    /**
     * Launch specific accessibility setting page.
     *
     * @param settingName Specific accessibility setting name
     * @throws Exception
     */
    public void launchSpecificAccessibilitySetting(String settingName) throws Exception {
        mSettingsHelper.launchSettingsPage(mContext, Settings.ACTION_ACCESSIBILITY_SETTINGS);
        int maxTry = 3;
        while (maxTry-- >= 0) {
            Thread.sleep(SHORT_TIMEOUT);
            UiObject2 actionBar = mDevice.wait(Until.findObject(
                    By.res(SETTINGS_PACKAGE, "action_bar").enabled(true)), SHORT_TIMEOUT);
            if (actionBar == null) {
                mSettingsHelper.launchSettingsPage(mContext,
                        Settings.ACTION_ACCESSIBILITY_SETTINGS);
            } else {
                String actionBarText = actionBar.findObject(By.clazz(TEXT_VIEW)).getText();
                if (actionBarText.equals(settingName)) {
                    break;
                } else if (actionBarText.equals("Accessibility")) {
                    getSettingFromList(settingName).click();
                } else {
                    mDevice.wait(Until.findObject(By.res(SETTINGS_PACKAGE, "action_bar")
                            .enabled(true)), SHORT_TIMEOUT)
                            .findObject(By.clazz(IMAGE_BUTTON)).click();
                }
            }
        }
    }

    /**
     * Set switch "ON"/"OFF".
     *
     * @param settingTag setting name
     * @param value "ON"/"OFF"
     * @return true/false
     * @throws UiObjectNotFoundException
     * @throws InterruptedException
     */
    private boolean setSettingSwitchValue(String settingTag, SwitchStatus value)
            throws UiObjectNotFoundException, InterruptedException {
        UiObject2 cellSwitch = getSettingFromList(settingTag)
                .getParent().getParent().findObject(By.clazz(SWITCH));
        if (cellSwitch != null) {
            if (!cellSwitch.getText().equals(value.toString())) {
                cellSwitch.click();
                UiObject2 okBtn = mDevice.wait(Until.findObject(
                        By.res("android:id/button1")), LONG_TIMEOUT);
                if (okBtn != null) {
                    okBtn.click();
                }
            }
            return cellSwitch.getText().equals(value.toString());
        }
        return false;
    }

    /**
     * Get setting name text object from list.
     *
     * @param settingName setting name
     * @return UiObject2
     * @throws UiObjectNotFoundException
     */
    private UiObject2 getSettingFromList(String settingName)
            throws UiObjectNotFoundException {
        UiScrollable listScrollable = new UiScrollable(
                new UiSelector().resourceId(SETTINGS_PACKAGE+":id/list"));
        if (listScrollable != null) {
            listScrollable.scrollToBeginning(100);
            listScrollable.scrollIntoView(
                    new UiSelector().resourceId("android:id/title").text(settingName));
            return mDevice.findObject(By.res("android:id/title").text(settingName));
        } else {
            throw new UiObjectNotFoundException("Fail to get scrollable list %s.");
        }
    }
}
