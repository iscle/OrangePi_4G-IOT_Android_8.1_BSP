/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.keystore.cts;

import android.test.AndroidTestCase;
import java.lang.reflect.Method;

public class PutOverflowTest extends AndroidTestCase {
    public void testCrash() throws Exception {
        try {
            Class<?> keystoreClass = Class.forName("android.security.KeyStore");
            Method getInstance = keystoreClass.getMethod("getInstance");
            Method put = keystoreClass.getMethod("put",
                    String.class, byte[].class, int.class, int.class);
            Object keystore = getInstance.invoke(null);
            byte[] buffer = new byte[65536];
            Boolean result = (Boolean)put.invoke(keystore, "crashFile", buffer, -1, 0);
            assertTrue("Fix for ANDROID-22802399 not present", result);
        } catch (ReflectiveOperationException ignored) {
            // Since this test requires reflection avoid causing undue failures if classes or
            // methods were changed.
        }
    }
}
