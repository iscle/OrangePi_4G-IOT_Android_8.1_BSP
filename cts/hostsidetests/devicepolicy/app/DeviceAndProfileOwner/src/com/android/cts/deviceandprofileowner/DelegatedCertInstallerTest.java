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

package com.android.cts.deviceandprofileowner;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.security.KeyChainException;
import android.test.MoreAsserts;

import com.android.org.conscrypt.TrustedCertificateStore;

import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Exercise delegated cert installer APIs in {@link DevicePolicyManager} by setting the test app
 * (CtsCertInstallerApp) as a delegated cert installer and then asking it to invoke various
 * cert-related APIs. The expected certificate changes are validated both remotely and locally.
 */
public class DelegatedCertInstallerTest extends BaseDeviceAdminTest {

    private static final String CERT_INSTALLER_PACKAGE = "com.android.cts.certinstaller";
    private static final String NOT_EXIST_CERT_INSTALLER_PACKAGE
            = "com.android.cts.certinstaller.not_exist";

    private static final String ACTION_INSTALL_CERT = "com.android.cts.certinstaller.install_cert";
    private static final String ACTION_REMOVE_CERT = "com.android.cts.certinstaller.remove_cert";
    private static final String ACTION_VERIFY_CERT = "com.android.cts.certinstaller.verify_cert";
    private static final String ACTION_INSTALL_KEYPAIR =
            "com.android.cts.certinstaller.install_keypair";
    private static final String ACTION_CERT_OPERATION_DONE = "com.android.cts.certinstaller.done";

    private static final String EXTRA_CERT_DATA = "extra_cert_data";
    private static final String EXTRA_KEY_DATA = "extra_key_data";
    private static final String EXTRA_KEY_ALIAS = "extra_key_alias";
    private static final String EXTRA_RESULT_VALUE = "extra_result_value";
    private static final String EXTRA_RESULT_EXCEPTION = "extra_result_exception";
    // package name of receiver has to be specified explicitly as the receiver is registered in
    // manifest
    private static final ComponentName CERT_INSTALLER_COMPONENT = new ComponentName(
            CERT_INSTALLER_PACKAGE, "com.android.cts.certinstaller.CertInstallerReceiver");

    /*
     * The CA and keypair below are generated with:
     *
     * openssl req -new -x509 -days 3650 -extensions v3_ca -keyout cakey.pem -out cacert.pem
     * openssl req -newkey rsa:1024 -keyout userkey.pem -nodes -days 3650 -out userkey.req
     * mkdir -p demoCA/newcerts
     * touch demoCA/index.txt
     * echo "01" > demoCA/serial
     * openssl ca -out usercert.pem -in userkey.req -cert cacert.pem -keyfile cakey.pem -days 3650
     */

