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

package com.android.uibench.janktests;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.DisplayMetrics;

import junit.framework.Assert;

/**
 * Jank benchmark tests helper for UiBench app
 */
public class UiBenchJankTestsHelper {
    public static final int LONG_TIMEOUT = 5000;
    public static final int FULL_TEST_DURATION = 25000;
    public static final int TIMEOUT = 250;
    public static final int SHORT_TIMEOUT = 2000;
    public static final int EXPECTED_FRAMES = 100;
    public static final int KEY_DELAY = 1000;

    /**
     * Only to be used for initial-fling tests, or similar cases
     * where perf during brief experience is important.
     */
    public static final int SHORT_EXPECTED_FRAMES = 30;

    public static final String PACKAGE_NAME = "com.android.test.uibench";

    private static final int SLOW_FLING_SPEED = 3000; // compare to UiObject2#DEFAULT_FLING_SPEED

    private static UiBenchJankTestsHelper sInstance;
    private UiDevice mDevice;
    private Context mContext;
    private DisplayMetrics mDisplayMetrics;
    protected UiObject2 mContents;

    private UiBenchJankTestsHelper(Context context, UiDevice device) {
        mContext = context;
        mDevice = device;
        mDisplayMetrics = context.getResources().getDisplayMetrics();
    }

    public static UiBenchJankTestsHelper getInstance(Context context, UiDevice device) {
        if (sInstance == null) {
            sInstance = new UiBenchJankTestsHelper(context, device);
        }
        return sInstance;
    }

    /**
     * Launch activity using intent
     */
    public void launchActivity(String activityName, Bundle extras, String verifyText) {
        ComponentName cn = new ComponentName(PACKAGE_NAME,
                String.format("%s.%s", PACKAGE_NAME, activityName));
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (extras != null) {
            intent.putExtras(extras);
        }
        intent.setComponent(cn);
        // Launch the activity
        mContext.startActivity(intent);
        UiObject2 expectedTextCmp = mDevice.wait(Until.findObject(
                By.text(verifyText)), LONG_TIMEOUT);
        Assert.assertNotNull(String.format("Issue in opening %s", activityName),
                expectedTextCmp);
    }

    public void launchActivity(String activityName, String verifyText) {
        launchActivity(activityName, null, verifyText);
    }

    public void launchActivityAndAssert(String activityName, String verifyText) {
        launchActivity(activityName, verifyText);
        mContents = mDevice.wait(Until.findObject(By.res("android", "content")), TIMEOUT);
        Assert.assertNotNull(activityName + " isn't found", mContents);
    }

    /**
     * To perform the fling down and up on given content for flingCount number
     * of times
     */
    public void flingUpDown(UiObject2 content, int flingCount) {
        flingUpDown(content, flingCount, false);
    }

    public void flingUpDown(UiObject2 content, int flingCount, boolean reverse) {
        for (int count = 0; count < flingCount; count++) {
            SystemClock.sleep(SHORT_TIMEOUT);
            content.fling(reverse ? Direction.UP : Direction.DOWN);
            SystemClock.sleep(SHORT_TIMEOUT);
            content.fling(reverse ? Direction.DOWN : Direction.UP);
        }
    }

    /**
     * To perform the swipe right and left on given content for swipeCount number
     * of times
     */
    public void swipeRightLeft(UiObject2 content, int swipeCount) {
        for (int count = 0; count < swipeCount; count++) {
            SystemClock.sleep(SHORT_TIMEOUT);
            content.swipe(Direction.RIGHT, 1);
            SystemClock.sleep(SHORT_TIMEOUT);
            content.swipe(Direction.LEFT, 1);
        }
    }

    public void slowSingleFlingDown(UiObject2 content) {
        SystemClock.sleep(SHORT_TIMEOUT);
        content.fling(Direction.DOWN, (int)(SLOW_FLING_SPEED * mDisplayMetrics.density));
    }

    public void pressKeyCode(int keyCode) {
        SystemClock.sleep(KEY_DELAY);
        mDevice.pressKeyCode(keyCode);
    }
}
