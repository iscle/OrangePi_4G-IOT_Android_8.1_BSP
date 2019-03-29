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
 * limitations under the License
 */
package com.android.compatibility.common.util;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.AssertionFailedError;

/**
 * Tests for {@line BusinessLogicDeviceExecutor}.
 */
@RunWith(AndroidJUnit4.class)
public class BusinessLogicDeviceExecutorTest {

    private static final String THIS_CLASS =
            "com.android.compatibility.common.util.BusinessLogicDeviceExecutorTest";
    private static final String METHOD_1 = THIS_CLASS + ".method1";
    private static final String METHOD_2 = THIS_CLASS + ".method2";
    private static final String METHOD_3 = THIS_CLASS + ".method3";
    private static final String METHOD_4 = THIS_CLASS + ".method4";
    private static final String METHOD_5 = THIS_CLASS + ".method5";
    private static final String METHOD_6 = THIS_CLASS + ".method6";
    private static final String METHOD_7 = THIS_CLASS + ".method7";
    private static final String METHOD_8 = THIS_CLASS + ".method8";
    private static final String METHOD_9 = THIS_CLASS + ".method9";
    private static final String METHOD_10 = THIS_CLASS + ".method10";
    private static final String FAKE_METHOD = THIS_CLASS + ".methodDoesntExist";
    private static final String ARG_STRING_1 = "arg1";
    private static final String ARG_STRING_2 = "arg2";

    private static final String OTHER_METHOD_1 = THIS_CLASS + "$OtherClass.method1";

