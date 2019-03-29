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

@SecurityTest
public class Poc17_06 extends SecurityTestCase {

    /**
     *  b/34328139
     */
    @SecurityTest
    public void testPocBug_34328139() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/mdss_rotator")) {
            AdbUtils.runPocNoOutput("Bug-34328139", getDevice(), 60);
        }
    }

    /**
     *  b/33452365
     */
    @SecurityTest
    public void testPocBug_33452365() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/snd/pcmC0D16c")) {
            AdbUtils.runPoc("Bug-33452365", getDevice(), 60);
        }
    }

    /**
     *  b/34125463
     */
    @SecurityTest
    public void testPocCVE_2017_0579() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/graphics/fb0")) {
            AdbUtils.runPoc("CVE-2017-0579", getDevice(), 60);
        }
    }

    /**
     *  b/33751424
     */
    @SecurityTest
    public void testPocCVE_2017_7369() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/snd/controlC0")) {
          AdbUtils.runPoc("CVE-2017-7369", getDevice(), 60);
        }
    }

    /**
     *  b/35047780
     */
    @SecurityTest
    public void testPocBug_35047780() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/ipa")) {
          AdbUtils.runPoc("Bug-35047780", getDevice(), 60);
        }
    }

    /**
     *  b/35048450
     */
    @SecurityTest
    public void testPocBug_35048450() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/ipa")) {
          AdbUtils.runPoc("Bug-35048450", getDevice(), 60);
        }
    }

    /**
     *  b/35047217
     */
    @SecurityTest
    public void testPocBug_35047217() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/ipa")) {
          AdbUtils.runPoc("Bug-35047217", getDevice(), 60);
        }
    }

    /**
     *  b/35644815
     */
    @SecurityTest
    public void testPocBug_35644815() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/sys/kernel/debug/ion/clients/pids/")) {
          String pocOut = AdbUtils.runPoc("Bug-35644815", getDevice(), 60);
          assertNotMatches("[\\s\\n\\S]*INFO DISC FLAG[\\s\\n\\S]*", pocOut);
        }
    }

    /**
     * b/35216793
     */
    @SecurityTest
    public void testPocBug_35216793() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/v4l-subdev*")) {
          AdbUtils.runPocNoOutput("Bug-35216793", getDevice(), 60);
        }
    }
}
