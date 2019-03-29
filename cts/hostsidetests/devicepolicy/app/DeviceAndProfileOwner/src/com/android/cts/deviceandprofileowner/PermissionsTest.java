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
package com.android.cts.deviceandprofileowner;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiWatcher;
import android.support.test.uiautomator.Until;
import android.test.suitebuilder.annotation.Suppress;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test Runtime Permissions APIs in DevicePolicyManager.
 */
public class PermissionsTest extends BaseDeviceAdminTest {
    private static final String TAG = "PermissionsTest";

    private static final String PERMISSION_APP_PACKAGE_NAME
            = "com.android.cts.permissionapp";
    private static final String SIMPLE_PRE_M_APP_PACKAGE_NAME =
            "com.android.cts.launcherapps.simplepremapp";
    private static final String PERMISSION_NAME = "android.permission.READ_CONTACTS";

    private static final String PERMISSIONS_ACTIVITY_NAME
            = PERMISSION_APP_PACKAGE_NAME + ".PermissionActivity";
    private static final String ACTION_CHECK_HAS_PERMISSION
            = "com.android.cts.permission.action.CHECK_HAS_PERMISSION";
    private static final String ACTION_REQUEST_PERMISSION
            = "com.android.cts.permission.action.REQUEST_PERMISSION";
    private static final String ACTION_PERMISSION_RESULT
            = "com.android.cts.permission.action.PERMISSION_RESULT";
    private static final String EXTRA_PERMISSION
            = "com.android.cts.permission.extra.PERMISSION";
    private static final String EXTRA_GRANT_STATE
            = "com.android.cts.permission.extra.GRANT_STATE";
    private static final int PERMISSION_ERROR = -2;
    private static final BySelector CRASH_POPUP_BUTTON_SELECTOR = By
            .clazz(android.widget.Button.class.getName())
            .text("OK")
            .pkg("android");
    private static final BySelector CRASH_POPUP_TEXT_SELECTOR = By
            .clazz(android.widget.TextView.class.getName())
            .pkg("android");
    private static final String CRASH_WATCHER_ID = "CRASH";

