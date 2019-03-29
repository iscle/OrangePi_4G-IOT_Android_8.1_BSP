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

package android.cts.backup;

import static junit.framework.Assert.assertNull;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

/**
 * Test for checking that key/value backup and restore works correctly.
 * It interacts with the app that saves values in different shared preferences and files.
 * The app uses BackupAgentHelper to do key/value backup of those values.
 *
 * NB: The tests use "bmgr backupnow" for backup, which works on N+ devices.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class KeyValueBackupRestoreHostSideTest extends BaseBackupHostSideTest {

    /** The name of the package of the app under test */
    private static final String KEY_VALUE_RESTORE_APP_PACKAGE =
            "android.cts.backup.keyvaluerestoreapp";

    /** The name of the package with the activity testing shared preference restore. */
    private static final String SHARED_PREFERENCES_RESTORE_APP_PACKAGE =
            "android.cts.backup.sharedprefrestoreapp";

    /** The name of the device side test class */
    private static final String KEY_VALUE_RESTORE_DEVICE_TEST_NAME =
            KEY_VALUE_RESTORE_APP_PACKAGE + ".KeyValueBackupRestoreTest";

    /** The name of the apk of the app under test */
    private static final String KEY_VALUE_RESTORE_APP_APK = "CtsKeyValueBackupRestoreApp.apk";

    /** The name of the apk with the activity testing shared preference restore. */
    private static final String SHARED_PREFERENCES_RESTORE_APP_APK =
            "CtsSharedPreferencesRestoreApp.apk";


    @Before
    public void setUp() throws Exception {
        super.setUp();
        installPackage(KEY_VALUE_RESTORE_APP_APK);
        clearPackageData(KEY_VALUE_RESTORE_APP_PACKAGE);

        installPackage(SHARED_PREFERENCES_RESTORE_APP_APK);
        clearPackageData(SHARED_PREFERENCES_RESTORE_APP_APK);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        // Clear backup data and uninstall the package (in that order!)
        clearBackupDataInLocalTransport(KEY_VALUE_RESTORE_APP_PACKAGE);
        assertNull(uninstallPackage(KEY_VALUE_RESTORE_APP_PACKAGE));

        clearBackupDataInLocalTransport(SHARED_PREFERENCES_RESTORE_APP_PACKAGE);
        assertNull(uninstallPackage(SHARED_PREFERENCES_RESTORE_APP_PACKAGE));
    }

    /**
     * Test that verifies key/value backup and restore.
     *
     * The flow of the test:
     * 1. Check that app has no saved data
     * 2. App saves the predefined values to shared preferences and files.
     * 3. Backup the app's data
     * 4. Uninstall the app
     * 5. Install the app back
     * 6. Check that all the shared preferences and files were restored.
     */
    @Test
    public void testKeyValueBackupAndRestore() throws Exception {
        if (!mIsBackupSupported) {
            CLog.i("android.software.backup feature is not supported on this device");
            return;
        }

        checkDeviceTest("checkSharedPrefIsEmpty");

        checkDeviceTest("saveSharedPreferencesAndNotifyBackupManager");

        backupNowAndAssertSuccess(KEY_VALUE_RESTORE_APP_PACKAGE);

        assertNull(uninstallPackage(KEY_VALUE_RESTORE_APP_PACKAGE));

        installPackage(KEY_VALUE_RESTORE_APP_APK);

        // Shared preference should be restored
        checkDeviceTest("checkSharedPreferencesAreRestored");
    }

    /**
     * Test that verifies SharedPreference restore behavior.
     *
     * The tests uses device-side test routines and a test activity in *another* package, since
     * the app containing the instrumented tests is killed after each test.
     *
     * Test logic:
     *   1. The activity is launched; it creates a new SharedPreferences instance and writes
     *       a known value to the INT_PREF element's via that instance.  The instance is
     *       kept live.
     *   2. The app is backed up, storing this known value in the backup dataset.
     *   3. Next, the activity is instructed to write a different value to the INT_PREF
     *       shared preferences element.  At this point, the app's current on-disk state
     *       and the live shared preferences instance are in agreement, holding a value
     *       different from that in the backup.
     *   4. The runner triggers a restore for this app.  This will rewrite the shared prefs
     *       file itself with the backed-up content (i.e. different from what was just
     *       committed from this activity).
     *   5. Finally, the runner instructs the activity to compare the value of its existing
     *       shared prefs instance's INT_PREF element with what was previously written.
     *       The test passes if these differ, i.e. if the live shared prefs instance picked
     *       up the newly-restored data.
     */
    @Test
    public void testSharedPreferencesRestore() throws Exception {
        checkDeviceTest("launchSharedPrefActivity");

        backupNowAndAssertSuccess(SHARED_PREFERENCES_RESTORE_APP_PACKAGE);

        checkDeviceTest("updateSharedPrefActivity");

        restoreAndAssertSuccess(SHARED_PREFERENCES_RESTORE_APP_PACKAGE);

        checkDeviceTest("checkSharedPrefActivity");
    }

    private void checkDeviceTest(String methodName)
            throws DeviceNotAvailableException {
        super.checkDeviceTest(KEY_VALUE_RESTORE_APP_PACKAGE, KEY_VALUE_RESTORE_DEVICE_TEST_NAME,
                methodName);
    }
}
