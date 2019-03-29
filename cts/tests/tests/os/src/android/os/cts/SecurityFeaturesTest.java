/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.os.cts;

import android.system.Os;
import static android.system.OsConstants.PR_GET_DUMPABLE;

import junit.framework.TestCase;

public class SecurityFeaturesTest extends TestCase {

    /**
     * Iterate over all possible capabilities, testing to make sure each capability
     * has been removed from the app's capability bounding set.
     */
    public void testPrCapbsetEmpty() {
        int i = 0;
        while (true) {
            int result = OSFeatures.prctlCapBsetRead(i);
            if (result == -1) {
                // The kernel has told us that the capability we're inquiring about
                // doesn't exist. Capabilities are assigned sequentially and
                // and monotonically increase with each kernel release, so if we
                // see -1, we know we've examined every capability the kernel
                // knows about.
                break;
            }
            assertEquals("capability " + i + " is still in the bounding set",
                         0, result);
            i++;
        }
    }

    /**
     * Verifies that prctl(PR_GET_DUMPABLE) == ro.debuggable
     *
     * When PR_SET_DUMPABLE is 0, an application will not generate a
     * coredump, and PTRACE_ATTACH is disallowed. It's a security best
     * practice to ensure that PR_SET_DUMPABLE is 0, to prevent an app
     * from leaking the contents of its memory to persistent storage,
     * other processes, or logs.
     *
     * By default, PR_SET_DUMPABLE is 0 for zygote spawned apps, except
     * in the following circumstances:
     *
     * 1) ro.debuggable=1 (global debuggable enabled, i.e., userdebug or
     * eng builds).
     *
     * 2) android:debuggable="true" in the manifest for an individual
     * application.
     *
     * 3) An app which explicitly calls prctl(PR_SET_DUMPABLE, 1).
     *
     * For this test, neither #2 nor #3 are true, so we expect ro.debuggable
     * to exactly equal prctl(PR_GET_DUMPABLE).
     */
    public void testPrctlDumpable() throws Exception {
        int ro_debuggable = Integer.parseInt(BuildTest.getProperty(BuildTest.RO_DEBUGGABLE));
        int prctl_dumpable = Os.prctl(PR_GET_DUMPABLE, 0, 0, 0, 0);
        assertEquals(ro_debuggable, prctl_dumpable);
    }
}
