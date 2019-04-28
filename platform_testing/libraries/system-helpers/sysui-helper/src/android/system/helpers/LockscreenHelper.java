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
import android.app.KeyguardManager;
import android.content.Context;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.system.helpers.ActivityHelper;
import android.system.helpers.DeviceHelper;
import android.graphics.Point;
import android.graphics.Rect;

import junit.framework.Assert;

import java.io.IOException;
/**
 * Implement common helper methods for Lockscreen.
 */
public class LockscreenHelper {
    private static final String LOG_TAG = LockscreenHelper.class.getSimpleName();
    public static final int SHORT_TIMEOUT = 200;
    public static final int LONG_TIMEOUT = 2000;
    public static final String EDIT_TEXT_CLASS_NAME = "android.widget.EditText";
    public static final String CAMERA2_PACKAGE = "com.android.camera2";
    public static final String CAMERA_PACKAGE = "com.google.android.GoogleCamera";
    public static final String MODE_PIN = "PIN";
    public static final String MODE_PASSWORD = "Password";
    public static final String MODE_PATTERN = "Pattern";
    private static final int SWIPE_MARGIN = 5;
    private static final int SWIPE_MARGIN_BOTTOM = 100;
    private static final int DEFAULT_FLING_STEPS = 5;
    private static final int DEFAULT_SCROLL_STEPS = 15;
    private static final String PIN_ENTRY = "com.android.systemui:id/pinEntry";
    private static final String SET_PIN_COMMAND = "locksettings set-pin %s";
    private static final String SET_PASSWORD_COMMAND = "locksettings set-password %s";
    private static final String SET_PATTERN_COMMAND = "locksettings set-pattern %s";
    private static final String CLEAR_COMMAND = "locksettings clear --old %s";
    private static final String HOTSEAT = "hotseat";

    private static LockscreenHelper sInstance = null;
    private Context mContext = null;
    private UiDevice mDevice = null;
    private final ActivityHelper mActivityHelper;
    private final CommandsHelper mCommandsHelper;
    private final DeviceHelper mDeviceHelper;
    private boolean mIsRyuDevice = false;

    public LockscreenHelper() {
        mContext = InstrumentationRegistry.getTargetContext();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mActivityHelper = ActivityHelper.getInstance();
        mCommandsHelper = CommandsHelper.getInstance(InstrumentationRegistry.getInstrumentation());
        mDeviceHelper = DeviceHelper.getInstance();
        mIsRyuDevice = mDeviceHelper.isRyuDevice();
    }

    public static LockscreenHelper getInstance() {
        if (sInstance == null) {
            sInstance = new LockscreenHelper();
        }
        return sInstance;
    }

    public String getLauncherPackage() {
        return mDevice.getLauncherPackageName();
    }

    /**
     * Launch Camera on LockScreen
     * @return true/false
     */
    public boolean launchCameraOnLockScreen() {
        String CameraPackage = mIsRyuDevice ? CAMERA2_PACKAGE : CAMERA_PACKAGE;
        int w = mDevice.getDisplayWidth();
        int h = mDevice.getDisplayHeight();
        // Load camera on LockScreen and take a photo
        mDevice.drag((w - 25), (h - 25), (int) (w * 0.5), (int) (w * 0.5), 40);
        mDevice.waitForIdle();
        return mDevice.wait(Until.hasObject(
                By.res(CameraPackage, "activity_root_view")),
                LONG_TIMEOUT * 2);
    }

     /**
     * Sets the screen lock pin or password
     * @param pwd text of Password or Pin for lockscreen
     * @param mode indicate if its password or PIN
     * @throws InterruptedException
     */
    public void setScreenLock(String pwd, String mode, boolean mIsNexusDevice)
            throws InterruptedException {
        enterScreenLockOnce(pwd, mode, mIsNexusDevice);
        Thread.sleep(LONG_TIMEOUT);
        // Re-enter password on confirmation screen
        UiObject2 pinField = mDevice.wait(Until.findObject(By.clazz(EDIT_TEXT_CLASS_NAME)),
                LONG_TIMEOUT);
        pinField.setText(pwd);
        Thread.sleep(LONG_TIMEOUT);
        mDevice.wait(Until.findObject(By.text("OK")), LONG_TIMEOUT).click();
    }