     // Content from cacert.pem
    private static final String TEST_CA =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIDXTCCAkWgAwIBAgIJAK9Tl/F9V8kSMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV\n" +
            "BAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBX\n" +
            "aWRnaXRzIFB0eSBMdGQwHhcNMTUwMzA2MTczMjExWhcNMjUwMzAzMTczMjExWjBF\n" +
            "MQswCQYDVQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50\n" +
            "ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB\n" +
            "CgKCAQEAvItOutsE75WBTgTyNAHt4JXQ3JoseaGqcC3WQij6vhrleWi5KJ0jh1/M\n" +
            "Rpry7Fajtwwb4t8VZa0NuM2h2YALv52w1xivql88zce/HU1y7XzbXhxis9o6SCI+\n" +
            "oVQSbPeXRgBPppFzBEh3ZqYTVhAqw451XhwdA4Aqs3wts7ddjwlUzyMdU44osCUg\n" +
            "kVg7lfPf9sTm5IoHVcfLSCWH5n6Nr9sH3o2ksyTwxuOAvsN11F/a0mmUoPciYPp+\n" +
            "q7DzQzdi7akRG601DZ4YVOwo6UITGvDyuAAdxl5isovUXqe6Jmz2/myTSpAKxGFs\n" +
            "jk9oRoG6WXWB1kni490GIPjJ1OceyQIDAQABo1AwTjAdBgNVHQ4EFgQUH1QIlPKL\n" +
            "p2OQ/AoLOjKvBW4zK3AwHwYDVR0jBBgwFoAUH1QIlPKLp2OQ/AoLOjKvBW4zK3Aw\n" +
            "DAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAcMi4voMMJHeQLjtq8Oky\n" +
            "Azpyk8moDwgCd4llcGj7izOkIIFqq/lyqKdtykVKUWz2bSHO5cLrtaOCiBWVlaCV\n" +
            "DYAnnVLM8aqaA6hJDIfaGs4zmwz0dY8hVMFCuCBiLWuPfiYtbEmjHGSmpQTG6Qxn\n" +
            "ZJlaK5CZyt5pgh5EdNdvQmDEbKGmu0wpCq9qjZImwdyAul1t/B0DrsWApZMgZpeI\n" +
            "d2od0VBrCICB1K4p+C51D93xyQiva7xQcCne+TAnGNy9+gjQ/MyR8MRpwRLv5ikD\n" +
            "u0anJCN8pXo6IMglfMAsoton1J6o5/ae5uhC6caQU8bNUsCK570gpNfjkzo6rbP0\n" +
            "wQ==\n" +
            "-----END CERTIFICATE-----";
    // Content from userkey.pem without the private key header and footer.
    private static final String TEST_KEY =
            "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBALCYprGsTU+5L3KM\n" +
            "fhkm0gXM2xjGUH+543YLiMPGVr3eVS7biue1/tQlL+fJsw3rqsPKJe71RbVWlpqU\n" +
            "mhegxG4s3IvGYVB0KZoRIjDKmnnvlx6nngL2ZJ8O27U42pHsw4z4MKlcQlWkjL3T\n" +
            "9sV6zW2Wzri+f5mvzKjhnArbLktHAgMBAAECgYBlfVVPhtZnmuXJzzQpAEZzTugb\n" +
            "tN1OimZO0RIocTQoqj4KT+HkiJOLGFQPwbtFpMre+q4SRqNpM/oZnI1yRtKcCmIc\n" +
            "mZgkwJ2k6pdSxqO0ofxFFTdT9czJ3rCnqBHy1g6BqUQFXT4olcygkxUpKYUwzlz1\n" +
            "oAl487CoPxyr4sVEAQJBANwiUOHcdGd2RoRILDzw5WOXWBoWPOKzX/K9wt0yL+mO\n" +
            "wlFNFSymqo9eLheHcEq/VD9qK9rT700dCewJfWj6+bECQQDNXmWNYIxGii5NJilT\n" +
            "OBOHiMD/F0NE178j+/kmacbhDJwpkbLYXaP8rW4+Iswrm4ORJ59lvjNuXaZ28+sx\n" +
            "fFp3AkA6Z7Bl/IO135+eATgbgx6ZadIqObQ1wbm3Qbmtzl7/7KyJvZXcnuup1icM\n" +
            "fxa//jtwB89S4+Ad6ZJ0WaA4dj5BAkEAuG7V9KmIULE388EZy8rIfyepa22Q0/qN\n" +
            "hdt8XasRGHsio5Jdc0JlSz7ViqflhCQde/aBh/XQaoVgQeO8jKyI8QJBAJHekZDj\n" +
            "WA0w1RsBVVReN1dVXgjm1CykeAT8Qx8TUmBUfiDX6w6+eGQjKtS7f4KC2IdRTV6+\n" +
            "bDzDoHBChHNC9ms=\n";

    // Content from usercert.pem without the header and footer.
    private static final String TEST_CERT =
            "MIIDEjCCAfqgAwIBAgIBATANBgkqhkiG9w0BAQsFADBFMQswCQYDVQQGEwJBVTET\n" +
            "MBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50ZXJuZXQgV2lkZ2l0cyBQ\n" +
            "dHkgTHRkMB4XDTE1MDUwMTE2NTQwNVoXDTI1MDQyODE2NTQwNVowWzELMAkGA1UE\n" +
            "BhMCQVUxEzARBgNVBAgMClNvbWUtU3RhdGUxITAfBgNVBAoMGEludGVybmV0IFdp\n" +
            "ZGdpdHMgUHR5IEx0ZDEUMBIGA1UEAwwLY2xpZW50IGNlcnQwgZ8wDQYJKoZIhvcN\n" +
            "AQEBBQADgY0AMIGJAoGBALCYprGsTU+5L3KMfhkm0gXM2xjGUH+543YLiMPGVr3e\n" +
            "VS7biue1/tQlL+fJsw3rqsPKJe71RbVWlpqUmhegxG4s3IvGYVB0KZoRIjDKmnnv\n" +
            "lx6nngL2ZJ8O27U42pHsw4z4MKlcQlWkjL3T9sV6zW2Wzri+f5mvzKjhnArbLktH\n" +
            "AgMBAAGjezB5MAkGA1UdEwQCMAAwLAYJYIZIAYb4QgENBB8WHU9wZW5TU0wgR2Vu\n" +
            "ZXJhdGVkIENlcnRpZmljYXRlMB0GA1UdDgQWBBQ8GL+jKSarvTn9fVNA2AzjY7qq\n" +
            "gjAfBgNVHSMEGDAWgBRzBBA5sNWyT/fK8GrhN3tOqO5tgjANBgkqhkiG9w0BAQsF\n" +
            "AAOCAQEAgwQEd2bktIDZZi/UOwU1jJUgGq7NiuBDPHcqgzjxhGFLQ8SQAAP3v3PR\n" +
            "mLzcfxsxnzGynqN5iHQT4rYXxxaqrp1iIdj9xl9Wl5FxjZgXITxhlRscOd/UOBvG\n" +
            "oMrazVczjjdoRIFFnjtU3Jf0Mich68HD1Z0S3o7X6sDYh6FTVR5KbLcxbk6RcoG4\n" +
            "VCI5boR5LUXgb5Ed5UxczxvN12S71fyxHYVpuuI0z0HTIbAxKeRw43I6HWOmR1/0\n" +
            "G6byGCNL/1Fz7Y+264fGqABSNTKdZwIU2K4ANEH7F+9scnhoO6OBp+gjBe5O+7jb\n" +
            "wZmUCAoTka4hmoaOCj7cqt/IkmxozQ==\n";

