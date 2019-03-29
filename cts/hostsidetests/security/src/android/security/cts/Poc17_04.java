/**
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

import android.platform.test.annotations.SecurityTest;

public class Poc17_04 extends SecurityTestCase {

    /**
     *  b/33544431
     */
    @SecurityTest
    public void testPocCVE_2017_0576() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/qce")) {
            AdbUtils.runPoc("CVE-2017-0576", getDevice(), 60);
        }
    }

    /**
     *  b/34325986
     */
    @SecurityTest
    public void testPocCVE_2017_0580() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/touch_fwu")) {
            AdbUtils.runPoc("CVE-2017-0580", getDevice(), 60);
        }
    }

    /**
     *  b/33353601
     */
    @SecurityTest
    public void testPocCVE_2017_0462() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/seemplog")) {
            AdbUtils.runPoc("CVE-2017-0462", getDevice(), 60);
       }
    }

    /**
     *  b/33842951
     */
    @SecurityTest
    public void testPocCVE_2017_0577() throws Exception {
      enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/touch_fwu")) {
            AdbUtils.runPoc("CVE-2017-0577", getDevice(), 60);
        }
    }

    /**
     *  b/33966912
     */
    @SecurityTest
    public void testPocCVE_2016_10231() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/snd/controlC0")) {
            AdbUtils.runPoc("CVE-2016-10231", getDevice(), 60);
        }
    }

    /**
     *  b/34276203
     */
    @SecurityTest
    public void testPocCVE_2017_0564() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/ion")) {
            AdbUtils.runPocNoOutput("CVE-2017-0564", getDevice(), 60);
        }
    }

    /**
     *  b/33649808
     */
    @SecurityTest
    public void testPocCVE_2017_0586() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/snd/controlC0")) {
            AdbUtils.runPoc("CVE-2017-0586", getDevice(), 60);
        }
    }

    /**
     *  b/32551280
     */
    @SecurityTest
    public void testPocBug_32551280() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/sys/kernel/debug/msm_subsys/adsp")) {
            String pocOut = AdbUtils.runPoc("Bug-32551280", getDevice(), 60);
            //Regex to match: 64 bit memory address with leading 1's
            //                64 bit memory address with leading 0's
            //                32 bit memory addresses
            assertNotMatches("[\\s\\n\\S]*get 64 bits kernel stack information: " +
                             "(0xffff[a-fA-F8-9]|0x0000[0-7]|0x)[a-fA-F0-9]{8,11}" +
                             "[\\s\\n\\S]*", pocOut);
        }
    }
}
