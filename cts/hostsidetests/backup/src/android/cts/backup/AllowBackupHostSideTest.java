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

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.device.DeviceNotAvailableException;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test checking that allowBackup manifest attribute is respected by backup manager.
 *
 * Uses 2 apps that differ only by 'allowBackup' manifest attribute value.
 *
 * Tests 2 scenarios:
 *
 * 1. App that has 'allowBackup=false' in the manifest shouldn't be backed up.
 * 2. App that doesn't have 'allowBackup' in the manifest (default is true) should be backed up.
 *
 * The flow of the tests is the following:
 * 1. Install the app
 * 2. Generate files in the app's data folder.
 * 3. Run 'bmgr backupnow'. Depending on the manifest we expect either 'Success' or
 * 'Backup is not allowed' in the output.
 * 4. Uninstall/reinstall the app
 * 5. Check whether the files were restored or not depending on the manifest.
 *
 * Invokes device side tests provided by
 * android.cts.backup.backupnotallowedapp.AllowBackupTest.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AllowBackupHostSideTest extends BaseBackupHostSideTest {

    private static final String ALLOWBACKUP_APP_NAME = "android.cts.backup.backupnotallowedapp";
    private static final String ALLOWBACKUP_DEVICE_TEST_CLASS_NAME =
            ALLOWBACKUP_APP_NAME + ".AllowBackupTest";

    /** The name of the APK of the app that has allowBackup=false in the manifest */
    private static final String ALLOWBACKUP_FALSE_APP_APK = "BackupNotAllowedApp.apk";

    /** The name of the APK of the app that doesn't have allowBackup in the manifest
     * (same as allowBackup=true by default) */
    private static final String ALLOWBACKUP_APP_APK = "BackupAllowedApp.apk";

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        // Clear backup data and uninstall the package (in that order!)
        clearBackupDataInLocalTransport(ALLOWBACKUP_APP_NAME);
        assertNull(uninstallPackage(ALLOWBACKUP_APP_NAME));
    }

    @Test
    public void testAllowBackup_False() throws Exception {
        installPackage(ALLOWBACKUP_FALSE_APP_APK, "-d", "-r");

        // Generate the files that are going to be backed up.
        checkAllowBackupDeviceTest("createFiles");

        // Do a backup
        String backupnowOutput = backupNow(ALLOWBACKUP_APP_NAME);

        assertBackupIsNotAllowed(ALLOWBACKUP_APP_NAME, backupnowOutput);

        assertNull(uninstallPackage(ALLOWBACKUP_APP_NAME));

        installPackage(ALLOWBACKUP_FALSE_APP_APK, "-d", "-r");

        checkAllowBackupDeviceTest("checkNoFilesExist");
    }

    @Test
    public void testAllowBackup_True() throws Exception {
        installPackage(ALLOWBACKUP_APP_APK, "-d", "-r");

        // Generate the files that are going to be backed up.
        checkAllowBackupDeviceTest("createFiles");

        // Do a backup
        String backupnowOutput = backupNow(ALLOWBACKUP_APP_NAME);

        assertBackupIsSuccessful(ALLOWBACKUP_APP_NAME, backupnowOutput);

        assertNull(uninstallPackage(ALLOWBACKUP_APP_NAME));

        installPackage(ALLOWBACKUP_APP_APK, "-d", "-r");

        checkAllowBackupDeviceTest("checkAllFilesExist");
    }

    private void checkAllowBackupDeviceTest(String methodName)
            throws DeviceNotAvailableException {
        checkDeviceTest(ALLOWBACKUP_APP_NAME, ALLOWBACKUP_DEVICE_TEST_CLASS_NAME,
                methodName);
    }

}
