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

package android.backup.cts;

/**
 * Verifies that key methods are called in expected order during backup / restore.
 */
public class KeyValueLifecycleTest extends BaseBackupCtsTest {

    private static final String BACKUP_APP_NAME = "android.backup.kvapp";

    private static final int LOCAL_TRANSPORT_CONFORMING_FILE_SIZE = 5 * 1024;

    private static final int TIMEOUT_SECONDS = 30;

    public void testExpectedMethodsCalledInOrder() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        String backupSeparator = clearLogcat();

        // Make sure there's something to backup
        createTestFileOfSize(BACKUP_APP_NAME, LOCAL_TRANSPORT_CONFORMING_FILE_SIZE);

        // Request backup and wait for it to complete
        exec("bmgr backupnow " + BACKUP_APP_NAME);

        waitForLogcat(TIMEOUT_SECONDS,backupSeparator,
            "onCreate",
            "Backup requested",
            "onDestroy");

        String restoreSeparator = clearLogcat();

        // Now request restore and wait for it to complete
        exec("bmgr restore " + BACKUP_APP_NAME);

        waitForLogcat(TIMEOUT_SECONDS, restoreSeparator,
            "onCreate",
            "Restore requested",
            "onRestoreFinished",
            "onDestroy");
    }

}