    /**
     * Enters the screen lock once on the setting screen
     * @param pwd text of Password or Pin for lockscreen
     * @param mode indicate if its password or PIN
     * @throws InterruptedException
     */
    public void enterScreenLockOnce(String pwd, String mode, boolean mIsNexusDevice) {
        mDevice.wait(Until.findObject(By.text(mode)), LONG_TIMEOUT * 2).click();
        // set up Secure start-up page
        if (!mIsNexusDevice) {
            mDevice.wait(Until.findObject(By.text("No thanks")), LONG_TIMEOUT).click();
        }
        UiObject2 pinField = mDevice.wait(Until.findObject(By.clazz(EDIT_TEXT_CLASS_NAME)),
                LONG_TIMEOUT);
        pinField.setText(pwd);
        // enter and verify password
        mDevice.pressEnter();
    }

    /*
     * Enters non matching passcodes on both setting screens.
     * Note: this will fail if you enter matching passcodes.
     */
    public void enterNonMatchingPasscodes(String firstPasscode, String secondPasscode,
            String mode, boolean mIsNexusDevice) throws Exception {
        enterScreenLockOnce(firstPasscode, mode, mIsNexusDevice);
        Thread.sleep(LONG_TIMEOUT);
        UiObject2 pinField = mDevice.wait(Until.findObject(By.clazz(EDIT_TEXT_CLASS_NAME)),
                LONG_TIMEOUT);
        pinField.setText(secondPasscode);
        mDevice.pressEnter();
        Thread.sleep(LONG_TIMEOUT);
        // Verify that error is thrown.
        UiObject2 dontMatchMessage = mDevice.wait(Until.findObject
                (By.textContains("don’t match")), LONG_TIMEOUT);
        Assert.assertNotNull("Error message for passcode confirmation not visible",
                dontMatchMessage);
    }

    /**
     * check if Emergency Call page exists
     * @throws InterruptedException
     */
    public void checkEmergencyCallOnLockScreen() throws InterruptedException {
        mDevice.pressMenu();
        mDevice.wait(Until.findObject(By.text("EMERGENCY")), LONG_TIMEOUT).click();
        Thread.sleep(LONG_TIMEOUT);
        UiObject2 dialButton = mDevice.wait(Until.findObject(By.desc("dial")),
                LONG_TIMEOUT);
        Assert.assertNotNull("Can't reach emergency call page", dialButton);
        mDevice.pressBack();
        Thread.sleep(LONG_TIMEOUT);
    }

    /**
     * remove Screen Lock
     * @throws InterruptedException
     */
    public void removeScreenLock(String pwd)
            throws InterruptedException {
        navigateToScreenLock();
        UiObject2 pinField = mDevice.wait(Until.findObject(By.clazz(EDIT_TEXT_CLASS_NAME)),
                LONG_TIMEOUT);
        pinField.setText(pwd);
        mDevice.pressEnter();
        mDevice.wait(Until.findObject(By.text("Swipe")), LONG_TIMEOUT).click();
        mDevice.waitForIdle();
        mDevice.wait(Until.findObject(By.text("YES, REMOVE")), LONG_TIMEOUT).click();
    }

    /**
     * Enter a screen password or PIN.
     * Pattern not supported, please use
     * unlockDeviceWithPattern(String) below.
     * Method assumes the device is on lockscreen.
     * with keyguard exposed. It will wake
     * up the device, swipe up to reveal the keyguard,
     * and enter the password or pin and hit enter.
     * @throws InterruptedException, IOException
     */
    public void unlockScreen(String pwd)
            throws InterruptedException, IOException {
        // Press menu key (82 is the code for the menu key)
        String command = String.format(" %s %s %s", "input", "keyevent", "82");
        mDevice.executeShellCommand(command);
        Thread.sleep(SHORT_TIMEOUT);
        Thread.sleep(SHORT_TIMEOUT);
        // enter password to unlock screen
        command = String.format(" %s %s %s", "input", "text", pwd);
        mDevice.executeShellCommand(command);
        mDevice.waitForIdle();
        Thread.sleep(SHORT_TIMEOUT);
        mDevice.pressEnter();
    }

    /**
     * navigate to screen lock setting page
     * @throws InterruptedException
     */
    public void navigateToScreenLock()
            throws InterruptedException {
        mActivityHelper.launchIntent(Settings.ACTION_SECURITY_SETTINGS);
        mDevice.wait(Until.findObject(By.text("Screen lock")), LONG_TIMEOUT).click();
    }

    /**
     * check if Lock Screen is enabled
     */
    public boolean isLockScreenEnabled() {
        KeyguardManager km = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        return km.isKeyguardSecure();
    }

