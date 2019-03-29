/**
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
 * limitations under the License.
 */

package android.security.cts;

import android.platform.test.annotations.SecurityTest;

@SecurityTest
public class Poc17_10 extends SecurityTestCase {

    /**
     * b/62058746
     */
    @SecurityTest
    public void testPocBug_62058746() throws Exception {
        enableAdbRoot(getDevice());
        if (containsDriver(getDevice(), "/proc/cld/athdiagpfs")) {
          AdbUtils.runPocNoOutput("Bug-62058746", getDevice(), 60);
        }
    }

    /**
     * b/37093119
     */
    @SecurityTest
    public void testPocBug_37093119() throws Exception {
        enableAdbRoot(getDevice());
        if (containsDriver(getDevice(), "/dev/graphics/fb*")) {
          AdbUtils.runPocNoOutput("Bug-37093119", getDevice(), 60);
        }
    }

    /**
     * b/62085265
     */
    @SecurityTest
    public void testPocBug_62085265() throws Exception {
        enableAdbRoot(getDevice());
        if (containsDriver(getDevice(), "/system/bin/pktlogconf")) {
            AdbUtils.runCommandLine("pktlogconf -a cld -erx,tx -s 1", getDevice());
            // Device can take up to 90 seconds before rebooting
            Thread.sleep(180000);
        }
    }

    /**
     * b/36817053
     */
    @SecurityTest
    public void testPocBug_36817053() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runCommandLine("dmesg -c" , getDevice());
        AdbUtils.runPocNoOutput("Bug-36817053", getDevice(), 60);
        String dmesgOut = AdbUtils.runCommandLine("dmesg", getDevice());
        assertNotMatches("[\\s\\n\\S]*" +
                         "__wlan_hdd_cfg80211_extscan_get_valid_channels: " +
                         "[0-9]+: attr request id failed[\\s\\n\\S]*",
                         dmesgOut);
    }

    /**
     * b/36730104
     */
    @SecurityTest
    public void testPocBug_36730104() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runCommandLine("dmesg -c" , getDevice());
        AdbUtils.runPocNoOutput("Bug-36730104", getDevice(), 60);
        String dmesgOut = AdbUtils.runCommandLine("dmesg", getDevice());
        assertNotMatches("[\\s\\n\\S]*" +
                         "hdd_extscan_start_fill_bucket_channel_spec: " +
                         "[0-9]+: attr bucket index failed[\\s\\n\\S]*",
                         dmesgOut);
    }
}
