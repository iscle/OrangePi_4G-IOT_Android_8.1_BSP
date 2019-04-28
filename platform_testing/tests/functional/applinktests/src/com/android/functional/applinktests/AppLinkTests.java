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

package com.android.functional.applinktests;

import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.HermeticTest;
import android.platform.test.annotations.Presubmit;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.util.Log;
import android.view.accessibility.AccessibilityWindowInfo;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AppLinkTests extends InstrumentationTestCase {
    public final String TEST_TAG = "AppLinkFunctionalTest";
    public final String TEST_PKG_NAME = "com.android.applinktestapp";
    public final String TEST_APP_NAME = "AppLinkTestApp";
    public final String YOUTUBE_PKG_NAME = "com.google.android.youtube";
    public final String HTTP_SCHEME = "http";
    public final String TEST_HOST = "test.com";
    public final int TIMEOUT = 1000;
    private UiDevice mDevice = null;
    private Context mContext = null;
    private UiAutomation mUiAutomation = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mContext = getInstrumentation().getContext();
        mUiAutomation = getInstrumentation().getUiAutomation();
        mDevice.setOrientationNatural();
    }

    // Ensures that default app link setting set to 'undefined' for 3P apps
    @Presubmit
    @HermeticTest
    public void testDefaultAppLinkSettting() throws InterruptedException {
        String out = getAppLink(TEST_PKG_NAME);
        assertTrue("Default app link not set to 'undefined' mode", "undefined".equals(out));
        openLink(HTTP_SCHEME, TEST_HOST);
        ensureDisambigPresent();
    }

    // User sets an app to open for a link 'Just Once' and disambig shows up next time too
    // Once user set to 'always' disambig never shows up
    public void testUserSetToJustOnceAndAlways() throws InterruptedException {
        openLink(HTTP_SCHEME, TEST_HOST);
        ensureDisambigPresent();
        mDevice.wait(Until.findObject(By.text("AppLinkTestApp")), TIMEOUT).click();
        mDevice.wait(Until.findObject(By.res("android:id/button_once")), TIMEOUT).click();
        Thread.sleep(TIMEOUT);
        verifyForegroundAppPackage(TEST_PKG_NAME);
        openLink(HTTP_SCHEME, TEST_HOST);
        assertTrue("Target app isn't the default choice",
                mDevice.wait(Until.hasObject(By.text("Open with AppLinkTestApp")), TIMEOUT));
        mDevice.wait(Until.findObject(By.res("android:id/button_once")), TIMEOUT)
                .clickAndWait(Until.newWindow(), TIMEOUT);
        Thread.sleep(TIMEOUT);
        verifyForegroundAppPackage(TEST_PKG_NAME);
        mDevice.pressHome();
        // Ensure it doesn't change on second attempt
        openLink(HTTP_SCHEME, TEST_HOST);
        // Ensure disambig is present
        mDevice.wait(Until.findObject(By.res("android:id/button_always")), TIMEOUT).click();
        mDevice.pressHome();
        // User chose to set to always and intent is opened in target direct
        openLink(HTTP_SCHEME, TEST_HOST);
        verifyForegroundAppPackage(TEST_PKG_NAME);
    }

    // Ensure verified app always open even candidate but unverified app set to 'always'
    @Presubmit
    @HermeticTest
    public void testVerifiedAppOpenWhenNotVerifiedSetToAlways() throws InterruptedException {
        setAppLink(TEST_PKG_NAME, "always");
        setAppLink(YOUTUBE_PKG_NAME, "always");
        Thread.sleep(TIMEOUT);
        openLink(HTTP_SCHEME, "youtube.com");
        verifyForegroundAppPackage(YOUTUBE_PKG_NAME);
    }

    // Ensure verified app always open even one candidate but unverified app set to 'ask'
    @Presubmit
    @HermeticTest
    public void testVerifiedAppOpenWhenUnverifiedSetToAsk() throws InterruptedException {
        setAppLink(TEST_PKG_NAME, "ask");
        setAppLink(YOUTUBE_PKG_NAME, "always");
        String out = getAppLink(YOUTUBE_PKG_NAME);
        openLink(HTTP_SCHEME, "youtube.com");
        verifyForegroundAppPackage(YOUTUBE_PKG_NAME);
    }

    // Ensure disambig is shown if verified app set to 'never' and unverified app set to 'ask'
    public void testUserChangeVerifiedLinkHandler() throws InterruptedException {
        setAppLink(TEST_PKG_NAME, "ask");
        setAppLink(YOUTUBE_PKG_NAME, "never");
        Thread.sleep(TIMEOUT);
        openLink(HTTP_SCHEME, "youtube.com");
        ensureDisambigPresent();
        setAppLink(YOUTUBE_PKG_NAME, "always");
        Thread.sleep(TIMEOUT);
        openLink(HTTP_SCHEME, "youtube.com");
        verifyForegroundAppPackage(YOUTUBE_PKG_NAME);
    }

    // Ensure unverified app always open when unverified app set to always but verified app set to
    // never
    @Presubmit
    @HermeticTest
    public void testTestAppSetToAlwaysVerifiedSetToNever() throws InterruptedException {
        setAppLink(TEST_PKG_NAME, "always");
        setAppLink(YOUTUBE_PKG_NAME, "never");
        Thread.sleep(TIMEOUT);
        openLink(HTTP_SCHEME, "youtube.com");
        verifyForegroundAppPackage(TEST_PKG_NAME);
    }

    // Test user can modify 'App Link Settings'
    public void testSettingsChangeUI() throws InterruptedException {
        Intent intent_as = new Intent(
                android.provider.Settings.ACTION_APPLICATION_SETTINGS);
        mContext.startActivity(intent_as);
        Thread.sleep(TIMEOUT * 5);
        mDevice.wait(Until.findObject(By.res("com.android.settings:id/advanced")), TIMEOUT)
                .clickAndWait(Until.newWindow(), TIMEOUT);
        mDevice.wait(Until.findObject(By.text("Opening links")), TIMEOUT)
                .clickAndWait(Until.newWindow(), TIMEOUT);
        mDevice.wait(Until.findObject(By.text("AppLinkTestApp")), TIMEOUT)
                .clickAndWait(Until.newWindow(), TIMEOUT);
        mDevice.wait(Until.findObject(By.text("Open supported links")), TIMEOUT)
                .clickAndWait(Until.newWindow(), TIMEOUT);
        mDevice.wait(Until.findObject(By.text("Open in this app")), TIMEOUT)
                .clickAndWait(Until.newWindow(), TIMEOUT);
        String out = getAppLink(TEST_PKG_NAME);
        Thread.sleep(TIMEOUT);
        assertTrue(String.format("Default app link not set to 'always ask' rather set to %s", out),
                "always".equals(out));
        mDevice.wait(Until.findObject(By.text("Open supported links")), TIMEOUT)
                .clickAndWait(Until.newWindow(), TIMEOUT);
        mDevice.wait(Until.findObject(By.text("Don’t open in this app")), TIMEOUT)
                .clickAndWait(Until.newWindow(), TIMEOUT);
        out = getAppLink(TEST_PKG_NAME);
        Thread.sleep(TIMEOUT);
        assertTrue(String.format("Default app link not set to 'never' rather set to %s", out),
                "never".equals(out));
        mDevice.wait(Until.findObject(By.text("Open supported links")), TIMEOUT)
                .clickAndWait(Until.newWindow(), TIMEOUT);
        mDevice.wait(Until.findObject(By.text("Ask every time")), TIMEOUT)
                .clickAndWait(Until.newWindow(), TIMEOUT);
        out = getAppLink(TEST_PKG_NAME);
        Thread.sleep(TIMEOUT);
        assertTrue(String.format("Default app link not set to 'always ask' rather set to %s", out),
                "always ask".equals(out));
    }

    // Ensure system apps that claim to open always for set to always
    @Presubmit
    @HermeticTest
    public void testSysappAppLinkSettings() {
        // List of system app that are set to 'Always' for certain urls
        List<String> alwaysOpenApps = new ArrayList<String>();
        alwaysOpenApps.add("com.android.vending"); // Playstore
        alwaysOpenApps.add("com.google.android.apps.docs"); // Drive
        alwaysOpenApps.add("com.google.android.apps.maps"); // Map
        alwaysOpenApps.add("com.google.android.calendar"); // Calendar
        alwaysOpenApps.add("com.google.android.music"); // PlayMusic
        alwaysOpenApps.add("com.google.android.youtube"); // YouTube
        for (String alwaysOpenApp : alwaysOpenApps) {
            String out = getAppLink(alwaysOpenApp);
            assertTrue(String.format("App link for %s should be set to 'Always'", alwaysOpenApp),
                    "always".equalsIgnoreCase(out));
        }
    }

    @Override
    protected void tearDown() throws Exception {
        executeShellCommand("pm clear " + TEST_PKG_NAME);
        executeShellCommand("pm clear " + YOUTUBE_PKG_NAME);
        executeShellCommand("pm set-app-link " + TEST_PKG_NAME + " undefined");
        executeShellCommand("pm set-app-link " + YOUTUBE_PKG_NAME + " always");
        Thread.sleep(TIMEOUT);
        mDevice.unfreezeRotation();
        mDevice.pressHome();
        super.tearDown();
    }

    // Start an intent to open a test link
    private void openLink(String scheme, String host) throws InterruptedException {
        String out = executeShellCommand(String.format(
                "am start -a android.intent.action.VIEW -d %s://%s/", scheme, host));
        Thread.sleep(TIMEOUT * 2);
    }

    // If framework identifies more than one app that can handle a link intent, framework presents a
    // window to user to choose the app to handle the intent.
    // This is also known as 'disambig' window
    private void ensureDisambigPresent() {
        assertNotNull("Disambig dialog is not shown",
                mDevice.wait(Until.hasObject(By.res("android:id/resolver_list")),
                        TIMEOUT));
        List<UiObject2> resolverApps = mDevice.wait(Until.findObjects(By.res("android:id/text1")),
                TIMEOUT);
        assertTrue("There aren't exactly 2 apps to resolve", resolverApps.size() == 2);
        assertTrue("Resolver apps aren't correct",
                "AppLinkTestApp".equals(resolverApps.get(0).getText()) &&
                        "Chrome".equals(resolverApps.get(1).getText()));
    }

    // Verifies that a certain package is in foreground
    private void verifyForegroundAppPackage(String pkgName) throws InterruptedException {
        int counter = 3;
        List<AccessibilityWindowInfo> windows = null;
        while (--counter > 0 && windows == null) {
            windows = mUiAutomation.getWindows();
            Thread.sleep(TIMEOUT);
        }
        assertTrue(String.format("%s is not top activity", "youtube"),
                windows.get(windows.size() - 1).getRoot().getPackageName().equals(pkgName));
    }

    // Gets app link for a package
    private String getAppLink(String pkgName) {
        return executeShellCommand(String.format("pm get-app-link %s", pkgName));
    }

    // Sets Openlink settings for a package to passed value
    private void setAppLink(String pkgName, String valueToBeSet) {
        executeShellCommand(String.format("pm set-app-link %s %s", pkgName, valueToBeSet));
    }

    // Executes 'adb shell' command. Converts ParcelFileDescriptor output to String
    private String executeShellCommand(String command) {
        if (command == null || command.isEmpty()) {
            return null;
        }
        ParcelFileDescriptor pfd = mUiAutomation.executeShellCommand(command);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(pfd.getFileDescriptor())))) {
            String str = reader.readLine();
            Log.d(TEST_TAG, String.format("Executing command: %s", command));
            return str;
        } catch (IOException e) {
            Log.e(TEST_TAG, e.getMessage());
        }

        return null;
    }
}
