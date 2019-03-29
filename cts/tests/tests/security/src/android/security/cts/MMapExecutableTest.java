/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.test.AndroidTestCase;

/**
 * Verify that we can mmap executable code from an APK.
 * Prevent regression on: b/16727210 and b/16076402.
 */
@SecurityTest
public class MMapExecutableTest extends AndroidTestCase {
    public MMapExecutableTest() {}

    /**
     * Test that we can mmap the APK file executable.
     */
    public void testMMapExecutable() {
        assertTrue(mmapExecutable(getContext().getApplicationInfo().sourceDir));
    }

    /**
     * Attempts to mmap a portion of the specified file executable (PROT_EXEC).
     * Returns true if successful false otherwise.
     */
    public static final native boolean mmapExecutable(String filename);

    static {
        System.loadLibrary("ctssecurity_jni");
    }
}
