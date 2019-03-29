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

import android.app.admin.DevicePolicyManager;
import android.util.Log;

import com.google.common.collect.ImmutableSet;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tests related to the parent profile of a managed profile.
 *
 * The parent profile is obtained by
 * {@link android.app.admin.DevicePolicyManager#getParentProfileInstance}.
 */
public class ParentProfileTest extends BaseManagedProfileTest {

    /**
     * A whitelist of public API methods in {@link android.app.admin.DevicePolicyManager}
     * that are supported on a parent profile.
     */
    private static final ImmutableSet<String> SUPPORTED_APIS = new ImmutableSet.Builder<String>()
            .add("getPasswordQuality")
            .add("setPasswordQuality")
            .add("getPasswordMinimumLength")
            .add("setPasswordMinimumLength")
            .add("getPasswordMinimumUpperCase")
            .add("setPasswordMinimumUpperCase")
            .add("getPasswordMinimumLowerCase")
            .add("setPasswordMinimumLowerCase")
            .add("getPasswordMinimumLetters")
            .add("setPasswordMinimumLetters")
            .add("getPasswordMinimumNumeric")
            .add("setPasswordMinimumNumeric")
            .add("getPasswordMinimumSymbols")
            .add("setPasswordMinimumSymbols")
            .add("getPasswordMinimumNonLetter")
            .add("setPasswordMinimumNonLetter")
            .add("getPasswordHistoryLength")
            .add("setPasswordHistoryLength")
            .add("getPasswordExpirationTimeout")
            .add("setPasswordExpirationTimeout")
            .add("getPasswordExpiration")
            .add("getPasswordMaximumLength")
            .add("isActivePasswordSufficient")
            .add("getCurrentFailedPasswordAttempts")
            .add("getMaximumFailedPasswordsForWipe")
            .add("setMaximumFailedPasswordsForWipe")
            .add("getMaximumTimeToLock")
            .add("setMaximumTimeToLock")
            .add("lockNow")
            .add("getKeyguardDisabledFeatures")
            .add("setKeyguardDisabledFeatures")
            .add("getTrustAgentConfiguration")
            .add("setTrustAgentConfiguration")
            .add("getRequiredStrongAuthTimeout")
            .add("setRequiredStrongAuthTimeout")
            .build();

    private static final String LOG_TAG = "ParentProfileTest";

    private static final String PACKAGE_NAME = DevicePolicyManager.class.getPackage().getName();
    private static final String CLASS_NAME = DevicePolicyManager.class.getSimpleName();

    /**
     * Verify that all public API methods of {@link android.app.admin.DevicePolicyManager},
     * except those explicitly whitelisted in {@link #SUPPORTED_APIS},
     * throw a {@link SecurityException} when called on a parent profile.
     *
     * <p><b>Note:</b> System API methods (i.e. those with the
     * {@link android.annotation.SystemApi} annotation) are NOT tested.
     */
    public void testParentProfileApiDisabled() throws Exception {
        List<Method> methods = CurrentApiHelper.getPublicApis(PACKAGE_NAME, CLASS_NAME);
        assertValidMethodNames(SUPPORTED_APIS, methods);

        ArrayList<String> failedMethods = new ArrayList<String>();

        for (Method method : methods) {
            String methodName = method.getName();
            if (SUPPORTED_APIS.contains(methodName)) {
                continue;
            }

            try {
                int paramCount = method.getParameterCount();
                Object[] params = new Object[paramCount];
                Class[] paramTypes = method.getParameterTypes();
                for (int i = 0; i < paramCount; ++i) {
                    params[i] = CurrentApiHelper.instantiate(paramTypes[i]);
                }
                method.invoke(mParentDevicePolicyManager, params);

            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof SecurityException) {
                    // Method throws SecurityException as expected
                    continue;
                } else {
                    Log.e(LOG_TAG,
                            methodName + " throws exception other than SecurityException.", e);
                }
            }

            // Either no exception is thrown, or the exception thrown is not a SecurityException
            failedMethods.add(methodName);
            Log.e(LOG_TAG, methodName + " failed to throw SecurityException");
        }

        assertTrue("Some method(s) failed to throw SecurityException: " + failedMethods,
                failedMethods.isEmpty());
    }

    private void assertValidMethodNames(Collection<String> names, Collection<Method> allMethods) {
        Set<String> allNames = allMethods.stream()
                .map(Method::getName)
                .collect(Collectors.toSet());

        for (String name : names) {
            assertTrue(name + " is not found in the API list", allNames.contains(name));
        }
    }
}
