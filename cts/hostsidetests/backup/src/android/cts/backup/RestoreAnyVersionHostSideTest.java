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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.device.DeviceNotAvailableException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

/**
 * Test checking that restoreAnyVersion manifest flag is respected by backup manager.
 *
 * Invokes device side tests provided by
 * android.cts.backup.restoreanyversionapp.RestoreAnyVersionTest.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class RestoreAnyVersionHostSideTest extends BaseBackupHostSideTest {

    /** The name of the package of the app under test */
    private static final String RESTORE_ANY_VERSION_APP_PACKAGE =
            "android.cts.backup.restoreanyversionapp";

    /** The name of the device side test class */
    private static final String RESTORE_ANY_VERSION_DEVICE_TEST_NAME =
            RESTORE_ANY_VERSION_APP_PACKAGE + ".RestoreAnyVersionTest";

    /** The name of the APK of the app that has restoreAnyVersion=true in the manifest */
    private static final String RESTORE_ANY_VERSION_APP_APK = "CtsBackupRestoreAnyVersionApp.apk";

    /** The name of the APK of the app that has a higher version code */
    private static final String RESTORE_ANY_VERSION_UPDATE_APK =
            "CtsBackupRestoreAnyVersionAppUpdate.apk";

    /** The name of the APK of the app that has restoreAnyVersion=false in the manifest */
    private static final String NO_RESTORE_ANY_VERSION_APK =
            "CtsBackupRestoreAnyVersionNoRestoreApp.apk";

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        // Clear backup data and uninstall the package (in that order!)
        clearBackupDataInLocalTransport(RESTORE_ANY_VERSION_APP_PACKAGE);
        assertNull(uninstallPackage(RESTORE_ANY_VERSION_APP_PACKAGE));
    }

    /**
     * Tests that the app that has restoreAnyVersion=false will not get the restored data from a
     * newer version of that app at install time
     */
    @Test
    public void testRestoreAnyVersion_False() throws Exception {
        installNewVersionApp();

        saveSharedPreferenceValue();
        checkRestoreAnyVersionDeviceTest("checkSharedPrefIsNew");

        backupNowAndAssertSuccess(RESTORE_ANY_VERSION_APP_PACKAGE);

        assertNull(uninstallPackage(RESTORE_ANY_VERSION_APP_PACKAGE));

        installNoRestoreAnyVersionApp();

        // Shared preference shouldn't be restored
        checkRestoreAnyVersionDeviceTest("checkSharedPrefIsEmpty");
    }

    /**
     * Tests that the app that has restoreAnyVersion=true will get the restored data from a
     * newer version of that app at install time
     */
    @Test
    public void testRestoreAnyVersion_True() throws Exception {
        installNewVersionApp();

        saveSharedPreferenceValue();
        checkRestoreAnyVersionDeviceTest("checkSharedPrefIsNew");

        backupNowAndAssertSuccess(RESTORE_ANY_VERSION_APP_PACKAGE);

        assertNull(uninstallPackage(RESTORE_ANY_VERSION_APP_PACKAGE));

        installRestoreAnyVersionApp();

        // Shared preference should be restored
        checkRestoreAnyVersionDeviceTest("checkSharedPrefIsNew");
    }

    /**
     * Tests that the app that has restoreAnyVersion=false will still get the restored data from an
     * older version of that app at install time
     */
    @Test
    public void testRestoreAnyVersion_OldBackupToNewApp() throws Exception {
        installNoRestoreAnyVersionApp();

        saveSharedPreferenceValue();
        checkRestoreAnyVersionDeviceTest("checkSharedPrefIsOld");

        backupNowAndAssertSuccess(RESTORE_ANY_VERSION_APP_PACKAGE);

        assertNull(uninstallPackage(RESTORE_ANY_VERSION_APP_PACKAGE));

        installNewVersionApp();

        checkRestoreAnyVersionDeviceTest("checkSharedPrefIsOld");
    }

    private void saveSharedPreferenceValue () throws DeviceNotAvailableException {
        checkRestoreAnyVersionDeviceTest("checkSharedPrefIsEmpty");
        checkRestoreAnyVersionDeviceTest("saveSharedPrefValue");
    }

    private void installRestoreAnyVersionApp()
            throws DeviceNotAvailableException, FileNotFoundException {
        installPackage(RESTORE_ANY_VERSION_APP_APK, "-d", "-r");

        checkRestoreAnyVersionDeviceTest("checkAppVersionIsOld");
    }

    private void installNoRestoreAnyVersionApp()
            throws DeviceNotAvailableException, FileNotFoundException {
        installPackage(NO_RESTORE_ANY_VERSION_APK, "-d", "-r");

        checkRestoreAnyVersionDeviceTest("checkAppVersionIsOld");
    }

    private void installNewVersionApp()
            throws DeviceNotAvailableException, FileNotFoundException {
        installPackage(RESTORE_ANY_VERSION_UPDATE_APK, "-d", "-r");

        checkRestoreAnyVersionDeviceTest("checkAppVersionIsNew");
    }

    private void checkRestoreAnyVersionDeviceTest(String methodName)
            throws DeviceNotAvailableException {
        checkDeviceTest(RESTORE_ANY_VERSION_APP_PACKAGE, RESTORE_ANY_VERSION_DEVICE_TEST_NAME,
                methodName);
    }
}
