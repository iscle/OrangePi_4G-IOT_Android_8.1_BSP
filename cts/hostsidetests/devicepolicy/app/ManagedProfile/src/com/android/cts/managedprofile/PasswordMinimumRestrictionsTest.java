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

package com.android.cts.managedprofile;

import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class PasswordMinimumRestrictionsTest extends BaseManagedProfileTest {

    private static final int TEST_PASSWORD_LENGTH = 5;
    private static final int TEST_PASSWORD_LENGTH_LOW = 2;
    private static final String[] METHOD_LIST = {
            "PasswordMinimumLength",
            "PasswordMinimumUpperCase",
            "PasswordMinimumLowerCase",
            "PasswordMinimumLetters",
            "PasswordMinimumNumeric",
            "PasswordMinimumSymbols",
            "PasswordMinimumNonLetter",
            "PasswordHistoryLength"};

    private DevicePolicyManager mParentDpm;
    private int mCurrentAdminPreviousPasswordQuality;
    private int mParentPreviousPasswordQuality;
    private List<Integer> mCurrentAdminPreviousPasswordRestriction = new ArrayList<Integer>();
    private List<Integer> mParentPreviousPasswordRestriction = new ArrayList<Integer>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mParentDpm = mDevicePolicyManager.getParentProfileInstance(ADMIN_RECEIVER_COMPONENT);
        mCurrentAdminPreviousPasswordQuality =
                mDevicePolicyManager.getPasswordQuality(ADMIN_RECEIVER_COMPONENT);
        mParentPreviousPasswordQuality = mParentDpm.getPasswordQuality(ADMIN_RECEIVER_COMPONENT);
        mDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT, PASSWORD_QUALITY_COMPLEX);
        mParentDpm.setPasswordQuality(ADMIN_RECEIVER_COMPONENT, PASSWORD_QUALITY_COMPLEX);
        for (String method : METHOD_LIST) {
            mCurrentAdminPreviousPasswordRestriction
                    .add(invokeGetMethod(method, mDevicePolicyManager, ADMIN_RECEIVER_COMPONENT));
            mParentPreviousPasswordRestriction
                    .add(invokeGetMethod(method, mParentDpm, ADMIN_RECEIVER_COMPONENT));
        }
    }

    @Override
    protected void tearDown() throws Exception {
        for (int i = 0; i < METHOD_LIST.length; i++) {
            invokeSetMethod(METHOD_LIST[i], mDevicePolicyManager, ADMIN_RECEIVER_COMPONENT,
                    mCurrentAdminPreviousPasswordRestriction.get(i));
            invokeSetMethod(METHOD_LIST[i], mParentDpm, ADMIN_RECEIVER_COMPONENT,
                    mCurrentAdminPreviousPasswordRestriction.get(i));
        }
        mDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT,
                mCurrentAdminPreviousPasswordQuality);
        mParentDpm.setPasswordQuality(ADMIN_RECEIVER_COMPONENT, mParentPreviousPasswordQuality);
        super.tearDown();
    }

    public void testPasswordMinimumRestriction() throws Exception {
        for (int i = 0; i < METHOD_LIST.length; i++) {
            invokeSetMethod(METHOD_LIST[i], mDevicePolicyManager, ADMIN_RECEIVER_COMPONENT,
                    TEST_PASSWORD_LENGTH + i);
            invokeSetMethod(METHOD_LIST[i], mParentDpm, ADMIN_RECEIVER_COMPONENT,
                    TEST_PASSWORD_LENGTH + 2 * i);

            // Passing the admin component returns the value set for that admin, rather than
            // aggregated values.
            assertEquals(
                    getMethodName(METHOD_LIST[i])
                            + " failed to get expected value on mDevicePolicyManager.",
                    TEST_PASSWORD_LENGTH + i, invokeGetMethod(METHOD_LIST[i], mDevicePolicyManager,
                            ADMIN_RECEIVER_COMPONENT));

            // Passing the admin component returns the value set for that admin, rather than
            // aggregated values.
            assertEquals(
                    getMethodName(METHOD_LIST[i]) + " failed to get expected value on mParentDpm.",
                    TEST_PASSWORD_LENGTH + 2 * i,
                    invokeGetMethod(METHOD_LIST[i], mParentDpm, ADMIN_RECEIVER_COMPONENT));
        }
    }

    public void testSetPasswordMinimumRestrictionWithNull() {
        // Test with mDevicePolicyManager.
        for (String method : METHOD_LIST) {
            try {
                invokeSetMethod(method, mDevicePolicyManager, null, TEST_PASSWORD_LENGTH);
                fail("Exception should have been thrown for null admin ComponentName");
            } catch (Exception e) {
                if (!(e.getCause() instanceof NullPointerException)) {
                    fail("Failed to execute set method: " + setMethodName(method));
                }
                // Expected to throw NullPointerException.
            }
        }

        // Test with mParentDpm.
        for (String method : METHOD_LIST) {
            try {
                invokeSetMethod(method, mParentDpm, null, TEST_PASSWORD_LENGTH);
                fail("Exception should have been thrown for null admin ComponentName");
            } catch (Exception e) {
                if (!(e.getCause() instanceof NullPointerException)) {
                    fail("Failed to execute set method: " + setMethodName(method));
                }
                // Expected to throw NullPointerException.
            }
        }
    }

    public void testGetPasswordMinimumRestrictionWithNullAdmin() throws Exception {
        for (int i = 0; i < METHOD_LIST.length; i++) {
            // Check getMethod with null admin. It should return the aggregated value (which is the
            // only value set so far).
            invokeSetMethod(METHOD_LIST[i], mDevicePolicyManager, ADMIN_RECEIVER_COMPONENT,
                    TEST_PASSWORD_LENGTH_LOW + i);
            assertEquals(getMethodName(METHOD_LIST[i]) + " failed.", TEST_PASSWORD_LENGTH_LOW + i,
                    invokeGetMethod(METHOD_LIST[i], mDevicePolicyManager, null));

            // Set strict password minimum restriction using parent instance.
            invokeSetMethod(METHOD_LIST[i], mParentDpm, ADMIN_RECEIVER_COMPONENT,
                    TEST_PASSWORD_LENGTH + i);
            // With null admin, the restriction should be the aggregate of all admins.
            assertEquals(getMethodName(METHOD_LIST[i]) + " failed.", TEST_PASSWORD_LENGTH + i,
                    invokeGetMethod(METHOD_LIST[i], mDevicePolicyManager, null));
            // With null admin, the restriction should be the aggregate of all admins.
            assertEquals(getMethodName(METHOD_LIST[i]) + " failed.", TEST_PASSWORD_LENGTH + i,
                    invokeGetMethod(METHOD_LIST[i], mParentDpm, null));

            // Passing the admin component returns the value set for that admin, rather than
            // aggregated values.
            assertEquals(getMethodName(METHOD_LIST[i]) + " failed.", TEST_PASSWORD_LENGTH_LOW + i,
                    invokeGetMethod(METHOD_LIST[i], mDevicePolicyManager,
                            ADMIN_RECEIVER_COMPONENT));
            assertEquals(getMethodName(METHOD_LIST[i]) + " failed.", TEST_PASSWORD_LENGTH + i,
                    invokeGetMethod(METHOD_LIST[i], mParentDpm, ADMIN_RECEIVER_COMPONENT));

            // Set strict password minimum restriction on current admin.
            invokeSetMethod(METHOD_LIST[i], mDevicePolicyManager, ADMIN_RECEIVER_COMPONENT,
                    TEST_PASSWORD_LENGTH + i);
            // Set password minimum restriction using parent instance.
            invokeSetMethod(METHOD_LIST[i], mParentDpm, ADMIN_RECEIVER_COMPONENT,
                    TEST_PASSWORD_LENGTH_LOW + i);
            // With null admin, the restriction should be the aggregate of all admins.
            assertEquals(getMethodName(METHOD_LIST[i]) + " failed.", TEST_PASSWORD_LENGTH + i,
                    invokeGetMethod(METHOD_LIST[i], mDevicePolicyManager, null));
            // With null admin, the restriction should be the aggregate of all admins.
            assertEquals(getMethodName(METHOD_LIST[i]) + " failed.", TEST_PASSWORD_LENGTH + i,
                    invokeGetMethod(METHOD_LIST[i], mParentDpm, null));

            // Passing the admin component returns the value set for that admin, rather than
            // aggregated values.
            assertEquals(getMethodName(METHOD_LIST[i]) + " failed.", TEST_PASSWORD_LENGTH + i,
                    invokeGetMethod(METHOD_LIST[i], mDevicePolicyManager,
                            ADMIN_RECEIVER_COMPONENT));
            assertEquals(getMethodName(METHOD_LIST[i]) + " failed.", TEST_PASSWORD_LENGTH_LOW + i,
                    invokeGetMethod(METHOD_LIST[i], mParentDpm, ADMIN_RECEIVER_COMPONENT));
        }
    }

    /**
     * Calls dpm.set{methodName} with given component name and length arguments using reflection.
     */
    private void invokeSetMethod(String methodName, DevicePolicyManager dpm,
            ComponentName componentName, int length) throws Exception {
        final Method setMethod = DevicePolicyManager.class.getMethod(setMethodName(methodName),
                ComponentName.class, int.class);
        setMethod.invoke(dpm, componentName, length);
    }

    /**
     * Calls dpm.get{methodName} with given component name using reflection.
     */
    private int invokeGetMethod(String methodName, DevicePolicyManager dpm,
            ComponentName componentName) throws Exception {
        final Method getMethod =
                DevicePolicyManager.class.getMethod(getMethodName(methodName), ComponentName.class);
        return (int) getMethod.invoke(dpm, componentName);
    }

    private String setMethodName(String methodName) {
        return "set" + methodName;
    }

    private String getMethodName(String methodName) {
        return "get" + methodName;
    }
}