    /**
     * Sets a screen lock via shell.
     */
    public void setScreenLockViaShell(String passcode, String mode) throws Exception {
        switch (mode) {
            case MODE_PIN:
                mCommandsHelper.executeShellCommand(String.format(SET_PIN_COMMAND, passcode));
                break;
            case MODE_PASSWORD:
                mCommandsHelper.executeShellCommand(String.format(SET_PASSWORD_COMMAND, passcode));
                break;
            case MODE_PATTERN:
                mCommandsHelper.executeShellCommand(String.format(SET_PATTERN_COMMAND, passcode));
                break;
            default:
                throw new IllegalArgumentException("Unsupported mode: " + mode);
        }
    }

    /**
     * Removes the screen lock via shell.
     */
    public void removeScreenLockViaShell(String pwd) throws Exception {
        mCommandsHelper.executeShellCommand(String.format(CLEAR_COMMAND, pwd));
    }

    /**
     * swipe up to unlock the screen
     */
    public void unlockScreenSwipeUp() throws Exception {
        mDevice.wakeUp();
        mDevice.waitForIdle();
        mDevice.swipe(mDevice.getDisplayWidth() / 2,
                mDevice.getDisplayHeight() - SWIPE_MARGIN,
                mDevice.getDisplayWidth() / 2,
                SWIPE_MARGIN,
                DEFAULT_SCROLL_STEPS);
        mDevice.waitForIdle();
    }

    /*
     * Takes in the correct code (pin or password), the attempted
     * code (pin or password), the mode for the code (whether pin or password)
     * and whether or not they are expected to match.
     * Asserts that the device has been successfully unlocked (or not).
     */
    public void setAndEnterLockscreenCode(String actualCode, String attemptedCode,
            String mode, boolean shouldMatch) throws Exception {
        setScreenLockViaShell(actualCode, mode);
        Thread.sleep(LONG_TIMEOUT);
        enterLockscreenCode(actualCode, attemptedCode, mode, shouldMatch);
    }

    public void enterLockscreenCode(String actualCode, String attemptedCode,
            String mode, boolean shouldMatch) throws Exception {
        mDevice.pressHome();
        mDeviceHelper.sleepAndWakeUpDevice();
        unlockScreen(attemptedCode);
        checkForHotseatOnHome(shouldMatch);
        removeScreenLockViaShell(actualCode);
        Thread.sleep(LONG_TIMEOUT);
        mDevice.pressHome();
    }

    /*
     * Takes in the correct pattern, the attempted pattern,
     * and whether or not they are expected to match.
     * Asserts that the device has been successfully unlocked (or not).
     */
    public void setAndEnterLockscreenPattern(String actualPattern,
        String attemptedPattern, boolean shouldMatch) throws Exception {
        setScreenLockViaShell
                (actualPattern, LockscreenHelper.MODE_PATTERN);
        unlockDeviceWithPattern(attemptedPattern);
        checkForHotseatOnHome(shouldMatch);
        removeScreenLockViaShell(actualPattern);
        Thread.sleep(LONG_TIMEOUT);
        mDevice.pressHome();
    }

    public void checkForHotseatOnHome(boolean deviceUnlocked)  throws Exception {
        mDevice.pressHome();
        Thread.sleep(LONG_TIMEOUT);
        UiObject2 hotseat = mDevice.findObject(By.res(getLauncherPackage(), HOTSEAT));
        if (deviceUnlocked) {
        Assert.assertNotNull("Device not unlocked correctly", hotseat);
        }
        else {
            Assert.assertNull("Device should not be unlocked", hotseat);
        }
    }

    /*
     * The pattern below is always invalid as you need at least
     * four dots for a valid lock. That action of changing
     * directions while dragging is unsupported by
     * uiautomator.
     */
    public void enterInvalidPattern() throws Exception {
        // Get coordinates for left top dot
        UiObject2 lockPattern = mDevice.wait(Until.findObject
                (By.res("com.android.systemui:id/lockPatternView")),
                LONG_TIMEOUT);
        // Get coordinates for left side dots
        int xCoordinate =(int) (lockPattern.getVisibleBounds().left +
                 lockPattern.getVisibleBounds().left*0.16);
        int y1Coordinate = (int) (lockPattern.getVisibleBounds().top +
                lockPattern.getVisibleBounds().top*0.16);
        int y2Coordinate = (int) (lockPattern.getVisibleBounds().bottom -
                lockPattern.getVisibleBounds().bottom*0.16);
        // Drag coordinates from one point to another
        mDevice.swipe(xCoordinate, y1Coordinate, xCoordinate, y2Coordinate, 2);
    }

