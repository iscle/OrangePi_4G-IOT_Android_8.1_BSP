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

package com.android.cts.encryptionapp;

import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.support.test.uiautomator.UiDevice;
import android.test.InstrumentationTestCase;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class EncryptionAppTest extends InstrumentationTestCase {
    private static final String TAG = "EncryptionAppTest";

    private static final long TIMEOUT = 10 * DateUtils.SECOND_IN_MILLIS;

    private static final String KEY_BOOT = "boot";

    private static final String TEST_PKG = "com.android.cts.encryptionapp";
    private static final String TEST_ACTION = "com.android.cts.encryptionapp.TEST";

    private static final String OTHER_PKG = "com.android.cts.splitapp";

    private Context mCe;
    private Context mDe;
    private PackageManager mPm;

    private UiDevice mDevice;
    private AwareActivity mActivity;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mCe = getInstrumentation().getContext();
        mDe = mCe.createDeviceProtectedStorageContext();
        mPm = mCe.getPackageManager();

        mDevice = UiDevice.getInstance(getInstrumentation());
        assertNotNull(mDevice);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (mActivity != null) {
            mActivity.finish();
        }
    }

    public void testSetUp() throws Exception {
        // Write both CE/DE data for ourselves
        assertTrue("CE file", getTestFile(mCe).createNewFile());
        assertTrue("DE file", getTestFile(mDe).createNewFile());

        doBootCountBefore();

        mActivity = launchActivity(getInstrumentation().getTargetContext().getPackageName(),
                AwareActivity.class, null);
        mDevice.waitForIdle();

        // Set a PIN for this user
        mDevice.executeShellCommand("settings put global require_password_to_decrypt 0");
        mDevice.executeShellCommand("locksettings set-disabled false");
        mDevice.executeShellCommand("locksettings set-pin 12345");
    }

    public void testTearDown() throws Exception {
        // Just in case, always try tearing down keyguard
        dismissKeyguard();

        mActivity = launchActivity(getInstrumentation().getTargetContext().getPackageName(),
                AwareActivity.class, null);
        mDevice.waitForIdle();

        // Clear PIN for this user
        mDevice.executeShellCommand("locksettings clear --old 12345");
        mDevice.executeShellCommand("locksettings set-disabled true");
        mDevice.executeShellCommand("settings delete global require_password_to_decrypt");
    }

    public void doBootCountBefore() throws Exception {
        final int thisCount = getBootCount();
        mDe.getSharedPreferences(KEY_BOOT, 0).edit().putInt(KEY_BOOT, thisCount).commit();
    }

    public void doBootCountAfter() throws Exception {
        final int lastCount = mDe.getSharedPreferences(KEY_BOOT, 0).getInt(KEY_BOOT, -1);
        final int thisCount = getBootCount();
        assertTrue("Current boot count " + thisCount + " not greater than last " + lastCount,
                thisCount > lastCount);
    }

    public void testVerifyUnlockedAndDismiss() throws Exception {
        doBootCountAfter();
        assertUnlocked();
        dismissKeyguard();
        assertUnlocked();
    }

    public void testVerifyLockedAndDismiss() throws Exception {
        doBootCountAfter();
        assertLocked();

        final CountDownLatch latch = new CountDownLatch(1);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        };
        mDe.registerReceiver(receiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));

        dismissKeyguard();

        // Dismiss keyguard should have kicked off immediate broadcast
        assertTrue("USER_UNLOCKED", latch.await(1, TimeUnit.MINUTES));

        // And we should now be fully unlocked; we run immediately like this to
        // avoid missing BOOT_COMPLETED due to instrumentation being torn down.
        assertUnlocked();
    }

    private void enterTestPin() throws Exception {
        // TODO: change the combination on my luggage
        mDevice.waitForIdle();
        mDevice.pressKeyCode(KeyEvent.KEYCODE_1);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_2);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_3);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_4);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_5);
        mDevice.waitForIdle();
        mDevice.pressEnter();
        mDevice.waitForIdle();
    }

    private void dismissKeyguard() throws Exception {
        mDevice.wakeUp();
        mDevice.waitForIdle();
        mDevice.pressMenu();
        mDevice.waitForIdle();
        enterTestPin();
    }

    public void assertLocked() throws Exception {
        awaitBroadcast(Intent.ACTION_LOCKED_BOOT_COMPLETED);

        assertFalse("CE exists", getTestFile(mCe).exists());
        assertTrue("DE exists", getTestFile(mDe).exists());

        assertFalse("isUserUnlocked", mCe.getSystemService(UserManager.class).isUserUnlocked());
        assertFalse("isUserUnlocked", mDe.getSystemService(UserManager.class).isUserUnlocked());

        assertTrue("AwareProvider", AwareProvider.sCreated);
        assertFalse("UnawareProvider", UnawareProvider.sCreated);

        assertNotNull("AwareProvider",
                mPm.resolveContentProvider("com.android.cts.encryptionapp.aware", 0));
        assertNull("UnawareProvider",
                mPm.resolveContentProvider("com.android.cts.encryptionapp.unaware", 0));

        assertGetAware(true, 0);
        assertGetAware(true, MATCH_DIRECT_BOOT_AWARE);
        assertGetAware(false, MATCH_DIRECT_BOOT_UNAWARE);
        assertGetAware(true, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);

        assertGetUnaware(false, 0);
        assertGetUnaware(false, MATCH_DIRECT_BOOT_AWARE);
        assertGetUnaware(true, MATCH_DIRECT_BOOT_UNAWARE);
        assertGetUnaware(true, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);

        assertQuery(1, 0);
        assertQuery(1, MATCH_DIRECT_BOOT_AWARE);
        assertQuery(1, MATCH_DIRECT_BOOT_UNAWARE);
        assertQuery(2, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);
    }

    public void assertUnlocked() throws Exception {
        awaitBroadcast(Intent.ACTION_LOCKED_BOOT_COMPLETED);
        awaitBroadcast(Intent.ACTION_BOOT_COMPLETED);

        assertTrue("CE exists", getTestFile(mCe).exists());
        assertTrue("DE exists", getTestFile(mDe).exists());

        assertTrue("isUserUnlocked", mCe.getSystemService(UserManager.class).isUserUnlocked());
        assertTrue("isUserUnlocked", mDe.getSystemService(UserManager.class).isUserUnlocked());

        assertTrue("AwareProvider", AwareProvider.sCreated);
        assertTrue("UnawareProvider", UnawareProvider.sCreated);

        assertNotNull("AwareProvider",
                mPm.resolveContentProvider("com.android.cts.encryptionapp.aware", 0));
        assertNotNull("UnawareProvider",
                mPm.resolveContentProvider("com.android.cts.encryptionapp.unaware", 0));

        assertGetAware(true, 0);
        assertGetAware(true, MATCH_DIRECT_BOOT_AWARE);
        assertGetAware(false, MATCH_DIRECT_BOOT_UNAWARE);
        assertGetAware(true, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);

        assertGetUnaware(true, 0);
        assertGetUnaware(false, MATCH_DIRECT_BOOT_AWARE);
        assertGetUnaware(true, MATCH_DIRECT_BOOT_UNAWARE);
        assertGetUnaware(true, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);

        assertQuery(2, 0);
        assertQuery(1, MATCH_DIRECT_BOOT_AWARE);
        assertQuery(1, MATCH_DIRECT_BOOT_UNAWARE);
        assertQuery(2, MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);
    }

    private void assertQuery(int count, int flags) throws Exception {
        final Intent intent = new Intent(TEST_ACTION);
        assertEquals("activity", count, mPm.queryIntentActivities(intent, flags).size());
        assertEquals("service", count, mPm.queryIntentServices(intent, flags).size());
        assertEquals("provider", count, mPm.queryIntentContentProviders(intent, flags).size());
        assertEquals("receiver", count, mPm.queryBroadcastReceivers(intent, flags).size());
    }

    private void assertGetUnaware(boolean visible, int flags) throws Exception {
        assertGet(visible, false, flags);
    }

    private void assertGetAware(boolean visible, int flags) throws Exception {
        assertGet(visible, true, flags);
    }

    private ComponentName buildName(String prefix, String type) {
        return new ComponentName(TEST_PKG, TEST_PKG + "." + prefix + type);
    }

    private void assertGet(boolean visible, boolean aware, int flags) throws Exception {
        final String prefix = aware ? "Aware" : "Unaware";

        ComponentName name;
        ComponentInfo info;

        name = buildName(prefix, "Activity");
        try {
            info = mPm.getActivityInfo(name, flags);
            assertTrue(name + " visible", visible);
            assertEquals(name + " directBootAware", aware, info.directBootAware);
        } catch (NameNotFoundException e) {
            assertFalse(name + " visible", visible);
        }

        name = buildName(prefix, "Service");
        try {
            info = mPm.getServiceInfo(name, flags);
            assertTrue(name + " visible", visible);
            assertEquals(name + " directBootAware", aware, info.directBootAware);
        } catch (NameNotFoundException e) {
            assertFalse(name + " visible", visible);
        }

        name = buildName(prefix, "Provider");
        try {
            info = mPm.getProviderInfo(name, flags);
            assertTrue(name + " visible", visible);
            assertEquals(name + " directBootAware", aware, info.directBootAware);
        } catch (NameNotFoundException e) {
            assertFalse(name + " visible", visible);
        }

        name = buildName(prefix, "Receiver");
        try {
            info = mPm.getReceiverInfo(name, flags);
            assertTrue(name + " visible", visible);
            assertEquals(name + " directBootAware", aware, info.directBootAware);
        } catch (NameNotFoundException e) {
            assertFalse(name + " visible", visible);
        }
    }

    private File getTestFile(Context context) {
        return new File(context.getFilesDir(), "test");
    }

    private int getBootCount() throws Exception {
        return Settings.Global.getInt(mDe.getContentResolver(), Settings.Global.BOOT_COUNT);
    }

    private void awaitBroadcast(String action) throws Exception {
        final Context otherContext = mDe.createPackageContext(OTHER_PKG, 0)
                .createDeviceProtectedStorageContext();
        final File probe = new File(otherContext.getFilesDir(),
                getBootCount() + "." + action);
        for (int i = 0; i < 150; i++) {
            Log.d(TAG, "Waiting for " + probe + "...");
            if (probe.exists()) {
                return;
            }
            SystemClock.sleep(1000);
        }
        throw new AssertionError("Failed to find " + probe);
    }
}
