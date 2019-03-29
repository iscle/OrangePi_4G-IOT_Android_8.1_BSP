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

package android.host.multiuser;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.Set;

/**
 * Test verifies that users can be created/switched to without error dialogs shown to the user
 */
public class CreateUsersNoAppCrashesTest extends BaseMultiUserTest {
    private int mInitialUserId;
    private static final long LOGCAT_POLL_INTERVAL_MS = 5000;
    private static final long BOOT_COMPLETED_TIMEOUT_MS = 120000;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInitialUserId = getDevice().getCurrentUser();
    }

    public void testCanCreateGuestUser() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        int userId = getDevice().createUser(
                "TestUser_" + System.currentTimeMillis() /* name */,
                true /* guest */,
                false /* ephemeral */);
        getDevice().executeAdbCommand("logcat", "-c"); // Reset log
        assertTrue("Couldn't switch to user " + userId, getDevice().switchUser(userId));
        Set<String> appErrors = new LinkedHashSet<>();
        assertTrue("Didn't receive BOOT_COMPLETED delivered notification. appErrors=" + appErrors,
                waitForBootCompleted(appErrors, userId));
        assertTrue("App error dialog(s) are present: " + appErrors, appErrors.isEmpty());
        assertTrue("Couldn't switch to user " + userId, getDevice().switchUser(mInitialUserId));
    }

    private boolean waitForBootCompleted(Set<String> appErrors, int targetUserId)
            throws DeviceNotAvailableException, InterruptedException {
        long ti = System.currentTimeMillis();
        while (System.currentTimeMillis() - ti < BOOT_COMPLETED_TIMEOUT_MS) {
            String logs = getDevice().executeAdbCommand("logcat", "-v", "brief", "-d");
            Scanner in = new Scanner(logs);
            while (in.hasNextLine()) {
                String line = in.nextLine();
                if (line.contains("Showing crash dialog for package")) {
                    appErrors.add(line);
                } else if (line.contains("Finished processing BOOT_COMPLETED for u" + targetUserId)) {
                    return true;
                }

            }
            in.close();
            Thread.sleep(LOGCAT_POLL_INTERVAL_MS);
        }
        return false;
    }
}
