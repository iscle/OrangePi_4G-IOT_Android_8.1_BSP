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
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test checking that 'fullBackupOnly' manifest attribute is respected by backup manager.
 *
 * Uses 3 different versions of the same app that differ only by 'fullBackupOnly' value and the
 * presence/absence of the backup agent.*
 *
 * Invokes device side tests provided by
 * {@link android.cts.backup.fullbackupapp.FullBackupOnlyTest}.
 *
 * The flow of the tests is the following:
 * 1. Install the app
 * 2. Generate files in data folder of the app, including a file in no_backup folder. The file in
 * no_backup folder is used for key/value backup by the backup agent of the app.
 * 3. Run 'bmgr backupnow'. Depending on the manifest of the app we expect either Dolly or Key/value
 * backup to be performed.
 * 4. Uninstall and reinstall the app.
 * 5. Check that the correct files were restored depending on the manifest:
 * - Files in the app's data folder for Dolly backup
 * - Only file in no_backup folder for key/value.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class FullBackupOnlyHostSideTest extends BaseBackupHostSideTest {

    private static final String FULLBACKUPONLY_APP_PACKAGE = "android.cts.backup.fullbackuponlyapp";
    private static final String FULLBACKUPONLY_DEVICE_TEST_CLASS_NAME =
            FULLBACKUPONLY_APP_PACKAGE + ".FullBackupOnlyTest";

    /**
     * The name of the APK of the app that has a backup agent and fullBackupOnly=false in the
     * manifest
     */
    private static final String FULLBACKUPONLY_FALSE_WITH_AGENT_APP_APK =
            "FullBackupOnlyFalseWithAgentApp.apk";

    /**
     * The name of the APK of the app that has no backup agent and fullBackupOnly=false in the
     * manifest
     */
    private static final String FULLBACKUPONLY_FALSE_NO_AGENT_APP_APK =
            "FullBackupOnlyFalseNoAgentApp.apk";

    /**
     * The name of the APK of the app that has a backup agent and fullBackupOnly=true in the
     * manifest
     */
    private static final String FULLBACKUPONLY_TRUE_WITH_AGENT_APP_APK =
            "FullBackupOnlyTrueWithAgentApp.apk";


    @After
    public void tearDown() throws Exception {
        super.tearDown();

        // Clear backup data and uninstall the package (in that order!)
        clearBackupDataInLocalTransport(FULLBACKUPONLY_APP_PACKAGE);
        assertNull(uninstallPackage(FULLBACKUPONLY_APP_PACKAGE));
    }

    /**
     * Tests that the app that doesn't have fullBackupOnly (same as fullBackupOnly=false by default)
     * and has a backup agent will get key/value backup.
     * We check that key/value data was restored after reinstall and dolly data was not.
     */
    @Test
    public void testFullBackupOnlyFalse_WithAgent() throws Exception {
        installPackage(FULLBACKUPONLY_FALSE_WITH_AGENT_APP_APK, "-d", "-r");

        checkFullBackupOnlyDeviceTest("createFiles");

        backupNowAndAssertSuccess(FULLBACKUPONLY_APP_PACKAGE);

        assertNull(uninstallPackage(FULLBACKUPONLY_APP_PACKAGE));

        installPackage(FULLBACKUPONLY_FALSE_WITH_AGENT_APP_APK, "-d", "-r");

        checkFullBackupOnlyDeviceTest("checkKeyValueFileExists");
        checkFullBackupOnlyDeviceTest("checkDollyFilesDontExist");
    }

    /**
     * Tests that the app that doesn't have fullBackupOnly (same as fullBackupOnly=false by default)
     * and has no backup agent will get Dolly backup.
     * We check that key/value data was not restored after reinstall and dolly data was.
     */
    @Test
    public void testFullBackupOnlyFalse_NoAgent() throws Exception {
        installPackage(FULLBACKUPONLY_FALSE_NO_AGENT_APP_APK, "-d", "-r");

        checkFullBackupOnlyDeviceTest("createFiles");

        backupNowAndAssertSuccess(FULLBACKUPONLY_APP_PACKAGE);

        assertNull(uninstallPackage(FULLBACKUPONLY_APP_PACKAGE));

        installPackage(FULLBACKUPONLY_FALSE_NO_AGENT_APP_APK, "-d", "-r");

        checkFullBackupOnlyDeviceTest("checkKeyValueFileDoesntExist");
        checkFullBackupOnlyDeviceTest("checkDollyFilesExist");
    }

    /**
     * Tests that the app that has fullBackupOnly=true  and has a backup agent will only get
     * Dolly backup.
     * We check that key/value data was not restored after reinstall and dolly data was.
     */
    @Test
    public void testFullBackupOnlyTrue_WithAgent() throws Exception {
        installPackage(FULLBACKUPONLY_TRUE_WITH_AGENT_APP_APK, "-d", "-r");

        checkFullBackupOnlyDeviceTest("createFiles");

        backupNowAndAssertSuccess(FULLBACKUPONLY_APP_PACKAGE);

        assertNull(uninstallPackage(FULLBACKUPONLY_APP_PACKAGE));

        installPackage(FULLBACKUPONLY_TRUE_WITH_AGENT_APP_APK, "-d", "-r");

        checkFullBackupOnlyDeviceTest("checkKeyValueFileDoesntExist");
        checkFullBackupOnlyDeviceTest("checkDollyFilesExist");
    }


    private void checkFullBackupOnlyDeviceTest(String methodName)
            throws DeviceNotAvailableException {
        checkDeviceTest(FULLBACKUPONLY_APP_PACKAGE, FULLBACKUPONLY_DEVICE_TEST_CLASS_NAME,
                methodName);
    }
}
