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
public class Poc17_07 extends SecurityTestCase {

    /**
     *  b/33863407
     */
    @SecurityTest
    public void testPocBug_33863407() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/sys/kernel/debug/mdp/reg")) {
            AdbUtils.runPoc("Bug-33863407", getDevice(), 60);
        }
    }

    /**
     *  b/36604779
     */
    @SecurityTest
    public void testPocBug_36604779() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/port")) {
          AdbUtils.runCommandLine("cat /dev/port", getDevice());
        }
    }

    /**
     *  b/34973477
     */
    @SecurityTest
    public void testPocCVE_2017_0705() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/proc/net/psched")) {
            AdbUtils.runPoc("CVE-2017-0705", getDevice(), 60);
        }
    }

    /**
     *  b/34126808
     */
    @SecurityTest
    public void testPocCVE_2017_8263() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/ashmem")) {
            AdbUtils.runPoc("CVE-2017-8263", getDevice(), 60);
        }
    }

    /**
     * b/34173755
     */
    @SecurityTest
    public void testPocBug_34173755() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/ashmem")) {
           AdbUtils.runPoc("Bug-34173755", getDevice(), 60);
        }
    }

     /**
     *  b/35950388
     */
    @SecurityTest
    public void testPocBug_35950388() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runPocNoOutput("Bug-35950388", getDevice(), 60);
    }

    /**
     *  b/34624155
     */
    @SecurityTest
    public void testPocBug_34624155() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/v4l-subdev*")) {
           AdbUtils.runPocNoOutput("Bug-34624155", getDevice(), 60);
        }
    }

    /**
     *  b/33299365
     */
    @SecurityTest
    public void testPocBug_33299365() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/v4l-subdev*")) {
           AdbUtils.runPocNoOutput("Bug-33299365", getDevice(), 60);
        }
    }

    /**
     *  b/35950805
     */
    @SecurityTest
    public void testPocBug_35950805() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/graphics/fb2")) {
          AdbUtils.runPocNoOutput("Bug-35950805", getDevice(), 60);
        }
    }

    /**
     *  b/35139833
     */
    @SecurityTest
    public void testPocBug_35139833() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/v4l-subdev*")) {
          AdbUtils.runPocNoOutput("Bug-35139833", getDevice(), 60);
        }
    }

    /**
     *  b/35468048
     */
    @SecurityTest
    public void testPocBug_35468048() throws Exception {
        enableAdbRoot(getDevice());
        String pocOut = AdbUtils.runPoc("Bug-35468048", getDevice(), 60);
        assertNotMatches("[\\s\\n\\S]*read succeeded: [0-9]+ bytes[\\s][\\S]" +
                         "{3} content: 0x[0-9]+. 0x[0-9]+[\\s\\n\\S]*", pocOut);
    }
}
