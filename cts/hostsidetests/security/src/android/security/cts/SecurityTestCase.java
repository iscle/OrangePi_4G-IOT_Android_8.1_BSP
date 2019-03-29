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

package android.security.cts;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.regex.Pattern;

public class SecurityTestCase extends DeviceTestCase {

    private long kernelStartTime;

    /**
     * Waits for device to be online, marks the most recent boottime of the device
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();

        String uptime = getDevice().executeShellCommand("cat /proc/uptime");
        kernelStartTime = System.currentTimeMillis()/1000 -
            Integer.parseInt(uptime.substring(0, uptime.indexOf('.')));
        //TODO:(badash@): Watch for other things to track.
        //     Specifically time when app framework starts
    }

    /**
     * Allows a CTS test to pass if called after a planned reboot.
     */
    public void updateKernelStartTime() throws Exception {
        kernelStartTime = System.currentTimeMillis()/1000 -
            Integer.parseInt(getDevice().executeShellCommand("cut -f1 -d. /proc/uptime").trim());
    }

    /**
     * Use {@link NativeDevice#enableAdbRoot()} internally.
     *
     * The test methods calling this function should run even if enableAdbRoot fails, which is why 
     * the return value is ignored. However, we may want to act on that data point in the future.
     */
    public boolean enableAdbRoot(ITestDevice mDevice) throws DeviceNotAvailableException {
        if(mDevice.enableAdbRoot()) {
            return true;
        } else {
            CLog.w("\"enable-root\" set to false! Root is required to check if device is vulnerable.");
            return false;
        }
    }

    /**
     * Check if a driver is present on a machine
     */
    public boolean containsDriver(ITestDevice mDevice, String driver) throws Exception {
        String result = mDevice.executeShellCommand("ls -Zl " + driver);
        if(result.contains("No such file or directory")) {
            return false;
        }
        return true;
    }

    /**
     * Makes sure the phone is online, and the ensure the current boottime is within 2 seconds
     * (due to rounding) of the previous boottime to check if The phone has crashed.
     */
    @Override
    public void tearDown() throws Exception {
        getDevice().waitForDeviceOnline(60 * 1000);
        String uptime = getDevice().executeShellCommand("cat /proc/uptime");
        assertTrue("Phone has had a hard reset",
            (System.currentTimeMillis()/1000 -
                Integer.parseInt(uptime.substring(0, uptime.indexOf('.')))
                    - kernelStartTime < 2));
        //TODO(badash@): add ability to catch runtime restart
        getDevice().disableAdbRoot();
    }

    public void assertMatches(String pattern, String input) throws Exception {
        assertTrue("Pattern not found", Pattern.matches(pattern, input));
    }

    public void assertNotMatches(String pattern, String input) throws Exception {
        assertFalse("Pattern found", Pattern.matches(pattern, input));
    }
}
