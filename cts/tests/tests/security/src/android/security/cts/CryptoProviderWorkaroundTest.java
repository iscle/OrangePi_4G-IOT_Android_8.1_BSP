/*
 * Copyright 2016 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import dalvik.system.VMRuntime;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * http://b/28550092 : Removal of "Crypto" provider in N caused application compatibility
 * issues for callers of SecureRandom. To improve compatibility the provider is not registered
 * as a JCA Provider obtainable via Security.getProvider() but is made available for
 * SecureRandom.getInstance() iff the application targets API <= 23.
 */
@RunWith(JUnit4.class)
public class CryptoProviderWorkaroundTest {
    @Test
    public void cryptoProvider_withWorkaround_Success() throws Exception {
        // Assert that SecureRandom is still using the default value. Sanity check.
        assertEquals(SecureRandom.DEFAULT_SDK_TARGET_FOR_CRYPTO_PROVIDER_WORKAROUND,
                SecureRandom.getSdkTargetForCryptoProviderWorkaround());

        try {
            // Modify the maximum target SDK to apply the workaround, thereby enabling the
            // workaround for the current SDK and enabling it to be tested.
            SecureRandom.setSdkTargetForCryptoProviderWorkaround(
                    VMRuntime.getRuntime().getTargetSdkVersion());

            // Assert that the crypto provider is not installed...
            assertNull(Security.getProvider("Crypto"));
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG", "Crypto");
            assertNotNull(sr);
            // ...but we can get a SecureRandom from it...
            assertEquals("org.apache.harmony.security.provider.crypto.CryptoProvider",
                    sr.getProvider().getClass().getName());
            // ...yet it's not installed. So the workaround worked.
            assertNull(Security.getProvider("Crypto"));
        } finally {
            // Reset the target SDK for the workaround to the default / real value.
            SecureRandom.setSdkTargetForCryptoProviderWorkaround(
                    SecureRandom.DEFAULT_SDK_TARGET_FOR_CRYPTO_PROVIDER_WORKAROUND);
        }
    }

    @Test
    public void cryptoProvider_withoutWorkaround_Failure() throws Exception {
        // Assert that SecureRandom is still using the default value. Sanity check.
        assertEquals(SecureRandom.DEFAULT_SDK_TARGET_FOR_CRYPTO_PROVIDER_WORKAROUND,
                SecureRandom.getSdkTargetForCryptoProviderWorkaround());

        try {
            // We set the limit SDK for the workaround at the previous one, indicating that the
            // workaround shouldn't be in place.
            SecureRandom.setSdkTargetForCryptoProviderWorkaround(
                    VMRuntime.getRuntime().getTargetSdkVersion() - 1);

            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG", "Crypto");
            fail("Should throw " + NoSuchProviderException.class.getName());
        } catch(NoSuchProviderException expected) {
            // The workaround doesn't work. As expected.
        } finally {
            // Reset the target SDK for the workaround to the default / real value.
            SecureRandom.setSdkTargetForCryptoProviderWorkaround(
                    SecureRandom.DEFAULT_SDK_TARGET_FOR_CRYPTO_PROVIDER_WORKAROUND);
        }
    }
}
