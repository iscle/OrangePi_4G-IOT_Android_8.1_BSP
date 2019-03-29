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
public class Poc17_08 extends SecurityTestCase {

    /**
     *  b/36266767
     */
    @SecurityTest
    public void testPocBug_36266767() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runPoc("Bug-36266767", getDevice(), 60);
    }

    /**
     *  b/36591162
     */
    @SecurityTest
    public void testPocBug_36591162() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/qce")) {
            AdbUtils.runPoc("Bug-36591162", getDevice(), 60);
        }
    }

    /**
     *  b/35258962
     */
    @SecurityTest
    public void testPocCVE_2017_9678() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/graphics/fb0")) {
            AdbUtils.runPoc("CVE-2017-9678", getDevice(), 60);
        }
    }

    /**
     *  b/36731152
     */
    @SecurityTest
    public void testPocCVE_2017_9692() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/graphics/fb2")) {
            AdbUtils.runPoc("CVE-2017-9692", getDevice(), 60);
        }
    }

    /**
     *  b/35764875
     */
    @SecurityTest
    public void testPocBug_35764875() throws Exception {
      enableAdbRoot(getDevice());
      if(containsDriver(getDevice(), "/dev/msm_aac")) {
            String pocOut = AdbUtils.runPoc("Bug-35764875", getDevice(), 60);
            assertNotMatches("[\\s\\n\\S]*information leaked, trycount=[0-9]" +
                             "+, rc=-[0-9]+, event_type=[0-9]+[\\s][0-9]{80}" +
                             "[\\s\\n\\S]*", pocOut);
      }
    }

    /**
     *  b/35644510
     */
    @SecurityTest
    public void testPocBug_35644510() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/qbt1000")) {
            String pocOut = AdbUtils.runPoc("Bug-35644510", getDevice(), 60);
            assertNotMatches("[\\s\\n\\S]*KERNEL ADDRESS LEAKED = 0x[a-f0-9]" +
                             "{16}[\\s\\n\\S]*", pocOut);
        }
    }

    /**
     *  b/35764241
     */
    @SecurityTest
    public void testPocCVE_2017_9680() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/qbt1000")) {
            AdbUtils.runPocNoOutput("CVE-2017-9680", getDevice(), 120);
        }
    }

    /**
     *  b/36818198
     *
     *  This test relies upon the existence of the following hddLog call in the function
     *  "__wlan_hdd_cfg80211_extscan_set_bssid_hotlist" in
     *  drivers/staging/qcacld-2.0/CORE/HDD/src/wlan_hdd_cfg80211.c:
     *  "hddLog(VOS_TRACE_LEVEL_ERROR, FL("attr mac address failed"));"
     */
    @SecurityTest
    public void testPocBug_36818198() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runCommandLine("dmesg -C", getDevice());
        AdbUtils.runPoc("Bug-36818198", getDevice(), 60);
        String pocOut = AdbUtils.runCommandLine("dmesg", getDevice());
        assertNotMatches("[\\s\\n\\S]*wlan: \\[[0-9]+:E :HDD\\] " +
                         "__wlan_hdd_cfg80211_extscan_set_bssid_hotlist: " +
                         "2775: attr mac address failed[\\s\\n\\S]*", pocOut);
    }
}
