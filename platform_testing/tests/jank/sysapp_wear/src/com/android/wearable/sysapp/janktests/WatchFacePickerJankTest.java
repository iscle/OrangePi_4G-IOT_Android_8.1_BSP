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

import android.os.Bundle;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

/**
 * Jank tests for watchFace picker on clockwork device
 */
public class WatchFacePickerJankTest extends JankTestBase {

    private static final String WEARABLE_APP_PACKAGE = "com.google.android.wearable.app";
    private static final String WATCHFACE_PICKER_FAVORITE_LIST_NAME = "watch_face_picker_list";
    private static final String TAG = "WatchFacePickerJankTest";
    private UiDevice mDevice;
    private SysAppTestHelper mHelper;
    private WatchFaceHelper mWfHelper;

    /*
     * (non-Javadoc)
     * @see junit.framework.TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        mDevice = UiDevice.getInstance(getInstrumentation());
        mHelper = SysAppTestHelper.getInstance(mDevice, getInstrumentation());
        mWfHelper = WatchFaceHelper.getInstance(mDevice, getInstrumentation());
        mDevice.wakeUp();
        super.setUp();
    }

    /**
     * Test the jank by open watchface picker (favorites)
     */
    @JankTest(beforeLoop = "startFromHome", afterTest = "goBackHome",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_WATCHFACE_PICKER_TEST)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testOpenWatchFacePicker() {
        mWfHelper.openPicker();
    }

    /**
     * Test the jank by adding watch face to favorites.
     */
    @JankTest(beforeLoop = "startFromWatchFacePickerFull",
            afterTest = "removeAllButOneWatchFace",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_WATCHFACE_PICKER_TEST_ADD_FAVORITE)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testSelectWatchFaceFromFullList() {
        mWfHelper.selectWatchFaceFromFullList(0);
    }

    /**
     * Test the jank by removing watch face from favorites.
     */
    @JankTest(beforeLoop = "startWithTwoWatchFaces", afterLoop = "resetToTwoWatchFaces",
            afterTest = "removeAllButOneWatchFace",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_WATCHFACE_PICKER_TEST)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testRemoveWatchFaceFromFavorites() {
        removeWatchFaceFromFavorites();
    }

    /**
     * Test the jank on flinging watch face picker.
     */
    @JankTest(beforeTest = "startWithFourWatchFaces", beforeLoop = "resetToWatchFacePicker",
            afterTest = "removeAllButOneWatchFace",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_WATCHFACE_PICKER_TEST)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testWatchFacePickerFling() {
        flingWatchFacePicker(5);
    }

    /**
     * Test the jank of flinging watch face full list picker.
     */
    @JankTest(beforeLoop = "startFromWatchFacePickerFull",
            afterLoop = "resetToWatchFacePickerFull",
            afterTest = "removeAllButOneWatchFace",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_WATCHFACE_PICKER_TEST)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testWatchFacePickerFullListFling() {
        flingWatchFacePickerFullList(5);
    }

    public void startFromHome() {
        mHelper.goBackHome();
    }

    public void startFromWatchFacePicker() {
        mWfHelper.startFromWatchFacePicker();
    }

    public void startFromWatchFacePickerFull() {
        Log.v(TAG, "Starting from watchface picker full list ...");
        startFromHome();
        mWfHelper.openPicker();
        mWfHelper.openPickerAllList();
    }

    public void startWithTwoWatchFaces() {
        Log.v(TAG, "Starting with two watchfaces ...");
        for (int i = 0; i < 2; ++i) {
            startFromHome();
            mWfHelper.openPicker();
            mWfHelper.openPickerAllList();
            mWfHelper.selectWatchFaceFromFullList(i);
        }
    }

    public void startWithFourWatchFaces() {
        Log.v(TAG, "Starting with four watchfaces ...");
        for (int i = 0; i < 4; ++i) {
            startFromHome();
            mWfHelper.openPicker();
            mWfHelper.openPickerAllList();
            mWfHelper.selectWatchFaceFromFullList(i);
        }
    }

    public void removeAllButOneWatchFace(Bundle metrics) {
        mWfHelper.removeAllButOneWatchFace();
        super.afterTest(metrics);
    }

    public void resetToWatchFacePicker() {
        Log.v(TAG, "Resetting to watchface picker screen ...");
        startFromWatchFacePicker();
    }

    public void resetToWatchFacePickerFull() {
        Log.v(TAG, "Resetting to watchface picker full list screen ...");
        startFromWatchFacePickerFull();
    }

    public void resetToTwoWatchFaces() {
        Log.v(TAG, "Resetting to two watchfaces in favorites ...");
        startWithTwoWatchFaces();
    }

    public void removeWatchFaceFromFavorites() {
        mWfHelper.removeWatchFaceFromFavorites();
    }

    // Ensuring that we head back to the first screen before launching the app again
    public void goBackHome(Bundle metrics) {
        Log.v(TAG, "Going back Home ...");
        startFromHome();
        super.afterTest(metrics);
    }

    private void flingWatchFacePicker(int iterations) {
        for (int i = 0; i < iterations; ++i) {
            // Start fling to right, then left, alternatively.
            if (i % 2 == 0) {
                mHelper.flingRight();
            } else {
                mHelper.flingLeft();
            }
        }
    }

    private void flingWatchFacePickerFullList(int iterations) {
        for (int i = 0; i < iterations; ++i) {
            // Start fling up, then down, alternatively.
            if (i % 2 == 0) {
                mHelper.flingUp();
            } else {
                mHelper.flingDown();
            }
        }
    }
}