    private DevicePolicyManager mDpm;
    private volatile boolean mReceivedResult;
    private volatile Exception mReceivedException;
    private Semaphore mAvailableResultSemaphore;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_CERT_OPERATION_DONE.equals(intent.getAction())) {
                synchronized (DelegatedCertInstallerTest.this) {
                    mReceivedResult = intent.getBooleanExtra(EXTRA_RESULT_VALUE, false);
                    mReceivedException =
                            (Exception) intent.getSerializableExtra(EXTRA_RESULT_EXCEPTION);
                    mAvailableResultSemaphore.release();
                }
            }
        }
    };

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDpm = (DevicePolicyManager) mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mAvailableResultSemaphore = new Semaphore(0);
        mReceivedResult = false;
        mReceivedException = null;
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CERT_OPERATION_DONE);
        mContext.registerReceiver(receiver, filter);
    }

    @Override
    public void tearDown() throws Exception {
        mContext.unregisterReceiver(receiver);
        mDpm.uninstallCaCert(ADMIN_RECEIVER_COMPONENT, TEST_CA.getBytes());
        // Installed private key pair will be removed once the lockscreen password is cleared,
        // which is done in the hostside test.
        mDpm.setCertInstallerPackage(ADMIN_RECEIVER_COMPONENT, null);
        super.tearDown();
    }

    public void testCaCertsOperations() throws InterruptedException, CertificateException {
        final byte[] cert = TEST_CA.getBytes();
        final Certificate caCert = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(cert));
        final TrustedCertificateStore store = new TrustedCertificateStore();

        mDpm.setCertInstallerPackage(ADMIN_RECEIVER_COMPONENT, CERT_INSTALLER_PACKAGE);
        assertEquals(CERT_INSTALLER_PACKAGE,
                mDpm.getCertInstallerPackage(ADMIN_RECEIVER_COMPONENT));

        // Exercise installCaCert()
        assertNull(store.getCertificateAlias(caCert));
        installCaCert(cert);
        assertResult("installCaCert", true);
        assertTrue("Certificate is not installed properly", mDpm.hasCaCertInstalled(
                ADMIN_RECEIVER_COMPONENT, cert));

        // Exercise getInstalledCaCerts()
        verifyCaCert(cert);
        assertResult("getInstalledCaCerts()", true);

        // Verify that the CA cert was marked as installed by the Device Owner / Profile Owner.
        final String alias = store.getCertificateAlias(caCert);
        assertNotNull(alias);
        verifyOwnerInstalledStatus(alias, true);

        // Exercise uninstallCaCert()
        removeCaCert(cert);
        assertResult("uninstallCaCert()", true);
        assertFalse("Certificate is not removed properly", mDpm.hasCaCertInstalled(
                ADMIN_RECEIVER_COMPONENT, cert));

        // Verify that the CA cert is no longer reported as installed by the Device Owner / Profile
        // Owner.
        verifyOwnerInstalledStatus(alias, false);

        // Clear delegated cert installer.
        // Tests after this are expected to fail.
        mDpm.setCertInstallerPackage(ADMIN_RECEIVER_COMPONENT, null);

        installCaCert(cert);
        assertResult("installCaCert", false);
    }

    public void testInstallKeyPair() throws InterruptedException, KeyChainException {
        final String alias = "delegated-cert-installer-test-key";

        // Clear delegated cert installer.
        mDpm.setCertInstallerPackage(ADMIN_RECEIVER_COMPONENT, null);
        // The app is not the cert installer , it shouldn't have have privilege to call
        // installKeyPair().
        installKeyPair(TEST_KEY, TEST_CERT, alias);
        assertResult("installKeyPair", false);

        // Set the app to be cert installer.
        mDpm.setCertInstallerPackage(ADMIN_RECEIVER_COMPONENT, CERT_INSTALLER_PACKAGE);
        assertEquals(CERT_INSTALLER_PACKAGE,
                mDpm.getCertInstallerPackage(ADMIN_RECEIVER_COMPONENT));

        // Exercise installKeyPair()
        checkKeyguardPrecondition();
        installKeyPair(TEST_KEY, TEST_CERT, alias);
        assertResult("installKeyPair", true);
    }

    /**
     * If DPC is targeting N+, @{link IllegalArgumentException } should be thrown if the package
     * is missing.
     */
    public void testSetNotExistCertInstallerPackage() throws Exception {
        boolean shouldThrowException = getTargetApiLevel() >= Build.VERSION_CODES.N;
        try {
            mDpm.setCertInstallerPackage(
                    ADMIN_RECEIVER_COMPONENT, NOT_EXIST_CERT_INSTALLER_PACKAGE);
            if (shouldThrowException) {
                fail("Did not throw IllegalArgumentException");
            }
        } catch (IllegalArgumentException ex) {
            if (!shouldThrowException) {
                fail("Should not throw exception");
            }
            MoreAsserts.assertContainsRegex("is not installed on the current user",
                        ex.getMessage());
        }
        if (!shouldThrowException) {
            assertTrue("Cert install delegate was not set on uninstalled package",
                    NOT_EXIST_CERT_INSTALLER_PACKAGE.equals(
                            mDpm.getCertInstallerPackage(ADMIN_RECEIVER_COMPONENT)));
        }
    }

    /**
     * installKeyPair() requires the system to have a lockscreen password, which should have been
     * set by the host side test.
     */
    private void checkKeyguardPrecondition() throws InterruptedException {
        KeyguardManager km = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        if (!km.isKeyguardSecure()) {
            Thread.sleep(5000);
          }
          assertTrue("A lockscreen password is required before keypair can be installed",
                          km.isKeyguardSecure());
    }

    private void installCaCert(byte[] cert) {
        Intent intent = new Intent();
        intent.setAction(ACTION_INSTALL_CERT);
        intent.setComponent(CERT_INSTALLER_COMPONENT);
        intent.putExtra(EXTRA_CERT_DATA, cert);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent);
    }

    private void removeCaCert(byte[] cert) {
        Intent intent = new Intent();
        intent.setAction(ACTION_REMOVE_CERT);
        intent.setComponent(CERT_INSTALLER_COMPONENT);
        intent.putExtra(EXTRA_CERT_DATA, cert);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent);
    }

    private void verifyCaCert(byte[] cert) {
        Intent intent = new Intent();
        intent.setAction(ACTION_VERIFY_CERT);
        intent.setComponent(CERT_INSTALLER_COMPONENT);
        intent.putExtra(EXTRA_CERT_DATA, cert);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent);
    }

    private void verifyOwnerInstalledStatus(String alias, boolean expectOwnerInstalled) {
        final List<String> ownerInstalledCerts =
                mDpm.getOwnerInstalledCaCerts(Process.myUserHandle());
        assertNotNull(ownerInstalledCerts);
        assertEquals(expectOwnerInstalled, ownerInstalledCerts.contains(alias));
    }

    private void assertResult(String testName, Boolean expectSuccess) throws InterruptedException {
        assertTrue("Cert installer did not respond in time.",
                mAvailableResultSemaphore.tryAcquire(10, TimeUnit.SECONDS));
        synchronized (this) {
            if (expectSuccess) {
                assertTrue(testName + " failed unexpectedly.", mReceivedResult);
                assertNull(testName + " raised exception", mReceivedException);
            } else {
                assertFalse(testName + " succeeded unexpectedly.", mReceivedResult);
                assertTrue(testName + " did not raise SecurityException",
                        mReceivedException != null &&
                        mReceivedException instanceof SecurityException);
            }
        }
    }

    private void installKeyPair(String key, String cert, String alias) {
        Intent intent = new Intent();
        intent.setAction(ACTION_INSTALL_KEYPAIR);
        intent.setComponent(CERT_INSTALLER_COMPONENT);
        intent.putExtra(EXTRA_CERT_DATA, cert);
        intent.putExtra(EXTRA_KEY_DATA, key);
        intent.putExtra(EXTRA_KEY_ALIAS, alias);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent);
    }
}
