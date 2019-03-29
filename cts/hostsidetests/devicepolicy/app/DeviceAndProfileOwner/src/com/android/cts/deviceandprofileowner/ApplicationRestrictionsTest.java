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
package com.android.cts.deviceandprofileowner;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.UserManager;
import android.test.MoreAsserts;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Functionality tests for application restrictions APIs.
 *
 * <p>APIs are executed locally to assert that what you set can later be retrieved via the getter.
 * It also fires up an external activity to observe an application's view of its restrictions.
 *
 * <p>Finally, it checks that the {@link Intent#ACTION_APPLICATION_RESTRICTIONS_CHANGED} broadcast
 * is sent whenever app restrictions are modified for a given package.
 */
public class ApplicationRestrictionsTest extends BaseDeviceAdminTest {

    private static final String APP_RESTRICTIONS_TARGET_PKG =
            "com.android.cts.apprestrictions.targetapp";
    private static final String APP_RESTRICTIONS_ACTIVITY_NAME =
            APP_RESTRICTIONS_TARGET_PKG + ".ApplicationRestrictionsActivity";
    private static final String ACTION_RESTRICTIONS_VALUE =
            "com.android.cts.apprestrictions.targetapp.RESTRICTIONS_VALUE";

    private static final String OTHER_PACKAGE = APP_RESTRICTIONS_TARGET_PKG + "dummy";

    private static final String[] TEST_STRINGS = new String[] {
            "<bad/>",
            ">worse!\"£$%^&*()'<",
            "<JSON>\"{ \\\"One\\\": { \\\"OneOne\\\": \\\"11\\\", \\\""
                    + "OneTwo\\\": \\\"12\\\" }, \\\"Two\\\": \\\"2\\\" } <JSON/>\""
    };

    private static final Bundle BUNDLE_0 = createBundle0();
    private static final Bundle BUNDLE_1 = createBundle1();

    private static final long RESTRICTIONS_TIMEOUT_SECONDS = 10;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_RESTRICTIONS_VALUE.equals(action)) {
                mReceivedRestrictions = intent.getBundleExtra("value");
                mOnRestrictionsReceivedFromAppSemaphore.release();
            } else if (Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED.equals(action)) {
                mOnAppRestrictionsChangedSemahore.release();
            }
        }
    };

    private final Semaphore mOnAppRestrictionsChangedSemahore = new Semaphore(0);
    private final Semaphore mOnRestrictionsReceivedFromAppSemaphore = new Semaphore(0);
    private Bundle mReceivedRestrictions;
    private UserManager mUserManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_RESTRICTIONS_VALUE);
        filter.addAction(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED);
        mContext.registerReceiver(mReceiver, filter);
    }

    @Override
    protected void tearDown() throws Exception {
        mContext.unregisterReceiver(mReceiver);

        mDevicePolicyManager.setApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, APP_RESTRICTIONS_TARGET_PKG, new Bundle());
        mDevicePolicyManager.setApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, OTHER_PACKAGE, new Bundle());
        mDevicePolicyManager.setApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, mContext.getPackageName(), new Bundle());

        super.tearDown();
    }

    public void testNullComponentThrowsException() {
        try {
            mDevicePolicyManager.setApplicationRestrictions(
                    null, APP_RESTRICTIONS_TARGET_PKG, null);
            fail("Expected SecurityException not thrown");
        } catch (SecurityException expected) {
            MoreAsserts.assertContainsRegex(
                    "Caller with uid \\d+ is not a delegate of scope delegation-app-restrictions.",
                    expected.getMessage());
        }
        try {
            mDevicePolicyManager.getApplicationRestrictions(null, APP_RESTRICTIONS_TARGET_PKG);
            fail("Expected SecurityException not thrown");
        } catch (SecurityException expected) {
            MoreAsserts.assertContainsRegex(
                    "Caller with uid \\d+ is not a delegate of scope delegation-app-restrictions.",
                    expected.getMessage());
        }
    }

    public void testSetApplicationRestrictions() {
        // Test setting restrictions
        mDevicePolicyManager.setApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, APP_RESTRICTIONS_TARGET_PKG, BUNDLE_0);
        mDevicePolicyManager.setApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, OTHER_PACKAGE, BUNDLE_1);

        // Retrieve restrictions locally and make sure they are what we put in.
        assertBundle0(mDevicePolicyManager.getApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, APP_RESTRICTIONS_TARGET_PKG));
        assertBundle1(mDevicePolicyManager.getApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, OTHER_PACKAGE));

        // Check that the target app can retrieve the same restrictions.
        assertBundle0(waitForRestrictionsValueFromTestActivity());

        // Test overwriting
        mDevicePolicyManager.setApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, APP_RESTRICTIONS_TARGET_PKG, BUNDLE_1);
        assertBundle1(mDevicePolicyManager.getApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, APP_RESTRICTIONS_TARGET_PKG));
        assertBundle1(waitForRestrictionsValueFromTestActivity());

        mDevicePolicyManager.setApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, APP_RESTRICTIONS_TARGET_PKG, new Bundle());
        assertTrue(mDevicePolicyManager.getApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, APP_RESTRICTIONS_TARGET_PKG).isEmpty());
        assertTrue(waitForRestrictionsValueFromTestActivity().isEmpty());

        mDevicePolicyManager.setApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, OTHER_PACKAGE, null);
        assertTrue(mDevicePolicyManager.getApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, OTHER_PACKAGE).isEmpty());
    }

    public void testCanRetrieveOwnRestrictionsViaUserManager() {
        final String packageName = mContext.getPackageName();

        mDevicePolicyManager.setApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, packageName, BUNDLE_0);
        assertBundle0(mDevicePolicyManager.getApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, packageName));

        // Check that we got the restrictions changed callback.
        assertBundle0(waitForRestrictionsChangedBroadcast());

        mDevicePolicyManager.setApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, packageName, BUNDLE_1);
        assertBundle1(mDevicePolicyManager.getApplicationRestrictions(
                ADMIN_RECEIVER_COMPONENT, packageName));
        assertBundle1(waitForRestrictionsChangedBroadcast());
    }

    public void testCannotRetrieveOtherPackageRestrictionsViaUserManager() {
        try {
            mUserManager.getApplicationRestrictions(OTHER_PACKAGE);
            fail("Expected SecurityException not thrown");
        } catch (SecurityException expected) {
        }
    }

    public void testSetApplicationRestrictionsManagingPackage() throws NameNotFoundException {
        final String previousValue = mDevicePolicyManager.getApplicationRestrictionsManagingPackage(
                ADMIN_RECEIVER_COMPONENT);
        try {
            mDevicePolicyManager.setApplicationRestrictionsManagingPackage(
                    ADMIN_RECEIVER_COMPONENT, APP_RESTRICTIONS_TARGET_PKG);
            assertEquals(APP_RESTRICTIONS_TARGET_PKG,
                    mDevicePolicyManager.getApplicationRestrictionsManagingPackage(
                            ADMIN_RECEIVER_COMPONENT));
            mDevicePolicyManager.setApplicationRestrictionsManagingPackage(
                    ADMIN_RECEIVER_COMPONENT, null);
            assertNull(mDevicePolicyManager.getApplicationRestrictionsManagingPackage(
                    ADMIN_RECEIVER_COMPONENT));
        } finally {
            mDevicePolicyManager.setApplicationRestrictionsManagingPackage(
                    ADMIN_RECEIVER_COMPONENT, previousValue);
            assertEquals(previousValue,
                    mDevicePolicyManager.getApplicationRestrictionsManagingPackage(
                            ADMIN_RECEIVER_COMPONENT));
        }
    }

    public void testSetApplicationRestrictionsManagingPackageForNotInstalledPackage()
            throws NameNotFoundException {
        try {
            mDevicePolicyManager.setApplicationRestrictionsManagingPackage(ADMIN_RECEIVER_COMPONENT,
                    OTHER_PACKAGE);
            fail("Not throwing exception for not installed package name");
        } catch (NameNotFoundException expected) {
            MoreAsserts.assertContainsRegex(OTHER_PACKAGE, expected.getMessage());
        } finally {
            mDevicePolicyManager.setApplicationRestrictionsManagingPackage(ADMIN_RECEIVER_COMPONENT,
                    null);
            assertNull(mDevicePolicyManager.getApplicationRestrictionsManagingPackage(
                    ADMIN_RECEIVER_COMPONENT));
        }
    }

    // Should be consistent with assertBundle0
    private static Bundle createBundle0() {
        Bundle result = new Bundle();
        // Tests for 6 allowed types: Integer, Boolean, String, String[], Bundle and Parcelable[]
        // Also test for string escaping handling
        result.putBoolean("boolean_0", false);
        result.putBoolean("boolean_1", true);
        result.putInt("integer", 0x7fffffff);
        // If a null is stored, "" will be read back
        result.putString("empty", "");
        result.putString("string", "text");
        result.putStringArray("string[]", TEST_STRINGS);

        // Adding a bundle, which contain 2 nested restrictions - bundle_string and bundle_int
        Bundle bundle = new Bundle();
        bundle.putString("bundle_string", "bundle_string");
        bundle.putInt("bundle_int", 1);
        result.putBundle("bundle", bundle);

        // Adding an array of 2 bundles
        Bundle[] bundleArray = new Bundle[2];
        bundleArray[0] = new Bundle();
        bundleArray[0].putString("bundle_array_string", "bundle_array_string");
        // Put bundle inside bundle
        bundleArray[0].putBundle("bundle_array_bundle", bundle);
        bundleArray[1] = new Bundle();
        bundleArray[1].putString("bundle_array_string2", "bundle_array_string2");
        result.putParcelableArray("bundle_array", bundleArray);
        return result;
    }

    // Should be consistent with createBundle0
    private void assertBundle0(Bundle bundle) {
        assertEquals(8, bundle.size());
        assertEquals(false, bundle.getBoolean("boolean_0"));
        assertEquals(true, bundle.getBoolean("boolean_1"));
        assertEquals(0x7fffffff, bundle.getInt("integer"));
        assertEquals("", bundle.getString("empty"));
        assertEquals("text", bundle.getString("string"));

        String[] strings = bundle.getStringArray("string[]");
        assertTrue(strings != null && strings.length == TEST_STRINGS.length);
        for (int i = 0; i < strings.length; i++) {
            assertEquals(strings[i], TEST_STRINGS[i]);
        }

        Bundle childBundle = bundle.getBundle("bundle");
        assertEquals("bundle_string", childBundle.getString("bundle_string"));
        assertEquals(1, childBundle.getInt("bundle_int"));

        Parcelable[] bundleArray = bundle.getParcelableArray("bundle_array");
        assertEquals(2, bundleArray.length);
        // Verifying bundle_array[0]
        Bundle bundle1 = (Bundle) bundleArray[0];
        assertEquals("bundle_array_string", bundle1.getString("bundle_array_string"));
        Bundle bundle1ChildBundle = bundle1.getBundle("bundle_array_bundle");
        assertNotNull(bundle1ChildBundle);
        assertEquals("bundle_string", bundle1ChildBundle.getString("bundle_string"));
        assertEquals(1, bundle1ChildBundle.getInt("bundle_int"));
        // Verifying bundle_array[1]
        Bundle bundle2 = (Bundle) bundleArray[1];
        assertEquals("bundle_array_string2", bundle2.getString("bundle_array_string2"));
    }

    // Should be consistent with assertBundle1
    private static Bundle createBundle1() {
        Bundle result = new Bundle();
        result.putInt("dummy", 1);
        return result;
    }

    // Should be consistent with createBundle1
    private void assertBundle1(Bundle bundle) {
        assertEquals(1, bundle.size());
        assertEquals(1, bundle.getInt("dummy"));
    }

    private void startTestActivity() {
        mContext.startActivity(new Intent()
                .setComponent(new ComponentName(
                        APP_RESTRICTIONS_TARGET_PKG, APP_RESTRICTIONS_ACTIVITY_NAME))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private Bundle waitForRestrictionsValueFromTestActivity() {
        startTestActivity();

        try {
            assertTrue(mOnRestrictionsReceivedFromAppSemaphore.tryAcquire(
                    RESTRICTIONS_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("waitForRestrictionsValueFromTestActivity() interrupted");
        }

        return mReceivedRestrictions;
    }

    private Bundle waitForRestrictionsChangedBroadcast() {
        try {
            assertTrue(mOnAppRestrictionsChangedSemahore.tryAcquire(
                    RESTRICTIONS_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("waitForRestrictionsChangedBroadcast() interrupted");
        }

        return mUserManager.getApplicationRestrictions(mContext.getPackageName());
    }
}
