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

public class TestAttributes extends TestCase {
    public void testCleartextTrafficPermitted() throws Exception {
        NetworkSecurityPolicy instance = NetworkSecurityPolicy.getInstance();
        // Since there are some configs that do not allow cleartext the non-hostname aware version
        // should return False.
        assertFalse(instance.isCleartextTrafficPermitted());
        // Domain that explicitly does not allow cleartext traffic.
        assertFalse(instance.isCleartextTrafficPermitted("foo.bar"));
        // Subdomains are not included in the above rule, they should use the base-config's value.
        assertTrue(instance.isCleartextTrafficPermitted("example.foo.bar"));
        // Domains in a domain-config that do not specify the flag, should inherit from the
        // base-config.
        assertTrue(instance.isCleartextTrafficPermitted("android.com"));
        assertTrue(instance.isCleartextTrafficPermitted("foo.android.com"));
        // Domains in a domain-config that explicitly allow cleartext.
        assertTrue(instance.isCleartextTrafficPermitted("example.com"));
        assertTrue(instance.isCleartextTrafficPermitted("test.example.com"));
        // Domain not specified in a domain-config, should use the base-config's value.
        assertTrue(instance.isCleartextTrafficPermitted("example.com"));
    }
}
