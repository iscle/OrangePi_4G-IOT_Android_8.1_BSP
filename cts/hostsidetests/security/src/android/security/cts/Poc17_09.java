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
public class Poc17_09 extends SecurityTestCase {

    /**
     *  b/33039685
     */
    @SecurityTest
    public void testPocBug_33039685() throws Exception {
        enableAdbRoot(getDevice());
        if (containsDriver(getDevice(), "/sys/kernel/debug/pci-msm/")) {
          AdbUtils.runPocNoOutput("Bug-33039685", getDevice(), 60);
        }
    }

    /**
     *  b/35676417
     */
    @SecurityTest
    public void testPocBug_35676417() throws Exception {
        enableAdbRoot(getDevice());
        if (containsDriver(getDevice(), "/sys/devices/soc/7544000.qcom,sps-dma/driver_override")) {
          AdbUtils.runPocNoOutput("Bug-35676417", getDevice(), 60);
        }
    }

    /**
     *  b/35644812
     */
    @SecurityTest
    public void testPocBug_35644812() throws Exception {
        enableAdbRoot(getDevice());
        if (containsDriver(getDevice(), "/dev/sg0")) {
          AdbUtils.runPocNoOutput("Bug-35644812", getDevice(), 60);
        }
    }

    /*
     * b/36492827
     */
    @SecurityTest
    public void testPocBug_36492827() throws Exception {
     enableAdbRoot(getDevice());
      if (containsDriver(getDevice(), "/dev/v4l-subdev*")) {
        AdbUtils.runPocNoOutput("Bug-36492827", getDevice(), 60);
      }
    }
}
