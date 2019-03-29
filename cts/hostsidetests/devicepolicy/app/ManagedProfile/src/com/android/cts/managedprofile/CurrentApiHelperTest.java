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

import static com.android.cts.managedprofile.CurrentApiHelper.getPublicApis;
import static com.android.cts.managedprofile.CurrentApiHelper.instantiate;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.test.AndroidTestCase;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests for {@link CurrentApiHelper}.
 */
public class CurrentApiHelperTest extends AndroidTestCase {

    private Class<DevicePolicyManager> mClazz;
    private Set<Method> mPublicApis;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mClazz = DevicePolicyManager.class;
        String testPackage = mClazz.getPackage().getName();
        String testClass = mClazz.getSimpleName();
        mPublicApis = new HashSet<>(getPublicApis(testPackage, testClass));
        assertTrue(mPublicApis.size() > 0);
    }

    /**
     * Test: {@link CurrentApiHelper#getPublicApis} includes public API methods.
     */
    public void testGetPublicApisIncludeMethods() throws Exception {
        Method publicMethod = mClazz.getMethod("lockNow");
        assertTrue(mPublicApis.contains(publicMethod));
        publicMethod = mClazz.getMethod("isProfileOwnerApp", String.class);
        assertTrue(mPublicApis.contains(publicMethod));
        publicMethod = mClazz.getMethod("resetPassword", String.class, int.class);
        assertTrue(mPublicApis.contains(publicMethod));
        publicMethod = mClazz.getMethod("hasGrantedPolicy", ComponentName.class, int.class);
        assertTrue(mPublicApis.contains(publicMethod));
        publicMethod = mClazz.getMethod("installCaCert", ComponentName.class, Class.forName("[B"));
        assertTrue(mPublicApis.contains(publicMethod));
    }

    /**
     * Test: {@link CurrentApiHelper#getPublicApis} excludes private, hidden or {@code @SystemApi}
     * methods.
     */
    public void testGetPublicApisExcludeMethods() throws Exception {
        Method privateMethod = mClazz.getDeclaredMethod("throwIfParentInstance", String.class);
        assertFalse(mPublicApis.contains(privateMethod));
        Method hiddenMethod = mClazz.getMethod("isDeviceProvisioned");
        assertFalse(mPublicApis.contains(hiddenMethod));
        Method systemMethod = mClazz.getMethod("getProfileOwnerNameAsUser", int.class);
        assertFalse(mPublicApis.contains(systemMethod));
    }

    /** Test for {@link CurrentApiHelper#instantiate}. */
    public void testInstantiate() {
        assertEquals(false, instantiate(boolean.class));
        assertEquals(0, instantiate(byte.class));
        assertEquals(0, instantiate(char.class));
        assertEquals(0, instantiate(double.class));
        assertEquals(0, instantiate(float.class));
        assertEquals(0, instantiate(int.class));
        assertEquals(0, instantiate(long.class));
        assertEquals(0, instantiate(short.class));
        assertNull(instantiate(String.class));
        assertNull(instantiate(Integer.class));
    }
}
