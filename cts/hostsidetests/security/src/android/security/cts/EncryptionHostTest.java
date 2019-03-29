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

package android.security.cts;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;

import java.util.UUID;

/**
 * Host side encryption tests
 *
 * These tests analyze a userdebug device for correct encryption properties
 */
public class EncryptionHostTest extends DeviceTestCase {
    ITestDevice mDevice;
    boolean mUserDebug;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
        mUserDebug = "userdebug".equals(mDevice.executeShellCommand("getprop ro.build.type").trim());
        if (!mUserDebug) {
            return;
        }
        mDevice.enableAdbRoot();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mUserDebug) {
            mDevice.disableAdbRoot();
        }
        super.tearDown();
    }

    public void testEncrypted() throws DeviceNotAvailableException {
        if (!mUserDebug || !mDevice.isDeviceEncrypted()) {
            return;
        }
        /*
        // Create file with name and contents a random UUID so we can search for it
        String uuid = UUID.randomUUID().toString();
        mDevice.executeShellCommand("echo " + uuid + " > /data/local/tmp/" + uuid);
        String uuidReturned = mDevice.executeShellCommand("cat /data/local/tmp/" + uuid).trim();
        assertTrue(uuid.equals(uuidReturned));

        // Get name of /data device
        String fstabName = mDevice.executeShellCommand("ls /fstab.*");
        String[] fstab = mDevice.executeShellCommand("cat " + fstabName).split("\n");
        String path = null;
        for (String line : fstab) {
            String[] entries = line.split("[ \t]+");
            if (entries.length < 2) continue;
            if ("/data".equals(entries[1])) {
                path = entries[0];
                break;
            }
        }
        assertFalse(path == null);

        // grep it for the data
        String result = mDevice.executeShellCommand("grep " + uuid + " " + path + " ").trim();
        assertTrue("".equals(result));

        // Clean up
        mDevice.executeShellCommand("rm /data/local/tmp/" + uuid);
        */
    }
}
