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

package android.platform.systemui.tests.jank;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.support.test.timeresulthelper.TimeResultLogger;
import android.os.Bundle;

import java.io.File;
import java.io.IOException;

public class SettingsJankTests extends JankTestBase {

    private static final int TIMEOUT = 5000;
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final BySelector SETTINGS_DASHBOARD = By.res(SETTINGS_PACKAGE,
            "dashboard_container");
    // short transitions should be repeated within the test function, otherwise frame stats
    // captured are not really meaningful in a statistical sense
    private static final int INNER_LOOP = 2;
    private static final File TIMESTAMP_FILE = new File(Environment.getExternalStorageDirectory()
            .getAbsolutePath(), "autotester.log");
    private static final File RESULTS_FILE = new File(Environment.getExternalStorageDirectory()
            .getAbsolutePath(), "results.log");

    private UiDevice mDevice;

    public void setUp() {
        mDevice = UiDevice.getInstance(getInstrumentation());

        try {
            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException("failed to freeze device orientaion", e);
        }
        // Start from the home screen
        mDevice.pressHome();
        mDevice.waitForIdle();

        // Launch the settings app
        Context context = getInstrumentation().getContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(SETTINGS_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK); // Clear out any previous instances
        context.startActivity(intent);
        mDevice.wait(Until.hasObject(By.pkg(SETTINGS_PACKAGE).depth(0)), TIMEOUT);
        SystemClock.sleep(1000);
    }

    @Override
    protected void tearDown() throws Exception {
        mDevice.unfreezeRotation();
        super.tearDown();
    }

    public void flingSettingsToStart() throws IOException {
        UiObject2 list = mDevice.wait(Until.findObject(SETTINGS_DASHBOARD), TIMEOUT);
        int count = 0;
        while (!list.isScrollable() && count <= 5) {
            mDevice.wait(Until.findObject(By.text("SEE ALL")), TIMEOUT).click();
            list = mDevice.wait(Until.findObject(SETTINGS_DASHBOARD), TIMEOUT);
            count++;
        }
        while (list.fling(Direction.UP));
        mDevice.waitForIdle();
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterTestSettingsFling(Bundle metrics) throws IOException {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /** Starts from the top of the settings activity and measures jank while flinging down */
    @JankTest(expectedFrames=100, beforeTest="flingSettingsToStart",
            afterTest="afterTestSettingsFling")
    @GfxMonitor(processName=SETTINGS_PACKAGE)
    public void testSettingsFling() {
        UiObject2 list = mDevice.findObject(SETTINGS_DASHBOARD);
        for (int i = 0; i < INNER_LOOP; i++) {
            list.fling(Direction.DOWN);
            mDevice.waitForIdle();
            list.fling(Direction.UP);
            mDevice.waitForIdle();
        }
    }
}
