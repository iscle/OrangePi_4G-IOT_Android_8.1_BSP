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
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.util.Log;

import java.util.List;

/**
 * Implement common helper functions for Accessibility scanner.
 */
public class AccessibilityScannerHelper {
    public static final String ACCESSIBILITY_SCANNER_PACKAGE
            = "com.google.android.apps.accessibility.auditor";
    public static final String MAIN_ACTIVITY_CLASS = "%s.ui.MainActivity";
    public static final String CHECK_BUTTON_RES_ID = "accessibilibutton";
    private static final int SCANNER_WAIT_TIME = 5000;
    private static final int SHORT_TIMEOUT = 2000;
    private static final String LOG_TAG = AccessibilityScannerHelper.class.getSimpleName();
    private static final String RESULT_TAG = "A11Y_SCANNER_RESULT";
    public static AccessibilityScannerHelper sInstance = null;
    private UiDevice mDevice = null;
    private ActivityHelper mActivityHelper = null;
    private PackageHelper mPackageHelper = null;
    private AccessibilityHelper mAccessibilityHelper = null;

    private AccessibilityScannerHelper(Instrumentation instr) {
        mDevice = UiDevice.getInstance(instr);
        mActivityHelper = ActivityHelper.getInstance();
        mPackageHelper = PackageHelper.getInstance(instr);
        mAccessibilityHelper = AccessibilityHelper.getInstance(instr);
    }

    public static AccessibilityScannerHelper getInstance(Instrumentation instr) {
        if (sInstance == null) {
            sInstance = new AccessibilityScannerHelper(instr);
        }
        return sInstance;
    }

    /**
     * If accessibility scanner installed.
     *
     * @return true/false
     */
    public boolean scannerInstalled() {
        return mPackageHelper.isPackageInstalled(ACCESSIBILITY_SCANNER_PACKAGE);
    }

    /**
     * Click scanner check button and parse and log results.
     *
     * @param resultPrefix
     * @throws Exception
     */
    public void runScanner(String resultPrefix) throws Exception {
        int tries = 3; // retries
        while (tries-- > 0) {
            try {
                clickScannerCheck();
                logScannerResult(resultPrefix);
                break;
            } catch (UiObjectNotFoundException e) {
                continue;
            } catch (Exception e) {
                throw e;
            }
        }
    }

    /**
     * Click scanner check button and open share app in the share menu.
     *
     * @param resultPrefix
     * @param shareAppTag
     * @throws Exception
     */
    public void runScannerAndOpenShareApp(String resultPrefix, String shareAppTag)
            throws Exception {
        runScanner(resultPrefix);
        UiObject2 shareApp = getShareApp(shareAppTag);
        if (shareApp != null) {
            shareApp.click();
        }
    }

    /**
     * Set Accessibility Scanner setting ON/OFF.
     *
     * @throws Exception
     */
    public void setAccessibilityScannerSetting(AccessibilityHelper.SwitchStatus value)
            throws Exception {
        if (!scannerInstalled()) {
            throw new Exception("Accessibility Scanner not installed.");
        }
        mAccessibilityHelper.launchSpecificAccessibilitySetting("Accessibility Scanner");
        for (int tries = 0; tries < 2; tries++) {
            UiObject2 swt = mDevice.wait(Until.findObject(
                    By.res(AccessibilityHelper.SETTINGS_PACKAGE, "switch_widget")),
                    SHORT_TIMEOUT * 2);
            if (swt.getText().equals(value.toString())) {
                break;
            } else if (tries == 1) {
                throw new Exception(String.format("Fail to set scanner to: %s.", value.toString()));
            } else {
                swt.click();
                UiObject2 okBtn = mDevice.wait(Until.findObject(By.text("OK")), SHORT_TIMEOUT);
                if (okBtn != null) {
                    okBtn.click();
                }
                if (initialSetups()) {
                    mDevice.pressBack();
                }
                grantPermissions();
            }
        }
    }

    /**
     * Click through all permission pop ups for scanner. Grant all necessary permissions.
     */
    private void grantPermissions() {
        UiObject2 auth1 = mDevice.wait(Until.findObject(
                By.text("BEGIN AUTHORIZATION")), SHORT_TIMEOUT);
        if (auth1 != null) {
            auth1.click();
        }
        UiObject2 chk = mDevice.wait(Until.findObject(
                By.clazz(AccessibilityHelper.CHECK_BOX)), SHORT_TIMEOUT);
        if (chk != null) {
            chk.click();
            mDevice.findObject(By.text("START NOW")).click();
        }
        UiObject2 auth2 = mDevice.wait(Until.findObject(
                By.text("BEGIN AUTHORIZATION")), SHORT_TIMEOUT);
        if (auth2 != null) {
            auth2.click();
        }
        UiObject2 tapOk = mDevice.wait(Until.findObject(
                By.pkg(ACCESSIBILITY_SCANNER_PACKAGE).text("OK")), SHORT_TIMEOUT);
        if (tapOk != null) {
            tapOk.click();
        }
    }

