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

package android.cts.backup.restoreanyversionapp;

import static android.content.Context.MODE_PRIVATE;
import static android.support.test.InstrumentationRegistry.getTargetContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Device side routines to be invoked by the host side RestoreAnyVersionHostSideTest. These
 * are not designed to be called in any other way, as they rely on state set up by the host side
 * test.
 */
@RunWith(AndroidJUnit4.class)
public class RestoreAnyVersionTest {
    private static final String TAG = "BackupTestRestoreAnyVer";

    static final String TEST_PREFS_1 = "test-prefs-1";
    static final String INT_PREF = "int-pref";
    static final int DEFAULT_INT_VALUE = 0;

    private static final int OLD_VERSION = 100;
    private static final int NEW_VERSION = 200;

    private Context mContext;

    @Before
    public void setUp() {
        Log.i(TAG, "set up");
        mContext = getTargetContext();
    }

    @Test
    public void saveSharedPrefValue() throws Exception {
        saveAppVersionCodeToSharedPreference();
    }

    @Test
    public void checkAppVersionIsNew() throws Exception {
        PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(
                mContext.getPackageName(), 0);
        assertEquals(NEW_VERSION, packageInfo.versionCode);
    }

    @Test
    public void checkSharedPrefIsEmpty() throws Exception {
        int intValue = getAppSharedPreference();
        assertEquals(DEFAULT_INT_VALUE, intValue);
    }

    @Test
    public void checkSharedPrefIsNew() throws Exception {
        int intValue = getAppSharedPreference();
        assertEquals(NEW_VERSION, intValue);
    }

    @Test
    public void checkAppVersionIsOld() throws Exception {
        PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(
                mContext.getPackageName(), 0);
        assertEquals(OLD_VERSION, packageInfo.versionCode);
    }

    @Test
    public void checkSharedPrefIsOld() throws Exception {
        int intValue = getAppSharedPreference();
        assertEquals(OLD_VERSION, intValue);
    }

    private void saveAppVersionCodeToSharedPreference() throws NameNotFoundException {
        SharedPreferences prefs = mContext.getSharedPreferences(TEST_PREFS_1, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(INT_PREF,
                mContext.getPackageManager()
                        .getPackageInfo(mContext.getPackageName(), 0).versionCode);
        assertTrue("Error committing shared preference value", editor.commit());
    }

    private int getAppSharedPreference() throws InterruptedException {
        SharedPreferences prefs = mContext.getSharedPreferences(TEST_PREFS_1, MODE_PRIVATE);
        int intValue = prefs.getInt(INT_PREF, DEFAULT_INT_VALUE);

        Log.i(TAG, "Read the shared preference value: " + intValue);

        return intValue;
    }
}
