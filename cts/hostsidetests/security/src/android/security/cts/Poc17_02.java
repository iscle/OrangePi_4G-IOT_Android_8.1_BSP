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
public class Poc17_02 extends SecurityTestCase {

    /**
     *  b/31796345
     */
    @SecurityTest
    public void testPocCVE_2017_0451() throws Exception {
	enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/voice_svc")) {
            AdbUtils.runPoc("CVE-2017-0451", getDevice(), 60);
        }
    }
}
