/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.test.ActivityInstrumentationTestCase2;

import static com.android.cts.managedprofile.BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT;

/**
 * Test for {@link DevicePolicyManager#addCrossProfileIntentFilter} API.
 *
 * Note that it expects that there is an activity responding to {@code PrimaryUserActivity.ACTION}
 * in the primary profile, one to {@code ManagedProfileActivity.ACTION} in the secondary profile,
 * and one to {@code AllUsersActivity.ACTION} in both profiles.
 */
public class ManagedProfileTest extends ActivityInstrumentationTestCase2<TestActivity> {

    private PackageManager mPackageManager;
    private DevicePolicyManager mDevicePolicyManager;

    public ManagedProfileTest() {
        super(TestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // As the way to access Instrumentation is changed in the new runner, we need to inject it
        // manually into ActivityInstrumentationTestCase2. ActivityInstrumentationTestCase2 will
        // be marked as deprecated and replaced with ActivityTestRule.
        injectInstrumentation(InstrumentationRegistry.getInstrumentation());
        mPackageManager = getActivity().getPackageManager();
        mDevicePolicyManager = (DevicePolicyManager)
                getActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager.clearCrossProfileIntentFilters(ADMIN_RECEIVER_COMPONENT);
        super.tearDown();
    }

    public void testClearCrossProfileIntentFilters() {
        IntentFilter testIntentFilter = new IntentFilter();
        testIntentFilter.addAction(PrimaryUserActivity.ACTION);
        mDevicePolicyManager.addCrossProfileIntentFilter(ADMIN_RECEIVER_COMPONENT,
                testIntentFilter, DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);
        assertEquals(1, mPackageManager.queryIntentActivities(
                new Intent(PrimaryUserActivity.ACTION), /* flags = */ 0).size());

        mDevicePolicyManager.clearCrossProfileIntentFilters(ADMIN_RECEIVER_COMPONENT);

        assertTrue(mPackageManager.queryIntentActivities(
                new Intent(PrimaryUserActivity.ACTION), /* flags = */ 0).isEmpty());
        getActivity().startActivity(ManagedProfileActivity.ACTION);
        assertTrue(getActivity().checkActivityStarted());
    }

    public void testAddCrossProfileIntentFilter_primary() {
        assertEquals(0, mPackageManager.queryIntentActivities(
                new Intent(PrimaryUserActivity.ACTION), /* flags = */ 0).size());

        IntentFilter testIntentFilter = new IntentFilter();
        testIntentFilter.addAction(PrimaryUserActivity.ACTION);
        mDevicePolicyManager.addCrossProfileIntentFilter(ADMIN_RECEIVER_COMPONENT,
                testIntentFilter, DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);

        assertEquals(1, mPackageManager.queryIntentActivities(
                new Intent(PrimaryUserActivity.ACTION), /* flags = */ 0).size());
        getActivity().startActivity(PrimaryUserActivity.ACTION);
        assertTrue(getActivity().checkActivityStarted());
    }

    public void testAddCrossProfileIntentFilter_all() {
        assertEquals(1, mPackageManager.queryIntentActivities(
                new Intent(AllUsersActivity.ACTION), /* flags = */ 0).size());

        IntentFilter testIntentFilter = new IntentFilter();
        testIntentFilter.addAction(AllUsersActivity.ACTION);
        mDevicePolicyManager.addCrossProfileIntentFilter(ADMIN_RECEIVER_COMPONENT,
                testIntentFilter, DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);

        assertEquals(2, mPackageManager.queryIntentActivities(
                new Intent(AllUsersActivity.ACTION), /* flags = */ 0).size());
        // If we used startActivity(), the user would have a disambiguation dialog presented which
        // requires human intervention, so we won't be testing like that
    }

    public void testAddCrossProfileIntentFilter_managed() {
        assertEquals(1, mPackageManager.queryIntentActivities(
                new Intent(ManagedProfileActivity.ACTION), /* flags = */ 0).size());

        IntentFilter testIntentFilter = new IntentFilter();
        testIntentFilter.addAction(ManagedProfileActivity.ACTION);
        mDevicePolicyManager.addCrossProfileIntentFilter(ADMIN_RECEIVER_COMPONENT,
                testIntentFilter, DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);

        // We should still be resolving in the profile
        assertEquals(1, mPackageManager.queryIntentActivities(
                new Intent(ManagedProfileActivity.ACTION), /* flags = */ 0).size());
        getActivity().startActivity(ManagedProfileActivity.ACTION);
        assertTrue(getActivity().checkActivityStarted());
    }
}