    /**
     * Launch accessibility scanner.
     *
     * @throws UiObjectNotFoundException
     */
    public void launchScannerApp() throws Exception {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        ComponentName settingComponent = new ComponentName(ACCESSIBILITY_SCANNER_PACKAGE,
                String.format(MAIN_ACTIVITY_CLASS, ACCESSIBILITY_SCANNER_PACKAGE));
        intent.setComponent(settingComponent);
        mActivityHelper.launchIntent(intent);
        initialSetups();
    }

    /**
     * Steps for first time launching scanner app.
     *
     * @return true/false return false immediately, if initial setup screen doesn't show up.
     * @throws Exception
     */
    private boolean initialSetups() throws Exception {
        UiObject2 getStartBtn = mDevice.wait(
                Until.findObject(By.text("GET STARTED")), SHORT_TIMEOUT);
        if (getStartBtn != null) {
            getStartBtn.click();
            UiObject2 msg = mDevice.wait(Until.findObject(
                    By.text("Turn on Accessibility Scanner")), SHORT_TIMEOUT);
            if (msg != null) {
                mDevice.findObject(By.text("OK")).click();
                setAccessibilityScannerSetting(AccessibilityHelper.SwitchStatus.ON);
            }
            mDevice.wait(Until.findObject(By.text("OK, GOT IT")), SCANNER_WAIT_TIME).click();
            mDevice.wait(Until.findObject(By.text("DISMISS")), SHORT_TIMEOUT).click();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Clear history of accessibility scanner.
     *
     * @throws InterruptedException
     */
    public void clearHistory() throws Exception {
        launchScannerApp();
        int maxTry = 20;
        while (maxTry > 0) {
            List<UiObject2> historyItemList = mDevice.findObjects(
                    By.res(ACCESSIBILITY_SCANNER_PACKAGE, "history_item_row"));
            if (historyItemList.size() == 0) {
                break;
            }
            historyItemList.get(0).click();
            Thread.sleep(SHORT_TIMEOUT);
            deleteHistory();
            Thread.sleep(SHORT_TIMEOUT);
            maxTry--;
        }
    }

    /**
     * Log results of accessibility scanner.
     *
     * @param pageName
     * @throws Exception
     */
    public void logScannerResult(String pageName) throws Exception {
        int res = getNumberOfSuggestions();
        if (res > 0) {
            Log.i(RESULT_TAG, String.format("%s: %s suggestions!", pageName, res));
        } else if (res == 0) {
            Log.i(RESULT_TAG, String.format("%s: Pass.", pageName));
        } else {
            throw new UiObjectNotFoundException("Fail to get number of suggestions.");
        }
    }

    /**
     * Move scanner button to avoid blocking the object.
     *
     * @param avoidObj object to move the check button away from
     */
    public void adjustScannerButton(UiObject2 avoidObj)
            throws UiObjectNotFoundException, InterruptedException {
        Rect origBounds = getScannerCheckBtn().getVisibleBounds();
        Rect avoidBounds = avoidObj.getVisibleBounds();
        if (origBounds.intersect(avoidBounds)) {
            Point dest = calculateDest(origBounds, avoidBounds);
            moveScannerCheckButton(dest.x, dest.y);
        }
    }

    /**
     * Move scanner check button back to the middle of the screen.
     */
    public void resetScannerCheckButton() throws UiObjectNotFoundException, InterruptedException {
        int midY = (int) Math.ceil(mDevice.getDisplayHeight() * 0.5);
        int midX = (int) Math.ceil(mDevice.getDisplayWidth() * 0.5);
        moveScannerCheckButton(midX, midY);
    }

    /**
     * Move scanner check button to a target location.
     *
     * @param locX target location x-axis
     * @param locY target location y-axis
     * @throws UiObjectNotFoundException
     */
    public void moveScannerCheckButton(int locX, int locY)
            throws UiObjectNotFoundException, InterruptedException {
        int tries = 2;
        while (tries-- > 0) {
            UiObject2 btn = getScannerCheckBtn();
            Rect bounds = btn.getVisibleBounds();
            int origX = bounds.centerX();
            int origY = bounds.centerY();
            int buttonWidth = bounds.width();
            int buttonHeight = bounds.height();
            if (Math.abs(locX - origX) > buttonWidth || Math.abs(locY - origY) > buttonHeight) {
                btn.drag(new Point(locX, locY));
            }
            Thread.sleep(SCANNER_WAIT_TIME);
            // drag cause a click on the scanner button, bring the UI into scanner app
            if (getScannerCheckBtn() == null
                    && mDevice.findObject(By.pkg(ACCESSIBILITY_SCANNER_PACKAGE)) != null) {
                mDevice.pressBack();
            } else {
                break;
            }
        }
    }

    /**
     * Calculate the moving destination of check button.
     *
     * @param origRect original bounds of the check button
     * @param avoidRect bounds to move away from
     * @return destination of check button center point.
     */
    private Point calculateDest(Rect origRect, Rect avoidRect) {
        int bufferY = (int)Math.ceil(mDevice.getDisplayHeight() * 0.1);
        int destY = avoidRect.bottom + bufferY + origRect.height()/2;
        if (destY >= mDevice.getDisplayHeight()) {
            destY = avoidRect.top - bufferY - origRect.height()/2;
        }
        return new Point(origRect.centerX(), destY);
    }

    /**
     * Return scanner check button.
     *
     * @return UiObject2
     */
    private UiObject2 getScannerCheckBtn() {
        return mDevice.findObject(By.res(ACCESSIBILITY_SCANNER_PACKAGE, CHECK_BUTTON_RES_ID));
    }

    private void clickScannerCheck() throws UiObjectNotFoundException, InterruptedException {
        UiObject2 accessibilityScannerButton = getScannerCheckBtn();
        if (accessibilityScannerButton != null) {
            accessibilityScannerButton.click();
        } else {
            // TODO: check if app crash error, restart scanner service
            Log.i(LOG_TAG, "Fail to find accessibility scanner check button.");
            throw new UiObjectNotFoundException(
                    "Fail to find accessibility scanner check button.");
        }
        Thread.sleep(SCANNER_WAIT_TIME);
    }

    /**
     * Check if no suggestion.
     * @deprecated Use {@link #getNumberOfSuggestions} instead
     */
    @Deprecated
    private Boolean testPass() throws UiObjectNotFoundException {
        UiObject2 txtView = getToolBarTextView();
        return txtView.getText().equals("No suggestions");
    }

    /**
     * Return accessibility scanner tool bar text view.
     *
     * @return UiObject2
     * @throws UiObjectNotFoundException
     */
    private UiObject2 getToolBarTextView() throws UiObjectNotFoundException {
        UiObject2 toolBar = mDevice.wait(Until.findObject(
                By.res(ACCESSIBILITY_SCANNER_PACKAGE, "toolbar")), SHORT_TIMEOUT);
        if (toolBar != null) {
            return toolBar.findObject(By.clazz(AccessibilityHelper.TEXT_VIEW));
        } else {
            throw new UiObjectNotFoundException(
                    "Failed to find Scanner tool bar. Scanner app might not be active.");
        }
    }

    /**
     * Delete active scanner history.
     */
    private void deleteHistory() {
        UiObject2 moreBtn = mDevice.wait(Until.findObject(By.desc("More options")), SHORT_TIMEOUT);
        if (moreBtn != null) {
            moreBtn.click();
            mDevice.wait(Until.findObject(
                    By.clazz(AccessibilityHelper.TEXT_VIEW).text("Delete")), SHORT_TIMEOUT).click();
        }
    }

    /**
     * Return number suggestions.
     *
     * @return number of suggestions
     * @throws UiObjectNotFoundException
     */
    private int getNumberOfSuggestions() throws UiObjectNotFoundException {
        int tries = 2; // retries
        while (tries-- > 0) {
            UiObject2 txtView = getToolBarTextView();
            if (txtView != null) {
                String result = txtView.getText();
                if (result.equals("No suggestions")) {
                    return 0;
                } else {
                    String str = result.split("\\s+")[0];
                    return Integer.parseInt(str);
                }
            }
        }
        Log.i(LOG_TAG, String.format("Error in getting number of suggestions."));
        return -1;
    }

    /**
     * Return share app UiObject2
     *
     * @param appName
     * @return
     */
    private UiObject2 getShareApp(String appName) throws UiObjectNotFoundException {
        UiObject2 shareBtn = mDevice.wait(Until.findObject(By.res(ACCESSIBILITY_SCANNER_PACKAGE,
                "action_share_results")), SHORT_TIMEOUT);
        if (shareBtn != null) {
            shareBtn.click();
            mDevice.wait(Until.hasObject(By.res("android:id/resolver_list")), SHORT_TIMEOUT * 3);
            UiScrollable scrollable = new UiScrollable(
                    new UiSelector().className("android.widget.ScrollView"));
            int tries = 3;
            while (!mDevice.hasObject(By.text(appName)) && tries-- > 0) {
                scrollable.scrollForward();
            }
            return mDevice.findObject(By.text(appName));
        }
        return null;
    }

    /**
     * Return if scanner enabled by check if home screen has check button.
     *
     * @return true/false
     */
    public boolean ifScannerEnabled() throws InterruptedException {
        mDevice.pressHome();
        Thread.sleep(SHORT_TIMEOUT);
        mDevice.waitForIdle();
        return getScannerCheckBtn() != null;
    }
}
