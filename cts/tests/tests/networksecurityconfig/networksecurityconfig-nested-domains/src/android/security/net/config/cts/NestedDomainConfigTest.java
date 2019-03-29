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

package android.security.net.config.cts;

import android.security.NetworkSecurityPolicy;

import junit.framework.TestCase;

public class NestedDomainConfigTest extends TestCase {

    public void testRootDomainConfig() throws Exception {
        TestUtils.assertTlsConnectionFails("android.com", 443);
        NetworkSecurityPolicy instance = NetworkSecurityPolicy.getInstance();
        assertTrue(instance.isCleartextTrafficPermitted("android.com"));
    }

    public void testNestedDomainConfig() throws Exception {
        TestUtils.assertTlsConnectionFails("developer.android.com", 443);
        NetworkSecurityPolicy instance = NetworkSecurityPolicy.getInstance();
        assertFalse(instance.isCleartextTrafficPermitted("developer.android.com"));
    }
}
