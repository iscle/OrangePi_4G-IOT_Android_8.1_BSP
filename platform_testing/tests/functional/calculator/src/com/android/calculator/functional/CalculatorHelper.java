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

package com.android.calculator.functional;

import android.content.Context;
import android.content.Intent;
import android.support.test.launcherhelper.ILauncherStrategy;
import android.support.test.launcherhelper.LauncherStrategyFactory;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.Until;
import android.support.test.uiautomator.UiSelector;
import android.test.InstrumentationTestCase;
import android.util.Log;
import junit.framework.Assert;

public class CalculatorHelper {
    private static CalculatorHelper mInstance = null;
    private static final int SHORT_TIMEOUT = 1000;
    private static final int LONG_TIMEOUT = 2000;
    public static final String PACKAGE_NAME = "com.google.android.calculator";
    public static final String APP_NAME = "Calculator";
    public static final String TEST_TAG = "CalculatorTests";
    public final int TIMEOUT = 500;
    private Context mContext = null;
    private UiDevice mDevice = null;
    public ILauncherStrategy mLauncherStrategy;

    private CalculatorHelper(UiDevice device, Context context) {
       mDevice = device;
       mContext = context;
       mLauncherStrategy = LauncherStrategyFactory.getInstance(mDevice).getLauncherStrategy();
    }

    public static CalculatorHelper getInstance(UiDevice device, Context context) {
        if (mInstance == null) {
        mInstance = new CalculatorHelper(device, context);
      }
        return mInstance;
    }

    public void launchApp(String packageName, String appName) {
        if (!mDevice.hasObject(By.pkg(packageName).depth(0))) {
        mLauncherStrategy.launch(appName, packageName);
      }
    }

    public void clickButton(String resource_id) {
        UiObject2 button = mDevice.wait(
            Until.findObject(By.res(PACKAGE_NAME, resource_id)),
                SHORT_TIMEOUT);
        Assert.assertNotNull("Element not found or pressed", button);
        button.click();
    }

    public void performCalculation(String input1, String operator, String input2) {
        clickButton(input1);
        clickButton(operator);
        clickButton(input2);
        clickButton("eq");
    }

    public void pressLongDigits() {
      for (int i=1; i<10; i++)    clickButton("digit_"+i);
    }

    public void pressNumber100000() {
        clickButton("digit_1");
        for (int i=0; i<5; i++)   clickButton("digit_0");
    }

    public String getResultText(String result) {
        UiObject2 resultText = mDevice.wait(
            Until.findObject(By.res(PACKAGE_NAME, result)),
                SHORT_TIMEOUT);
        Assert.assertNotNull("Result text box not found", resultText);
        return resultText.getText();
    }

    public void clearResults(String result) {
        UiObject2 resultText = mDevice.wait(
              Until.findObject(By.res(PACKAGE_NAME, result)),
                  SHORT_TIMEOUT);
        Assert.assertNotNull("Result box not found", resultText);
        resultText.clear();
    }

    public void scrollResults(String result) {
        UiObject2 resultText = mDevice.wait(
            Until.findObject(By.res(PACKAGE_NAME, result)),
                  SHORT_TIMEOUT);
        Assert.assertNotNull("Result text box not found", resultText);
        resultText.swipe(Direction.LEFT, 1.0f, 5000);
        Assert.assertEquals("Scroll failed","…41578750190521", getResultText("result"));
        mDevice.waitForIdle();
        resultText.swipe(Direction.RIGHT, 1.0f, 5000);
    }

    public void showAdvancedPad(){
        UiObject2 padAdvanced = mDevice.wait(
              Until.findObject(By.res(PACKAGE_NAME, "pad_advanced")),
                  SHORT_TIMEOUT);
            if (padAdvanced.isClickable()) {//don't click if already pad opened
                padAdvanced.click();
                Assert.assertNotNull("Advanced pad not found", padAdvanced);
            }
        mDevice.waitForIdle();
    }

    public void dismissAdvancedPad() {
        UiObject2 padAdvanced = mDevice.wait(
              Until.findObject(By.res(PACKAGE_NAME, "pad_advanced")),
                  SHORT_TIMEOUT);
        padAdvanced.swipe(Direction.RIGHT, 1.0f);
        mDevice.waitForIdle();
    }
}
