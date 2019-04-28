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
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.inputmethod.InputMethodManager;

public class CalculatorTests extends InstrumentationTestCase {
    private CalculatorHelper mCalculatorHelper = null;
    private static final int SHORT_TIMEOUT = 1000;
    private static final int LONG_TIMEOUT = 2000;
    private UiDevice mDevice = null;
    private Context mContext;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mContext = getInstrumentation().getContext();
        mDevice.setOrientationNatural();
        mDevice.pressHome();
        mCalculatorHelper = CalculatorHelper.getInstance(mDevice, mContext);
    }

    @Override
    public void tearDown() throws Exception {
        mDevice.pressHome();
        mDevice.unfreezeRotation();
        super.tearDown();
    }

    // Launch the app
    public void launchCalculator() {
        mCalculatorHelper.launchApp(CalculatorHelper.PACKAGE_NAME, CalculatorHelper.APP_NAME);
        mDevice.waitForIdle();
    }

    @SmallTest
    //Test to verify basic addition functionality
    public void testAdd() throws Exception {
        launchCalculator();
        mCalculatorHelper.performCalculation("digit_9","op_add","digit_9");
        assertEquals("Results are wrong", "18", mCalculatorHelper.getResultText("result"));
        mDevice.waitForIdle();
        mDevice.pressBack();
    }

    @SmallTest
    //Test to verify basic subraction functionality
    public void testSubtract() throws Exception {
        launchCalculator();
        mCalculatorHelper.performCalculation("digit_6","op_sub","digit_4");
        assertEquals("Results are wrong","2", mCalculatorHelper.getResultText("result"));
        mDevice.waitForIdle();
        mDevice.pressBack();
    }

    @SmallTest
    //Test to verify basic multiplication functionality
    public void testMultiply() throws Exception {
        launchCalculator();
        mCalculatorHelper.performCalculation("digit_7","op_mul","digit_5");
        assertEquals("Results are wrong","35", mCalculatorHelper.getResultText("result"));
        mDevice.waitForIdle();
        mDevice.pressBack();
    }

    @SmallTest
    //Test to verify basic divition functionality
    public void testDivide() throws Exception {
        launchCalculator();
        mCalculatorHelper.performCalculation("digit_8","op_div","digit_2");
        assertEquals("Results are wrong","4", mCalculatorHelper.getResultText("result"));
        mDevice.waitForIdle();
        mDevice.pressBack();
    }

    @SmallTest
    //Test to verify to clear the results
    public void testClearButton() throws Exception {
        launchCalculator();
        mCalculatorHelper.performCalculation("digit_9","op_mul","digit_9");
        mCalculatorHelper.clickButton("clr");
        UiObject2 deleteButton = mDevice.wait(
            Until.findObject(By.res(mCalculatorHelper.PACKAGE_NAME, "del")),
                SHORT_TIMEOUT);
        if (deleteButton !=null) { //Verify the button is changed to delete after clear
            assertNull(mCalculatorHelper.getResultText("result"));
        }
        mDevice.waitForIdle();
        mDevice.pressBack();
    }

    @SmallTest
    // Test divide by zero error
    public void testDivideByZero() throws Exception {
        launchCalculator();
        mCalculatorHelper.performCalculation("digit_1", "op_div","digit_0");
        assertEquals("Error", "Can't divide by 0", mCalculatorHelper.getResultText("result"));
        mDevice.waitForIdle();
        mDevice.pressBack();
    }

    @MediumTest
    // Test Scroll funtion in long results
    public void testScrollLongResult() throws Exception {
        launchCalculator();
        mCalculatorHelper.pressLongDigits();
        mCalculatorHelper.clickButton("op_mul");
        mCalculatorHelper.pressLongDigits();
        mCalculatorHelper.clickButton("eq");
        mCalculatorHelper.scrollResults("result");
        assertEquals("Scroll failed","1.52415787501E16", mCalculatorHelper.getResultText("result"));
        mDevice.waitForIdle();
        mDevice.pressBack();
    }

    @MediumTest
    // Test to verify the advanced panel and basic operation works
    public void testAdvancedOperation() throws Exception {
        launchCalculator();
        mCalculatorHelper.showAdvancedPad();
        mCalculatorHelper.clickButton("fun_cos");
        mCalculatorHelper.dismissAdvancedPad();
        mCalculatorHelper.clickButton("digit_0");
        mCalculatorHelper.clickButton("eq");
        assertEquals("Results are wrong", "1",mCalculatorHelper.getResultText("result"));
        mDevice.waitForIdle();
        mDevice.pressBack();
    }

    @MediumTest
    //Test timeouts on complex calculations
    public void testComplexCalculationTimeout() throws Exception {
        launchCalculator();
        mCalculatorHelper.pressNumber100000();
        mCalculatorHelper.showAdvancedPad();
        mCalculatorHelper.clickButton("op_fact");
        mCalculatorHelper.dismissAdvancedPad();
        mCalculatorHelper.clickButton("eq");
        mDevice.waitForIdle();
        UiObject2 alertTitle = mDevice.wait(
            Until.findObject(By.res("android:id/alertTitle")), SHORT_TIMEOUT);
        assertNotNull("Alert pop up not found", alertTitle);
        UiObject2 msgText = mDevice.wait(
            Until.findObject(By.res(mCalculatorHelper.PACKAGE_NAME, "message")),
                SHORT_TIMEOUT);
        mDevice.waitForIdle();
        assertEquals("Message not found", "Value may be infinite or undefined.",msgText.getText());
        UiObject2 dismissButton = mDevice.wait(
            Until.findObject(By.res("android:id/button2")), SHORT_TIMEOUT);
        assertNotNull("Dismiss button not found", dismissButton);
        dismissButton.click();
        mCalculatorHelper.clearResults("formula");
        mDevice.pressBack();
    }

    @MediumTest
    // Test DEG/RAD switch happens and display changes
    public void testDegRadSwitch() throws Exception {
        launchCalculator();
        mCalculatorHelper.showAdvancedPad();

        UiObject2 toggleButton = mDevice.wait(
            Until.findObject(By.res(mCalculatorHelper.PACKAGE_NAME, "toggle_mode")),
                SHORT_TIMEOUT);
        assertNotNull("Toggle Button not found", toggleButton);

        UiObject2 modeBox = mDevice.wait(
            Until.findObject(By.res(mCalculatorHelper.PACKAGE_NAME, "mode")),
                SHORT_TIMEOUT);
        assertNotNull("Mode Box not found", modeBox);

        for (int i=0; i<3; i++) { //Test the toggle button 3 times
            mCalculatorHelper.clickButton("toggle_mode");
            mDevice.waitForIdle();
            assertNotSame("Switch Failed",toggleButton.getText(),modeBox.getText());
        }
        mCalculatorHelper.dismissAdvancedPad();
        mDevice.waitForIdle();
        mDevice.pressBack();
    }
}
