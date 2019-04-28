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
package com.android.wearable.sysapp.janktests;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import java.util.concurrent.TimeoutException;

/**
 * Jank tests to fling through apps available in the launcher [1st page]
 */
public class AppLauncherFlingJankTest extends JankTestBase {

    private UiDevice mDevice;
    private SysAppTestHelper mHelper;
    private PowerManager mPm;
    private WakeLock mWakeLock;

    /*
     * (non-Javadoc)
     * @see junit.framework.TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mHelper = SysAppTestHelper.getInstance(mDevice, this.getInstrumentation());
        mPm = (PowerManager) getInstrumentation().
                getContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPm.newWakeLock(PowerManager.FULL_WAKE_LOCK,
                AppLauncherFlingJankTest.class.getSimpleName());
        mWakeLock.acquire();
    }

    /**
     * This method ensures the device is taken to Home and launch the apps page (also known as 1st
     * page) before running the fling test on apps.
     * @throws RemoteException
     * @throws TimeoutException
     */
    public void openLauncher() throws RemoteException, TimeoutException {
        mHelper.gotoAppLauncher();
     }

    /**
     * Test the jank by flinging in apps screen.
     * @throws TimeoutException
     *
     */
    @JankTest(beforeTest = "openLauncher", afterTest = "goBackHome",
        expectedFrames = SysAppTestHelper.EXPECTED_FRAMES)
    @GfxMonitor(processName = "com.google.android.wearable.app")
    public void testFlingApps() throws TimeoutException {
        UiObject2 recyclerViewContents = mDevice.wait(Until.findObject(
        By.res("com.google.android.wearable.app","launcher_view")), SysAppTestHelper.SHORT_TIMEOUT);
        for (int i = 0; i < 3; i++) {
          recyclerViewContents.fling(Direction.DOWN, SysAppTestHelper.FLING_SPEED);
          recyclerViewContents.fling(Direction.UP, SysAppTestHelper.FLING_SPEED);
       }
    }

    // Ensuring that we head back to the first screen before launching the app again
    public void goBackHome(Bundle metrics) throws RemoteException {
        mHelper.goBackHome();
        super.afterTest(metrics);
    }

    /*
     * (non-Javadoc)
     * @see android.test.InstrumentationTestCase#tearDown()
     */
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mWakeLock.release();
    }
}
