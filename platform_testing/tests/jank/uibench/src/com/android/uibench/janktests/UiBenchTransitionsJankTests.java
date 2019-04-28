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

import static com.android.uibench.janktests.UiBenchJankTestsHelper.EXPECTED_FRAMES;
import static com.android.uibench.janktests.UiBenchJankTestsHelper.PACKAGE_NAME;

import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import junit.framework.Assert;

/**
 * Jank benchmark Text tests for UiBench app
 */

public class UiBenchTransitionsJankTests extends JankTestBase {

    private UiDevice mDevice;
    private UiBenchJankTestsHelper mHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mDevice.setOrientationNatural();
        mHelper = UiBenchJankTestsHelper.getInstance(
                this.getInstrumentation().getContext(), mDevice);
    }

    @Override
    protected void tearDown() throws Exception {
        mDevice.unfreezeRotation();
        super.tearDown();
    }

    // Open Transitions
    public void openActivityTransition() {
        mHelper.launchActivity("ActivityTransition",
                "Transitions/Activity Transition");
    }

    // Get the image to click
    public void clickImage(String imageName) {
        UiObject2 image = mDevice.wait(Until.findObject(
                By.res(mHelper.PACKAGE_NAME, imageName)), mHelper.TIMEOUT);
        Assert.assertNotNull(imageName + "Image not found", image);
        image.clickAndWait(Until.newWindow(), mHelper.TIMEOUT);
        mDevice.pressBack();
    }

    // Measures jank for activity transition animation
    @JankTest(beforeTest = "openActivityTransition", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testActivityTransitionsAnimation() {
        clickImage("ducky");
        clickImage("woot");
        clickImage("ball");
        clickImage("block");
        clickImage("jellies");
        clickImage("mug");
        clickImage("pencil");
        clickImage("scissors");
    }
}