    private String mInvoked = null;
    private Object[] mArgsUsed = null;
    private Context mContext;
    private BusinessLogicExecutor mExecutor;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mExecutor = new BusinessLogicDeviceExecutor(mContext, this);
        // reset the instance variables tracking the method invoked and the args used
        mInvoked = null;
        mArgsUsed = null;
        // reset the OtherClass class variable tracking the method invoked
        OtherClass.otherInvoked = null;
    }

    @Test
    public void testInvokeMethodInThisClass() throws Exception {
        mExecutor.invokeMethod(METHOD_1);
        // assert that mInvoked was set for this BusinessLogicDeviceExecutorTest instance
        assertEquals("Failed to invoke method in this class", mInvoked, METHOD_1);
    }

    @Test
    public void testInvokeMethodInOtherClass() throws Exception {
        mExecutor.invokeMethod(OTHER_METHOD_1);
        // assert that OtherClass.method1 was invoked, and static field of OtherClass was changed
        assertEquals("Failed to invoke method in other class", OtherClass.otherInvoked,
                OTHER_METHOD_1);
    }

    @Test
    public void testInvokeMethodWithStringArgs() throws Exception {
        mExecutor.invokeMethod(METHOD_2, ARG_STRING_1, ARG_STRING_2);
        assertEquals("Failed to invoke method in this class", mInvoked, METHOD_2);
        // assert both String arguments were correctly set for method2
        assertEquals("Failed to set first argument", mArgsUsed[0], ARG_STRING_1);
        assertEquals("Failed to set second argument", mArgsUsed[1], ARG_STRING_2);
    }

    @Test
    public void testInvokeMethodWithStringAndContextArgs() throws Exception {
        mExecutor.invokeMethod(METHOD_3, ARG_STRING_1);
        assertEquals("Failed to invoke method in this class", mInvoked, METHOD_3);
        // assert that String arg and Context arg were correctly set for method3
        assertEquals("Failed to set first argument", mArgsUsed[0], ARG_STRING_1);
        assertEquals("Failed to set second argument", mArgsUsed[1], mContext);
    }

    @Test
    public void testInvokeMethodWithContextAndStringArgs() throws Exception {
        mExecutor.invokeMethod(METHOD_4, ARG_STRING_1);
        assertEquals("Failed to invoke method in this class", mInvoked, METHOD_4);
        // Like testInvokeMethodWithStringAndContextArgs, but flip the args for method4
        assertEquals("Failed to set first argument", mArgsUsed[0], mContext);
        assertEquals("Failed to set second argument", mArgsUsed[1], ARG_STRING_1);
    }

    @Test
    public void testInvokeMethodWithStringArrayArg() throws Exception {
        mExecutor.invokeMethod(METHOD_5, ARG_STRING_1, ARG_STRING_2);
        assertEquals("Failed to invoke method in this class", mInvoked, METHOD_5);
        // assert both String arguments were correctly set for method5
        assertEquals("Failed to set first argument", mArgsUsed[0], ARG_STRING_1);
        assertEquals("Failed to set second argument", mArgsUsed[1], ARG_STRING_2);
    }

    @Test
    public void testInvokeMethodWithEmptyStringArrayArg() throws Exception {
        mExecutor.invokeMethod(METHOD_5);
        assertEquals("Failed to invoke method in this class", mInvoked, METHOD_5);
        // assert no String arguments were set for method5
        assertEquals("Incorrectly set args", mArgsUsed.length, 0);
    }

    @Test
    public void testInvokeMethodWithStringAndStringArrayArgs() throws Exception {
        mExecutor.invokeMethod(METHOD_6, ARG_STRING_1, ARG_STRING_2);
        assertEquals("Failed to invoke method in this class", mInvoked, METHOD_6);
        // assert both String arguments were correctly set for method6
        assertEquals("Failed to set first argument", mArgsUsed[0], ARG_STRING_1);
        assertEquals("Failed to set second argument", mArgsUsed[1], ARG_STRING_2);
    }

    @Test
    public void testInvokeMethodWithAllArgTypes() throws Exception {
        mExecutor.invokeMethod(METHOD_7, ARG_STRING_1, ARG_STRING_2);
        assertEquals("Failed to invoke method in this class", mInvoked, METHOD_7);
        // assert all arguments were correctly set for method7
        assertEquals("Failed to set first argument", mArgsUsed[0], ARG_STRING_1);
        assertEquals("Failed to set second argument", mArgsUsed[1], mContext);
        assertEquals("Failed to set third argument", mArgsUsed[2], ARG_STRING_2);
    }

    @Test
    public void testInvokeOverloadedMethodOneArg() throws Exception {
        mExecutor.invokeMethod(METHOD_1, ARG_STRING_1);
        assertEquals("Failed to invoke method in this class", mInvoked, METHOD_1);
        assertEquals("Set wrong number of arguments", mArgsUsed.length, 1);
        assertEquals("Failed to set first argument", mArgsUsed[0], ARG_STRING_1);
    }

    @Test
    public void testInvokeOverloadedMethodTwoArgs() throws Exception {
        mExecutor.invokeMethod(METHOD_1, ARG_STRING_1, ARG_STRING_2);
        assertEquals("Failed to invoke method in this class", mInvoked, METHOD_1);
        assertEquals("Set wrong number of arguments", mArgsUsed.length, 2);
        assertEquals("Failed to set first argument", mArgsUsed[0], ARG_STRING_1);
        assertEquals("Failed to set second argument", mArgsUsed[1], ARG_STRING_2);
    }

    @Test(expected = RuntimeException.class)
    public void testInvokeNonExistentMethod() throws Exception {
        mExecutor.invokeMethod(FAKE_METHOD, ARG_STRING_1, ARG_STRING_2);
    }

    @Test(expected = RuntimeException.class)
    public void testInvokeMethodTooManyArgs() throws Exception {
        mExecutor.invokeMethod(METHOD_3, ARG_STRING_1, ARG_STRING_2);
    }

    @Test(expected = RuntimeException.class)
    public void testInvokeMethodTooFewArgs() throws Exception {
        mExecutor.invokeMethod(METHOD_2, ARG_STRING_1);
    }

    @Test(expected = RuntimeException.class)
    public void testInvokeMethodIncompatibleArgs() throws Exception {
        mExecutor.invokeMethod(METHOD_8, ARG_STRING_1);
    }

    @Test
    public void testExecuteConditionCheckReturnValue() throws Exception {
        assertTrue("Wrong return value",
                mExecutor.executeCondition(METHOD_2, ARG_STRING_1, ARG_STRING_1));
        assertFalse("Wrong return value",
                mExecutor.executeCondition(METHOD_2, ARG_STRING_1, ARG_STRING_2));
    }

    @Test(expected = RuntimeException.class)
    public void testExecuteInvalidCondition() throws Exception {
        mExecutor.executeCondition(METHOD_1); // method1 does not return type boolean
    }

    @Test
    public void testExecuteAction() throws Exception {
        mExecutor.executeAction(METHOD_2, ARG_STRING_1, ARG_STRING_2);
        assertEquals("Failed to invoke method in this class", mInvoked, METHOD_2);
        // assert both String arguments were correctly set for method2
        assertEquals("Failed to set first argument", mArgsUsed[0], ARG_STRING_1);
        assertEquals("Failed to set second argument", mArgsUsed[1], ARG_STRING_2);
    }

    @Test(expected = RuntimeException.class)
    public void testExecuteActionThrowException() throws Exception {
        mExecutor.executeAction(METHOD_9);
    }

    @Test
    public void testExecuteActionViolateAssumption() throws Exception {
        try {
            mExecutor.executeAction(METHOD_10);
            // JUnit4 doesn't support expecting AssumptionViolatedException with "expected"
            // attribute on @Test annotation, so test using Assert.fail()
            fail("Expected assumption failure");
        } catch (AssumptionViolatedException e) {
            // expected
        }
    }

    public void method1() {
        mInvoked = METHOD_1;
    }

    // overloaded method with one arg
    public void method1(String arg1) {
        mInvoked = METHOD_1;
        mArgsUsed = new Object[]{arg1};
    }

    // overloaded method with two args
    public void method1(String arg1, String arg2) {
        mInvoked = METHOD_1;
        mArgsUsed = new Object[]{arg1, arg2};
    }

    public boolean method2(String arg1, String arg2) {
        mInvoked = METHOD_2;
        mArgsUsed = new Object[]{arg1, arg2};
        return arg1.equals(arg2);
    }

    public void method3(String arg1, Context arg2) {
        mInvoked = METHOD_3;
        mArgsUsed = new Object[]{arg1, arg2};
    }

    // Same as method3, but flipped args
    public void method4(Context arg1, String arg2) {
        mInvoked = METHOD_4;
        mArgsUsed = new Object[]{arg1, arg2};
    }

    public void method5(String... args) {
        mInvoked = METHOD_5;
        mArgsUsed = args;
    }

    public void method6(String arg1, String... moreArgs) {
        mInvoked = METHOD_6;
        List<String> allArgs = new ArrayList<>();
        allArgs.add(arg1);
        allArgs.addAll(Arrays.asList(moreArgs));
        mArgsUsed = allArgs.toArray(new String[0]);
    }

    public void method7(String arg1, Context arg2, String... moreArgs) {
        mInvoked = METHOD_7;
        List<Object> allArgs = new ArrayList<>();
        allArgs.add(arg1);
        allArgs.add(arg2);
        allArgs.addAll(Arrays.asList(moreArgs));
        mArgsUsed = allArgs.toArray(new Object[0]);
    }

    public void method8(String arg1, Integer arg2) {
        // This method should never be successfully invoked, since Integer parameter types are
        // unsupported for the BusinessLogic service
    }

    // throw AssertionFailedError
    public void method9() throws AssertionFailedError {
        assertTrue(false);
    }

    // throw AssumptionViolatedException
    public void method10() throws AssumptionViolatedException {
        assumeTrue(false);
    }

    public static class OtherClass {

        public static String otherInvoked = null;

        public void method1() {
            otherInvoked = OTHER_METHOD_1;
        }
    }
}
