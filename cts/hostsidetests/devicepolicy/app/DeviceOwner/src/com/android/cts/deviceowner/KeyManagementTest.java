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
package com.android.cts.deviceowner;

import static com.android.compatibility.common.util.FakeKeys.FAKE_RSA_1;
import static com.android.cts.deviceowner.BaseDeviceOwnerTest.getWho;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.net.Uri;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.test.ActivityInstrumentationTestCase2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.cert.Certificate;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.AssetManager;

public class KeyManagementTest extends ActivityInstrumentationTestCase2<KeyManagementActivity> {

    private static final long KEYCHAIN_TIMEOUT_MINS = 6;
    private DevicePolicyManager mDevicePolicyManager;

    public KeyManagementTest() {
        super(KeyManagementActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Confirm our DeviceOwner is set up
        mDevicePolicyManager = (DevicePolicyManager)
                getActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
        BaseDeviceOwnerTest.assertDeviceOwner(mDevicePolicyManager);

        // Hostside test has set a device lockscreen in order to enable credential storage
    }

    @Override
    protected void tearDown() throws Exception {
        // Hostside test will clear device lockscreen which in turn will clear the keystore.
        super.tearDown();
    }

    public void testCanInstallAndRemoveValidRsaKeypair() throws Exception {
        final String alias = "com.android.test.valid-rsa-key-1";
        final PrivateKey privKey = getPrivateKey(FAKE_RSA_1.privateKey , "RSA");
        final Certificate cert = getCertificate(FAKE_RSA_1.caCertificate);

        // Install keypair.
        assertTrue(mDevicePolicyManager.installKeyPair(getWho(), privKey, cert, alias));
        try {
            // Request and retrieve using the alias.
            assertGranted(alias, false);
            assertEquals(alias, new KeyChainAliasFuture(alias).get());
            assertGranted(alias, true);

            // Verify key is at least something like the one we put in.
            assertEquals(KeyChain.getPrivateKey(getActivity(), alias).getAlgorithm(), "RSA");
        } finally {
            // Delete regardless of whether the test succeeded.
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
        // Verify alias is actually deleted.
        assertGranted(alias, false);
    }

    public void testCanInstallWithAutomaticAccess() throws Exception {
        final String grant = "com.android.test.autogrant-key-1";
        final String withhold = "com.android.test.nongrant-key-1";
        final PrivateKey privKey = getPrivateKey(FAKE_RSA_1.privateKey , "RSA");
        final Certificate cert = getCertificate(FAKE_RSA_1.caCertificate);

        // Install keypairs.
        assertTrue(mDevicePolicyManager.installKeyPair(getWho(), privKey, new Certificate[] {cert},
                grant, true));
        assertTrue(mDevicePolicyManager.installKeyPair(getWho(), privKey, new Certificate[] {cert},
                withhold, false));
        try {
            // Verify only the requested key was actually granted.
            assertGranted(grant, true);
            assertGranted(withhold, false);

            // Verify the granted key is actually obtainable in PrivateKey form.
            assertEquals(KeyChain.getPrivateKey(getActivity(), grant).getAlgorithm(), "RSA");
        } finally {
            // Delete both keypairs.
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), grant));
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), withhold));
        }
        // Verify they're actually gone.
        assertGranted(grant, false);
        assertGranted(withhold, false);
    }

    public void testCanInstallCertChain() throws Exception {
        // Use assets/generate-client-cert-chain.sh to regenerate the client cert chain.
        final PrivateKey privKey = loadPrivateKeyFromAsset("user-cert-chain.key");
        final Collection<Certificate> certs = loadCertificatesFromAsset("user-cert-chain.crt");
        final Certificate[] certChain = certs.toArray(new Certificate[certs.size()]);
        final String alias = "com.android.test.clientkeychain";
        // Some sanity check on the cert chain
        assertTrue(certs.size() > 1);
        for (int i = 1; i < certs.size(); i++) {
            certChain[i - 1].verify(certChain[i].getPublicKey());
        }

        // Install keypairs.
        assertTrue(mDevicePolicyManager.installKeyPair(getWho(), privKey, certChain, alias, true));
        try {
            // Verify only the requested key was actually granted.
            assertGranted(alias, true);

            // Verify the granted key is actually obtainable in PrivateKey form.
            assertEquals(KeyChain.getPrivateKey(getActivity(), alias).getAlgorithm(), "RSA");

            // Verify the certificate chain is correct
            X509Certificate[] returnedCerts = KeyChain.getCertificateChain(getActivity(), alias);
            assertTrue(Arrays.equals(certChain, returnedCerts));
        } finally {
            // Delete both keypairs.
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
        // Verify they're actually gone.
        assertGranted(alias, false);
    }

    public void testGrantsDoNotPersistBetweenInstallations() throws Exception {
        final String alias = "com.android.test.persistent-key-1";
        final PrivateKey privKey = getPrivateKey(FAKE_RSA_1.privateKey , "RSA");
        final Certificate cert = getCertificate(FAKE_RSA_1.caCertificate);

        // Install keypair.
        assertTrue(mDevicePolicyManager.installKeyPair(getWho(), privKey, new Certificate[] {cert},
                alias, true));
        try {
            assertGranted(alias, true);
        } finally {
            // Delete and verify.
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
        assertGranted(alias, false);

        // Install again.
        assertTrue(mDevicePolicyManager.installKeyPair(getWho(), privKey, new Certificate[] {cert},
                alias, false));
        try {
            assertGranted(alias, false);
        } finally {
            // Delete.
            assertTrue(mDevicePolicyManager.removeKeyPair(getWho(), alias));
        }
    }

    public void testNullKeyParamsFailPredictably() throws Exception {
        final String alias = "com.android.test.null-key-1";
        final PrivateKey privKey = getPrivateKey(FAKE_RSA_1.privateKey, "RSA");
        final Certificate cert = getCertificate(FAKE_RSA_1.caCertificate);
        try {
            mDevicePolicyManager.installKeyPair(getWho(), null, cert, alias);
            fail("Exception should have been thrown for null PrivateKey");
        } catch (NullPointerException expected) {
        }
        try {
            mDevicePolicyManager.installKeyPair(getWho(), privKey, null, alias);
            fail("Exception should have been thrown for null Certificate");
        } catch (NullPointerException expected) {
        }
    }

    public void testNullAdminComponentIsDenied() throws Exception {
        final String alias = "com.android.test.null-admin-1";
        final PrivateKey privKey = getPrivateKey(FAKE_RSA_1.privateKey, "RSA");
        final Certificate cert = getCertificate(FAKE_RSA_1.caCertificate);
        try {
            mDevicePolicyManager.installKeyPair(null, privKey, cert, alias);
            fail("Exception should have been thrown for null ComponentName");
        } catch (SecurityException expected) {
        }
    }

    private void assertGranted(String alias, boolean expected) throws InterruptedException {
        boolean granted = false;
        try {
            granted = (KeyChain.getPrivateKey(getActivity(), alias) != null);
        } catch (KeyChainException e) {
        }
        assertEquals("Grant for alias: \"" + alias + "\"", expected, granted);
    }

    private static PrivateKey getPrivateKey(final byte[] key, String type)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        return KeyFactory.getInstance(type).generatePrivate(
                new PKCS8EncodedKeySpec(key));
    }

    private static Certificate getCertificate(byte[] cert) throws CertificateException {
        return CertificateFactory.getInstance("X.509").generateCertificate(
                new ByteArrayInputStream(cert));
    }

    private Collection<Certificate> loadCertificatesFromAsset(String assetName) {
        try {
            final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            AssetManager am = getActivity().getAssets();
            InputStream is = am.open(assetName);
            return (Collection<Certificate>) certFactory.generateCertificates(is);
        } catch (IOException | CertificateException e) {
            e.printStackTrace();
        }
        return null;
    }

    private PrivateKey loadPrivateKeyFromAsset(String assetName) {
        try {
            AssetManager am = getActivity().getAssets();
            InputStream is = am.open(assetName);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int length;
            byte[] buffer = new byte[4096];
            while ((length = is.read(buffer, 0, buffer.length)) != -1) {
              output.write(buffer, 0, length);
            }
            return getPrivateKey(output.toByteArray(), "RSA");
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return null;
    }

    private class KeyChainAliasFuture implements KeyChainAliasCallback {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private String mChosenAlias = null;

        @Override
        public void alias(final String chosenAlias) {
            mChosenAlias = chosenAlias;
            mLatch.countDown();
        }

        public KeyChainAliasFuture(String alias) throws UnsupportedEncodingException {
            /* Pass the alias as a GET to an imaginary server instead of explicitly asking for it,
             * to make sure the DPC actually has to do some work to grant the cert.
             */
            final Uri uri =
                    Uri.parse("https://example.org/?alias=" + URLEncoder.encode(alias, "UTF-8"));
            KeyChain.choosePrivateKeyAlias(getActivity(), this,
                    null /* keyTypes */, null /* issuers */, uri, null /* alias */);
        }

        public String get() throws InterruptedException {
            assertTrue("Chooser timeout", mLatch.await(KEYCHAIN_TIMEOUT_MINS, TimeUnit.MINUTES));
            return mChosenAlias;
        }
    }
}
