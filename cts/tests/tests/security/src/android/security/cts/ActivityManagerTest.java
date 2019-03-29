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

import android.os.IBinder;
import android.platform.test.annotations.SecurityTest;
import junit.framework.TestCase;

@SecurityTest
public class ActivityManagerTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testActivityManager_injectInputEvents() throws ClassNotFoundException {
        try {
            /*
             * Should throw NoSuchMethodException. getEnclosingActivityContainer() has been
             * removed/renamed.
             * Patch:  https://android.googlesource.com/platform/frameworks/base/+/aa7e3ed%5E!/
             */
            Class.forName("android.app.ActivityManagerNative").getMethod(
                    "getEnclosingActivityContainer", IBinder.class);
            fail("ActivityManagerNative.getEnclosingActivityContainer() API should not be" +
                    "available in patched devices: Device is vulnerable to CVE-2015-1533");
        } catch (NoSuchMethodException e) {
            // Patched devices should throw this exception
        }
    }
}