    /* Valid pattern unlock attempt
     * Takes in a contiguous string as input
     * 1 2 3
     * 4 5 6
     * 7 8 9
     * with each number representing a dot. Eg: "1236"
     */
    public void unlockDeviceWithPattern(String unlockPattern) throws Exception {
        mDeviceHelper.sleepAndWakeUpDevice();
        unlockScreenSwipeUp();
        Point[] coordinateArray = new Point[unlockPattern.length()];
        for (int i=0; i < unlockPattern.length(); i++) {
            coordinateArray[i] = calculateCoordinatesForPatternDot(unlockPattern.charAt(i),
                                 "com.android.systemui:id/lockPatternView");
        }
        // Note: 50 controls the speed of the pattern drawing.
        mDevice.swipe(coordinateArray, 50);
        Thread.sleep(SHORT_TIMEOUT);
    }

    /* Pattern lock setting attempt
     * Takes in a contiguous string as input
     * 1 2 3
     * 4 5 6
     * 7 8 9
     * with each number representing a dot. Eg: "1236"
     */
    public void enterPatternLockOnceForSettingLock(String unlockPattern)
            throws InterruptedException {
        Point[] coordinateArray = new Point[unlockPattern.length()];
        for (int i=0; i < unlockPattern.length(); i++) {
            coordinateArray[i] = calculateCoordinatesForPatternDot(unlockPattern.charAt(i),
                                 "com.android.settings:id/lockPattern");
        }
        // Note: 50 controls the speed of the pattern drawing.
        mDevice.swipe(coordinateArray, 50);
        Thread.sleep(SHORT_TIMEOUT);
    }

    /* Pattern lock setting - this enters and reconfirms pattern to set
     * using the UI.
     * Takes in a contiguous string as input
     * 1 2 3
     * 4 5 6
     * 7 8 9
     * with each number representing a dot. Eg: "1236"
     */
    public void setPatternLockSettingLock(String unlockPattern)  throws Exception {
        // Enter the same pattern twice, once on the initial set
        // screen and once on the confirmation screen.
        for (int i=0; i<2; i++) {
            enterPatternLockOnceForSettingLock(unlockPattern);
            mDevice.pressEnter();
        }
        mDevice.wait(Until.findObject(By.text("DONE")), LONG_TIMEOUT).click();
    }

    /* Returns screen coordinates for each pattern dot
     * for the current device
     * Represented as follows by chars
     * 1 2 3
     * 4 5 6
     * 7 8 9
     * this is consistent with the set-pattern command
     * to avoid confusion.
     */
    private Point calculateCoordinatesForPatternDot(char dotNumber, String lockPatternResId) {
        UiObject2 lockPattern = mDevice.wait(Until.findObject
                (By.res(lockPatternResId)), LONG_TIMEOUT);
        // Calculate x coordinate
        int xCoordinate = 0;
        int deltaX = (int) ((lockPattern.getVisibleBounds().right -
                lockPattern.getVisibleBounds().left)*0.16);
        if (dotNumber == '1' || dotNumber == '4' || dotNumber == '7') {
            xCoordinate = lockPattern.getVisibleBounds().left + deltaX;
        }
        else if (dotNumber == '2' || dotNumber == '5' || dotNumber == '8') {
            xCoordinate = lockPattern.getVisibleCenter().x;
        }
        else if (dotNumber == '3' || dotNumber == '6' || dotNumber == '9') {
            xCoordinate = lockPattern.getVisibleBounds().right - deltaX;
        }
        // Calculate y coordinate
        int yCoordinate = 0;
        int deltaY = (int) ((lockPattern.getVisibleBounds().bottom -
                lockPattern.getVisibleBounds().top)*0.16);
        if (dotNumber == '1' || dotNumber == '2' || dotNumber == '3') {
            yCoordinate = lockPattern.getVisibleBounds().top + deltaY;
        }
        else if (dotNumber == '4' || dotNumber == '5' || dotNumber == '6') {
            yCoordinate = lockPattern.getVisibleCenter().y;
        }
        else if (dotNumber == '7' || dotNumber == '8' || dotNumber == '9') {
            yCoordinate = lockPattern.getVisibleBounds().bottom - deltaY;
        }
        return new Point(xCoordinate, yCoordinate);
     }
}