    private PermissionBroadcastReceiver mReceiver;
    private PackageManager mPackageManager;
    private UiDevice mDevice;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mReceiver = new PermissionBroadcastReceiver();
        mContext.registerReceiver(mReceiver, new IntentFilter(ACTION_PERMISSION_RESULT));
        mPackageManager = mContext.getPackageManager();
        mDevice = UiDevice.getInstance(getInstrumentation());
    }

    @Override
    protected void tearDown() throws Exception {
        mContext.unregisterReceiver(mReceiver);
        mDevice.removeWatcher(CRASH_WATCHER_ID);
        super.tearDown();
    }

    public void testPermissionGrantState() throws Exception {
        assertSetPermissionGrantState(DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED);
        assertPermissionGrantState(PackageManager.PERMISSION_DENIED);
        assertPermissionRequest(PackageManager.PERMISSION_DENIED);

        assertSetPermissionGrantState(DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        // Should stay denied
        assertPermissionGrantState(PackageManager.PERMISSION_DENIED);

        assertSetPermissionGrantState(DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
        assertPermissionGrantState(PackageManager.PERMISSION_GRANTED);
        assertPermissionRequest(PackageManager.PERMISSION_GRANTED);

        assertSetPermissionGrantState(DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        // Should stay granted
        assertPermissionGrantState(PackageManager.PERMISSION_GRANTED);
    }

    public void testPermissionPolicy() throws Exception {
        // reset permission to denied and unlocked
        assertSetPermissionGrantState(DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED);
        assertSetPermissionGrantState(DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);

        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY);
        assertPermissionRequest(PackageManager.PERMISSION_DENIED);
        // permission should be locked, so changing the policy should not change the grant state
        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_PROMPT);
        assertPermissionRequest(PackageManager.PERMISSION_DENIED);
        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT);
        assertPermissionRequest(PackageManager.PERMISSION_DENIED);

        // reset permission to denied and unlocked
        assertSetPermissionGrantState(DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);

        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT);
        assertPermissionRequest(PackageManager.PERMISSION_GRANTED);
        // permission should be locked, so changing the policy should not change the grant state
        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_PROMPT);
        assertPermissionRequest(PackageManager.PERMISSION_GRANTED);
        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY);
        assertPermissionRequest(PackageManager.PERMISSION_GRANTED);

        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_PROMPT);
    }

    public void testPermissionMixedPolicies() throws Exception {
        assertSetPermissionGrantState(DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED);
        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT);
        assertPermissionRequest(PackageManager.PERMISSION_DENIED);

        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY);
        assertPermissionRequest(PackageManager.PERMISSION_DENIED);

        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_PROMPT);
        assertPermissionRequest(PackageManager.PERMISSION_DENIED);

        assertSetPermissionGrantState(DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT);
        assertPermissionRequest(PackageManager.PERMISSION_GRANTED);

        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY);
        assertPermissionRequest(PackageManager.PERMISSION_GRANTED);

        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_PROMPT);
        assertPermissionRequest(PackageManager.PERMISSION_GRANTED);
    }

    @Suppress // Flakey.
    public void testPermissionPrompts() throws Exception {
        // register a crash watcher
        mDevice.registerWatcher(CRASH_WATCHER_ID, new UiWatcher() {
            @Override
            public boolean checkForCondition() {
                UiObject2 button = mDevice.findObject(CRASH_POPUP_BUTTON_SELECTOR);
                if (button != null) {
                    UiObject2 text = mDevice.findObject(CRASH_POPUP_TEXT_SELECTOR);
                    Log.d(TAG, "Removing an error dialog: " + text != null ? text.getText() : null);
                    button.click();
                    return true;
                }
                return false;
            }
        });
        mDevice.runWatchers();

        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_PROMPT);
        assertPermissionRequest(PackageManager.PERMISSION_DENIED, "permission_deny_button");
        assertPermissionRequest(PackageManager.PERMISSION_GRANTED, "permission_allow_button");
    }

    public void testPermissionUpdate_setDeniedState() throws Exception {
        assertEquals(mDevicePolicyManager.getPermissionGrantState(ADMIN_RECEIVER_COMPONENT,
                PERMISSION_APP_PACKAGE_NAME, PERMISSION_NAME),
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        assertSetPermissionGrantState(DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED);
    }

    public void testPermissionUpdate_setAutoDeniedPolicy() throws Exception {
        assertEquals(mDevicePolicyManager.getPermissionGrantState(ADMIN_RECEIVER_COMPONENT,
                PERMISSION_APP_PACKAGE_NAME, PERMISSION_NAME),
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY);
        assertPermissionRequest(PackageManager.PERMISSION_DENIED);
    }

    public void testPermissionUpdate_checkDenied() throws Exception {
        assertPermissionRequest(PackageManager.PERMISSION_DENIED);
        assertPermissionGrantState(PackageManager.PERMISSION_DENIED);
    }

    public void testPermissionUpdate_setGrantedState() throws Exception {
        assertEquals(mDevicePolicyManager.getPermissionGrantState(ADMIN_RECEIVER_COMPONENT,
                PERMISSION_APP_PACKAGE_NAME, PERMISSION_NAME),
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        assertSetPermissionGrantState(DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
    }

    public void testPermissionUpdate_setAutoGrantedPolicy() throws Exception {
        assertEquals(mDevicePolicyManager.getPermissionGrantState(ADMIN_RECEIVER_COMPONENT,
                PERMISSION_APP_PACKAGE_NAME, PERMISSION_NAME),
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        assertSetPermissionPolicy(DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT);
        assertPermissionRequest(PackageManager.PERMISSION_GRANTED);
    }

    public void testPermissionUpdate_checkGranted() throws Exception {
        assertPermissionRequest(PackageManager.PERMISSION_GRANTED);
        assertPermissionGrantState(PackageManager.PERMISSION_GRANTED);
    }

    public void testPermissionGrantStatePreMApp() throws Exception {
        // These tests are to make sure that pre-M apps are not granted runtime permissions
        // by a profile owner
        assertSetPermissionGrantStatePreMApp(DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED);
        assertSetPermissionGrantStatePreMApp(DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
    }

    private void assertPermissionRequest(int expected) throws Exception {
        assertPermissionRequest(expected, null);
    }

    private void assertPermissionRequest(int expected, String buttonResource) throws Exception {
        Intent launchIntent = new Intent();
        launchIntent.setComponent(new ComponentName(PERMISSION_APP_PACKAGE_NAME,
                PERMISSIONS_ACTIVITY_NAME));
        launchIntent.putExtra(EXTRA_PERMISSION, PERMISSION_NAME);
        launchIntent.setAction(ACTION_REQUEST_PERMISSION);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        mContext.startActivity(launchIntent);
        pressPermissionPromptButton(buttonResource);
        assertEquals(expected, mReceiver.waitForBroadcast());
        assertEquals(expected, mPackageManager.checkPermission(PERMISSION_NAME,
                PERMISSION_APP_PACKAGE_NAME));
    }

    private void assertPermissionGrantState(int expected) throws Exception {
        assertEquals(expected, mPackageManager.checkPermission(PERMISSION_NAME,
                PERMISSION_APP_PACKAGE_NAME));
        Intent launchIntent = new Intent();
        launchIntent.setComponent(new ComponentName(PERMISSION_APP_PACKAGE_NAME,
                PERMISSIONS_ACTIVITY_NAME));
        launchIntent.putExtra(EXTRA_PERMISSION, PERMISSION_NAME);
        launchIntent.setAction(ACTION_CHECK_HAS_PERMISSION);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        mContext.startActivity(launchIntent);
        assertEquals(expected, mReceiver.waitForBroadcast());
    }

    private void assertSetPermissionPolicy(int value) throws Exception {
        mDevicePolicyManager.setPermissionPolicy(ADMIN_RECEIVER_COMPONENT,
                value);
        assertEquals(mDevicePolicyManager.getPermissionPolicy(ADMIN_RECEIVER_COMPONENT),
                value);
    }

    private void assertSetPermissionGrantState(int value) throws Exception {
        mDevicePolicyManager.setPermissionGrantState(ADMIN_RECEIVER_COMPONENT,
                PERMISSION_APP_PACKAGE_NAME, PERMISSION_NAME,
                value);
        assertEquals(mDevicePolicyManager.getPermissionGrantState(ADMIN_RECEIVER_COMPONENT,
                PERMISSION_APP_PACKAGE_NAME, PERMISSION_NAME),
                value);
    }

    private void assertSetPermissionGrantStatePreMApp(int value) throws Exception {
        assertFalse(mDevicePolicyManager.setPermissionGrantState(ADMIN_RECEIVER_COMPONENT,
                SIMPLE_PRE_M_APP_PACKAGE_NAME, PERMISSION_NAME,
                value));
        assertEquals(mDevicePolicyManager.getPermissionGrantState(ADMIN_RECEIVER_COMPONENT,
                SIMPLE_PRE_M_APP_PACKAGE_NAME, PERMISSION_NAME),
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        // Install time permissions should always be granted
        assertEquals(mPackageManager.checkPermission(PERMISSION_NAME,
                SIMPLE_PRE_M_APP_PACKAGE_NAME),
                PackageManager.PERMISSION_GRANTED);
    }

    private void pressPermissionPromptButton(String resName) throws Exception {
        if (resName == null) {
            return;
        }

        BySelector selector = By
                .clazz(android.widget.Button.class.getName())
                .res("com.android.packageinstaller", resName);
        mDevice.wait(Until.hasObject(selector), 5000);
        UiObject2 button = mDevice.findObject(selector);
        assertNotNull("Couldn't find button with resource id: " + resName, button);
        button.click();
    }

    private class PermissionBroadcastReceiver extends BroadcastReceiver {
        private BlockingQueue<Integer> mQueue = new ArrayBlockingQueue<Integer> (1);

        @Override
        public void onReceive(Context context, Intent intent) {
            Integer result = new Integer(intent.getIntExtra(EXTRA_GRANT_STATE, PERMISSION_ERROR));
            Log.d(TAG, "Grant state received " + result);
            assertTrue(mQueue.add(result));
        }

        public int waitForBroadcast() throws Exception {
            Integer result = mQueue.poll(30, TimeUnit.SECONDS);
            mQueue.clear();
            assertNotNull(result);
            Log.d(TAG, "Grant state retrieved " + result.intValue());
            return result.intValue();
        }
    }
}
