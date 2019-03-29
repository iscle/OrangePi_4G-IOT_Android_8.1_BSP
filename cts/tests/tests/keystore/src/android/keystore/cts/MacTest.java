/*
 * Copyright 2015 The Android Open Source Project
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

import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;

import junit.framework.TestCase;

import java.security.InvalidKeyException;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.Security;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Tests for algorithm-agnostic functionality of MAC implementations backed by Android Keystore.
 */
public class MacTest extends TestCase {

    private static final String EXPECTED_PROVIDER_NAME = TestUtils.EXPECTED_CRYPTO_OP_PROVIDER_NAME;

    private static final String[] EXPECTED_ALGORITHMS = {
        "HmacSHA1",
        "HmacSHA224",
        "HmacSHA256",
        "HmacSHA384",
        "HmacSHA512",
    };

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "227b212bebd775493929ef626729a587d3f81b8e18a3ed482d403910e184479b448cfa79b62bd90595efdd"
            + "15f87bd7b2d2dac480c61e969ba90a7b8ceadd3284");

    private static final byte[] SHORT_MSG_KAT_MESSAGE = HexEncoding.decode("a16037e3c901c9a1ab");

    private static final Map<String, Collection<KatVector>> SHORT_MSG_KAT_MACS =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    static {
        // From RI
        SHORT_MSG_KAT_MACS.put("HmacSHA1", Arrays.asList(
                new KatVector(HexEncoding.decode("4818c5466a1d05bc8f6ad4"),
                        HexEncoding.decode("aa178e4d174dc90d1ac3d22386c32d1af82396e7")),
                new KatVector(HexEncoding.decode("8d07f59f63fc493711a2217d8603cbc4d9874c58"),
                        HexEncoding.decode("002215de36f8836e966ff459af80c4b31fe0ca27")),
                new KatVector(HexEncoding.decode("f85f1c94023968729e3b4a7f495bf31c283b710662"),
                        HexEncoding.decode("f518db3015203606ea15145ad16b3d981db32a77"))));
        SHORT_MSG_KAT_MACS.put("HmacSHA224", Arrays.asList(
                new KatVector(
                        HexEncoding.decode(
                                "ed959815cc03f01e19ce93c1a3318ae87a905b7c27351d571ee858"),
                        HexEncoding.decode(
                                "abece4641458461f8b6a46f7daa61fc6119344c5c4bb5e7967da0e3e")),
                new KatVector(
                        HexEncoding.decode(
                                "9150e1ad6a9d12370a2423a5d95e9bc2dd73b4ee9bc96b03c9cc2fba"),
                        HexEncoding.decode(
                                "523aa06730d1abe7da2ba94a966cd20db56c771f1899e2850c31158c")),
                new KatVector(
                        HexEncoding.decode(
                                "424d5e7375c2040543f76b97451b1c074ee93b81ad24cef23800ebfe529a74ee2b"
                                ),
                        HexEncoding.decode(
                                "7627a86d829f45e3295a25813219ed5291f80029b972192d32a845c3"))));
        SHORT_MSG_KAT_MACS.put("HmacSHA256", Arrays.asList(
                new KatVector(
                        HexEncoding.decode(
                                "74a7ec4c79419d76fa5d3bdbedc17e5bebf0ee011c609b9f4c9126091613"),
                        HexEncoding.decode(
                                "c17b62519155b0d7f005f465becf9a1610635ae46a2c4d2b255851f201689ba5"
                                )),
                new KatVector(
                        HexEncoding.decode(
                                "42b44e6a1600bed10ca6c6dc24df2871790f948e73f9457fa4889c340cf69496"),
                        HexEncoding.decode(
                                "e9082a5db98c8086ad306ac23a1da9478eb5733757af6b1148d25fa1459290de")
                                ),
                new KatVector(
                        HexEncoding.decode(
                                "20bfc407c62022fea95f046f8ade6ee4b232665a9e97f75d3e35f1a9447991651a"
                                        ),
                        HexEncoding.decode(
                                "dbf10ca8c362aa665562065e76e42beb19444f61ab0828438714c82779b71a0d"
                                ))));
        SHORT_MSG_KAT_MACS.put("HmacSHA384", Arrays.asList(
                new KatVector(
                        HexEncoding.decode(
                                "7d277b2ec95fca68fe6ce0b665b28b48e128762714c66ca2c3405b432f6ab835e3"
                                ),
                        HexEncoding.decode(
                                "bf8555816d8fa058e1d0ed4be23abda522adfae629b6a8819dcc2416d00507782a"
                                        + "c714fdbfc7a340da4e6cf646a619f1")),
                new KatVector(
                        HexEncoding.decode(
                                "7b30abe948ceab9d94965f274fd2a21c966aa9bdf06476f94a0bcc6c20fd5d2bdc"
                                        + "e21af7c6fdf6017bce342a701f55c3"),
                        HexEncoding.decode(
                                "0fe51798528407119a5884f65ad76409983e978e25ab8f82aa412c08e76c8065d2"
                                        + "6dfdb1935de49036fb24262a532a29")),
                new KatVector(
                        HexEncoding.decode(
                                "26f232a40ada35850a14edf8f23c9ca97898ac0fa18640e5a7835230fa68f630b1"
                                        + "c4579bc059c318bb2c5da609db13f1567fa175a6439e1d729713d1fa"
                                        + "1039a3db"),
                        HexEncoding.decode(
                                "a086c610a382c24bb05e8bdd12cffc01055ec98a8f071239360cf8135205ffee33"
                                        + "2da2134bed2ec3efde8bf145d1d257"))));
        SHORT_MSG_KAT_MACS.put("HmacSHA512", Arrays.asList(
                new KatVector(
                        HexEncoding.decode(
                                "46fb12ef48d4e8162f0828a66c9f7124de"),
                        HexEncoding.decode(
                                "036320b51376f5840b03fdababac53c189d4d6b35f26f562a909f8ecac4a02c244"
                                        + "bfddc8f4eb89e0d0909fd2d8a46b796175e619cff215a675ce309540"
                                        + "42b1c9")),
                new KatVector(
                        HexEncoding.decode(
                                "45b3f16b1a247dd76f72ab2d5019f87b94efeb9a2fc01da3ca347050302dbda9c1"
                                        + "19cf991aaa30b747c808ec6bc19be7b5ae5e66176e38f222347a1659"
                                        + "15d007"),
                        HexEncoding.decode(
                                "92817ce36858ccad20a903e15952565d241ebaa87e07655754470090f1c6b9252a"
                                        + "cff9b873f36840fa8fdaaf91c6f9de3b82f46de0b1fdfa584eaf27de"
                                        + "f52c65")),
                new KatVector(
                        HexEncoding.decode(
                                "e91630c69c8c294755e27e5ccf01fe09e06de6c4e423c1c4ef0ac9b67f9af3cc6b"
                                        + "bc6292d18cf6e76738888a948b49f9509b44eb3af6974ca7e61f5208"
                                        + "b9f7dca3"),
                        HexEncoding.decode(
                                "6ff5616e9c38cef3d20076841c65b8747193eb8033ea61e8693715109e0e448966"
                                        + "3d8abcb2b7cf0911e461202112819fb8650ba02bdce08aa0d24b3873"
                                        + "30f18f"))));
    }

    private static final byte[] LONG_MSG_KAT_SEED = SHORT_MSG_KAT_MESSAGE;
    private static final int LONG_MSG_KAT_SIZE_BYTES = 3 * 1024 * 1024 + 149;

    private static final Map<String, byte[]> LONG_MSG_KAT_MACS =
            new TreeMap<String, byte[]>(String.CASE_INSENSITIVE_ORDER);
    static {
        // From RI
        LONG_MSG_KAT_MACS.put("HmacSHA1", HexEncoding.decode(
                "2a89d12da79f541512db9c35c0a1e76750e01d48"));
        LONG_MSG_KAT_MACS.put("HmacSHA224", HexEncoding.decode(
                "5fef55c822f9b931c1b4ad7142e0a74ceaddf03f0a6533155cc06871"));
        LONG_MSG_KAT_MACS.put("HmacSHA256", HexEncoding.decode(
                "0bc25f22b8993d003a95a88c6cfa1c5a7b067a8aae1064ef897712418569bfe9"));
        LONG_MSG_KAT_MACS.put("HmacSHA384", HexEncoding.decode(
                "595a616295123966126102c06d69f8bb06c11090490186243420c2c4692877d75752b220c1b0447320"
                + "959e28345523fc"));
        LONG_MSG_KAT_MACS.put("HmacSHA512", HexEncoding.decode(
                "aa97d594d799164d56e6652578f7884d1198bb2663641ad7903e3c0bda4c136e9f94ca0d16c3504302"
                + "2944224e538e88a5410adb38eaa5169b3125738990e6d0"));
    }


    private static final long DAY_IN_MILLIS = TestUtils.DAY_IN_MILLIS;

    public void testAlgorithmList() {
        // Assert that Android Keystore Provider exposes exactly the expected MAC algorithms. We
        // don't care whether the algorithms are exposed via aliases, as long as the canonical names
        // of algorithms are accepted.
        // If the Provider exposes extraneous algorithms, it'll be caught because it'll have to
        // expose at least one Service for such an algorithm, and this Service's algorithm will
        // not be in the expected set.

        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        Set<Service> services = provider.getServices();
        Set<String> actualAlgsLowerCase = new HashSet<String>();
        Set<String> expectedAlgsLowerCase = new HashSet<String>(
                Arrays.asList(TestUtils.toLowerCase(EXPECTED_ALGORITHMS)));
        for (Service service : services) {
            if ("Mac".equalsIgnoreCase(service.getType())) {
                String algLowerCase = service.getAlgorithm().toLowerCase(Locale.US);
                actualAlgsLowerCase.add(algLowerCase);
            }
        }

        TestUtils.assertContentsInAnyOrder(actualAlgsLowerCase,
                expectedAlgsLowerCase.toArray(new String[0]));
    }

    public void testAndroidKeyStoreKeysHandledByAndroidKeyStoreProvider() throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                SecretKey key = importDefaultKatKey(algorithm);

                // Generate a MAC
                Mac mac = Mac.getInstance(algorithm);
                mac.init(key);
                assertSame(provider, mac.getProvider());
            } catch (Throwable e) {
                throw new RuntimeException(algorithm + " failed", e);
            }
        }
    }

    public void testMacGeneratedForEmptyMessage() throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                SecretKey key = importDefaultKatKey(algorithm);

                // Generate a MAC
                Mac mac = Mac.getInstance(algorithm, provider);
                mac.init(key);
                byte[] macBytes = mac.doFinal();
                assertNotNull(macBytes);
                if (macBytes.length == 0) {
                    fail("Empty MAC");
                }
            } catch (Throwable e) {
                throw new RuntimeException(algorithm + " failed", e);
            }
        }
    }

    public void testMacGeneratedByAndroidKeyStoreVerifiesByAndroidKeyStore() throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                SecretKey key = importDefaultKatKey(algorithm);

                // Generate a MAC
                Mac mac = Mac.getInstance(algorithm, provider);
                mac.init(key);
                byte[] message = "This is a test".getBytes("UTF-8");
                byte[] macBytes = mac.doFinal(message);

                assertMacVerifiesOneShot(algorithm, provider, key, message, macBytes);
            } catch (Throwable e) {
                throw new RuntimeException(algorithm + " failed", e);
            }
        }
    }

    public void testMacGeneratedByAndroidKeyStoreVerifiesByHighestPriorityProvider()
            throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                SecretKey key = getDefaultKatKey(algorithm);
                SecretKey keystoreKey = importDefaultKatKey(algorithm);

                // Generate a MAC
                Mac mac = Mac.getInstance(algorithm, provider);
                mac.init(keystoreKey);
                byte[] message = "This is a test".getBytes("UTF-8");
                byte[] macBytes = mac.doFinal(message);

                assertMacVerifiesOneShot(algorithm, key, message, macBytes);
            } catch (Throwable e) {
                throw new RuntimeException(algorithm + " failed", e);
            }
        }
    }

    public void testMacGeneratedByHighestPriorityProviderVerifiesByAndroidKeyStore()
            throws Exception {
        Provider keystoreProvider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(keystoreProvider);
        for (String algorithm : EXPECTED_ALGORITHMS) {
            Provider signingProvider = null;
            try {
                SecretKey key = getDefaultKatKey(algorithm);
                SecretKey keystoreKey = importDefaultKatKey(algorithm);

                // Generate a MAC
                Mac mac = Mac.getInstance(algorithm);
                mac.init(key);
                signingProvider = mac.getProvider();
                byte[] message = "This is a test".getBytes("UTF-8");
                byte[] macBytes = mac.doFinal(message);

                assertMacVerifiesOneShot(
                        algorithm, keystoreProvider, keystoreKey, message, macBytes);
            } catch (Throwable e) {
                throw new RuntimeException(
                        algorithm + " failed, signing provider: " + signingProvider, e);
            }
        }
    }

    public void testSmallMsgKat() throws Exception {
        byte[] message = SHORT_MSG_KAT_MESSAGE;

        for (String algorithm : EXPECTED_ALGORITHMS) {
            for (KatVector testVector : SHORT_MSG_KAT_MACS.get(algorithm)) {
                byte[] keyBytes = testVector.key;
                try {
                    SecretKey key = TestUtils.importIntoAndroidKeyStore(
                            "test",
                            new SecretKeySpec(keyBytes, algorithm),
                            getWorkingImportParams(algorithm)).getKeystoreBackedSecretKey();

                    byte[] goodMacBytes = testVector.mac.clone();
                    assertNotNull(goodMacBytes);
                    assertMacVerifiesOneShot(algorithm, key, message, goodMacBytes);
                    assertMacVerifiesFedOneByteAtATime(algorithm, key, message, goodMacBytes);
                    assertMacVerifiesFedUsingFixedSizeChunks(
                            algorithm, key, message, goodMacBytes, 3);

                    byte[] messageWithBitFlip = message.clone();
                    messageWithBitFlip[messageWithBitFlip.length / 2] ^= 1;
                    assertMacDoesNotVerifyOneShot(algorithm, key, messageWithBitFlip, goodMacBytes);

                    byte[] goodMacWithBitFlip = goodMacBytes.clone();
                    goodMacWithBitFlip[goodMacWithBitFlip.length / 2] ^= 1;
                    assertMacDoesNotVerifyOneShot(algorithm, key, message, goodMacWithBitFlip);
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Failed for " + algorithm + " with key " + HexEncoding.encode(keyBytes),
                            e);
                }
            }
        }
    }

    public void testLargeMsgKat() throws Exception {
        byte[] message = TestUtils.generateLargeKatMsg(LONG_MSG_KAT_SEED, LONG_MSG_KAT_SIZE_BYTES);

        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                SecretKey key = importDefaultKatKey(algorithm);

                byte[] goodMacBytes = LONG_MSG_KAT_MACS.get(algorithm);
                assertNotNull(goodMacBytes);
                assertMacVerifiesOneShot(algorithm,  key, message, goodMacBytes);
                assertMacVerifiesFedUsingFixedSizeChunks(
                        algorithm, key, message, goodMacBytes, 20389);
                assertMacVerifiesFedUsingFixedSizeChunks(
                        algorithm, key, message, goodMacBytes, 393571);
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitFailsWhenNotAuthorizedToSign() throws Exception {
        int badPurposes = KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                | KeyProperties.PURPOSE_VERIFY;

        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyProtection good = getWorkingImportParams(algorithm);
                assertInitSucceeds(algorithm, good);
                assertInitThrowsInvalidKeyException(algorithm,
                        TestUtils.buildUpon(good, badPurposes).build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitFailsWhenDigestNotAuthorized() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyProtection good = getWorkingImportParams(algorithm);
                assertInitSucceeds(algorithm, good);

                String badKeyAlgorithm = ("HmacSHA256".equalsIgnoreCase(algorithm))
                        ? "HmacSHA384" : "HmacSHA256";
                assertInitThrowsInvalidKeyException(algorithm, badKeyAlgorithm, good);
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitFailsWhenKeyNotYetValid() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyProtection good = TestUtils.buildUpon(getWorkingImportParams(algorithm))
                        .setKeyValidityStart(new Date(System.currentTimeMillis() - DAY_IN_MILLIS))
                        .build();
                assertInitSucceeds(algorithm, good);

                Date badStartDate = new Date(System.currentTimeMillis() + DAY_IN_MILLIS);
                assertInitThrowsInvalidKeyException(algorithm,
                        TestUtils.buildUpon(good).setKeyValidityStart(badStartDate).build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitFailsWhenKeyNoLongerValidForOrigination() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyProtection good = TestUtils.buildUpon(getWorkingImportParams(algorithm))
                        .setKeyValidityForOriginationEnd(
                                new Date(System.currentTimeMillis() + DAY_IN_MILLIS))
                        .build();
                assertInitSucceeds(algorithm, good);

                Date badEndDate = new Date(System.currentTimeMillis() - DAY_IN_MILLIS);
                assertInitThrowsInvalidKeyException(algorithm,
                        TestUtils.buildUpon(good)
                                .setKeyValidityForOriginationEnd(badEndDate)
                                .build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitIgnoresThatKeyNoLongerValidForConsumption() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyProtection good = TestUtils.buildUpon(getWorkingImportParams(algorithm))
                        .setKeyValidityForConsumptionEnd(
                                new Date(System.currentTimeMillis() + DAY_IN_MILLIS))
                        .build();
                assertInitSucceeds(algorithm, good);

                Date badEndDate = new Date(System.currentTimeMillis() - DAY_IN_MILLIS);
                assertInitSucceeds(algorithm,
                        TestUtils.buildUpon(good)
                                .setKeyValidityForConsumptionEnd(badEndDate)
                                .build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    private void assertMacVerifiesOneShot(
            String algorithm,
            SecretKey key,
            byte[] message,
            byte[] mac) throws Exception {
        assertMacVerifiesOneShot(algorithm, null, key, message, mac);
    }

    private void assertMacVerifiesOneShot(
            String algorithm,
            Provider provider,
            SecretKey key,
            byte[] message,
            byte[] mac) throws Exception {
        Mac m = (provider != null)
                ? Mac.getInstance(algorithm, provider) : Mac.getInstance(algorithm);
        m.init(key);
        byte[] mac2 = m.doFinal(message);
        if (!Arrays.equals(mac, mac2)) {
            fail("MAC did not verify. algorithm: " + algorithm
                    + ", provider: " + m.getProvider().getName()
                    + ", MAC (" + mac.length + " bytes): " + HexEncoding.encode(mac)
                    + ", actual MAC (" + mac2.length + " bytes): " + HexEncoding.encode(mac2));
        }
    }

    private void assertMacDoesNotVerifyOneShot(
            String algorithm,
            SecretKey key,
            byte[] message,
            byte[] mac) throws Exception {
        Mac m = Mac.getInstance(algorithm);
        m.init(key);
        byte[] mac2 = m.doFinal(message);
        if (Arrays.equals(mac, mac2)) {
            fail("MAC verifies unexpectedly. algorithm: " + algorithm
                    + ", provider: " + m.getProvider().getName()
                    + ", MAC (" + mac.length + " bytes): " + HexEncoding.encode(mac));
        }
    }

    private void assertMacVerifiesFedOneByteAtATime(
            String algorithm,
            SecretKey key,
            byte[] message,
            byte[] mac) throws Exception {
        Mac m = Mac.getInstance(algorithm);
        m.init(key);
        for (int i = 0; i < message.length; i++) {
            m.update(message[i]);
        }
        byte[] mac2 = m.doFinal();
        if (!Arrays.equals(mac, mac2)) {
            fail("MAC did not verify. algorithm: " + algorithm
                    + ", provider: " + m.getProvider().getName()
                    + ", MAC (" + mac.length + " bytes): " + HexEncoding.encode(mac)
                    + ", actual MAC (" + mac2.length + " bytes): " + HexEncoding.encode(mac2));
        }
    }

    private void assertMacVerifiesFedUsingFixedSizeChunks(
            String algorithm,
            SecretKey key,
            byte[] message,
            byte[] mac,
            int chunkSizeBytes) throws Exception {
        Mac m = Mac.getInstance(algorithm);
        m.init(key);
        int messageRemaining = message.length;
        int messageOffset = 0;
        while (messageRemaining > 0) {
            int actualChunkSizeBytes =  Math.min(chunkSizeBytes, messageRemaining);
            m.update(message, messageOffset, actualChunkSizeBytes);
            messageOffset += actualChunkSizeBytes;
            messageRemaining -= actualChunkSizeBytes;
        }
        byte[] mac2 = m.doFinal();
        if (!Arrays.equals(mac, mac2)) {
            fail("MAC did not verify. algorithm: " + algorithm
                    + ", provider: " + m.getProvider().getName()
                    + ", MAC (" + mac.length + " bytes): " + HexEncoding.encode(mac)
                    + ", actual MAC (" + mac2.length + " bytes): " + HexEncoding.encode(mac2));
        }
    }

    private void assertInitSucceeds(String algorithm, KeyProtection keyProtection)
            throws Exception {
        assertInitSucceeds(algorithm, algorithm, keyProtection);
    }

    private void assertInitSucceeds(
            String macAlgorithm, String keyAlgorithm, KeyProtection keyProtection)
                    throws Exception {
        SecretKey key = importDefaultKatKey(keyAlgorithm, keyProtection);
        Mac mac = Mac.getInstance(macAlgorithm);
        mac.init(key);
    }

    private void assertInitThrowsInvalidKeyException(String algorithm, KeyProtection keyProtection)
                    throws Exception {
        assertInitThrowsInvalidKeyException(algorithm, algorithm, keyProtection);
    }

    private void assertInitThrowsInvalidKeyException(
            String macAlgorithm, String keyAlgorithm, KeyProtection keyProtection)
                    throws Exception {
        SecretKey key = importDefaultKatKey(keyAlgorithm, keyProtection);
        Mac mac = Mac.getInstance(macAlgorithm);
        try {
            mac.init(key);
            fail("InvalidKeyException should have been thrown. MAC algorithm: " + macAlgorithm
                    + ", key algorithm: " + keyAlgorithm);
        } catch (InvalidKeyException expected) {}
    }

    private SecretKey getDefaultKatKey(String keyAlgorithm) {
        return new SecretKeySpec(KAT_KEY, keyAlgorithm);
    }

    private SecretKey importDefaultKatKey(String keyAlgorithm) throws Exception {
        return importDefaultKatKey(
                keyAlgorithm,
                new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build());
    }

    private SecretKey importDefaultKatKey(
            String keyAlgorithm, KeyProtection keyProtection) throws Exception {
        return TestUtils.importIntoAndroidKeyStore(
                "test1",
                getDefaultKatKey(keyAlgorithm),
                keyProtection).getKeystoreBackedSecretKey();
    }

    private static KeyProtection getWorkingImportParams(
            @SuppressWarnings("unused") String algorithm) {
        return new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build();
    }

    private static class KatVector {
        public byte[] key;
        public byte[] mac;

        public KatVector(byte[] key, byte[] mac) {
            this.key = key;
            this.mac = mac;
        }
    }
}
