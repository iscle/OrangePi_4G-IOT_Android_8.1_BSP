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
package com.android.cts.certinstaller;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.util.Base64InputStream;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.List;

/**
 * Delegated certificate installer app that responds to specific intents and executes various DPM
 * certificate manipulation APIs. The following APIs are exercised:
 * {@link DevicePolicyManager#installCaCert},
 * {@link DevicePolicyManager#uninstallCaCert},
 * {@link DevicePolicyManager#hasCaCertInstalled},
 * {@link DevicePolicyManager#getInstalledCaCerts},
 * {@link DevicePolicyManager#installKeyPair}.
 */
public class CertInstallerReceiver extends BroadcastReceiver {

    private static final String TAG = "DelegatedCertInstaller";
    // exercises {@link DevicePolicyManager#installCaCert} and
    // {@link DevicePolicyManager#hasCaCertInstalled},
    private static final String ACTION_INSTALL_CERT = "com.android.cts.certinstaller.install_cert";
    // exercises {@link DevicePolicyManager#uninstallCaCert} and
    // {@link DevicePolicyManager#hasCaCertInstalled},
    private static final String ACTION_REMOVE_CERT = "com.android.cts.certinstaller.remove_cert";
    // exercises {@link DevicePolicyManager#getInstalledCaCerts},
    private static final String ACTION_VERIFY_CERT = "com.android.cts.certinstaller.verify_cert";
    // exercises {@link DevicePolicyManager#installKeyPair},
    private static final String ACTION_INSTALL_KEYPAIR =
            "com.android.cts.certinstaller.install_keypair";

    private static final String ACTION_CERT_OPERATION_DONE = "com.android.cts.certinstaller.done";

    private static final String EXTRA_CERT_DATA = "extra_cert_data";
    private static final String EXTRA_KEY_DATA = "extra_key_data";
    private static final String EXTRA_KEY_ALIAS = "extra_key_alias";
    private static final String EXTRA_RESULT_VALUE = "extra_result_value";
    private static final String EXTRA_RESULT_EXCEPTION = "extra_result_exception";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);

        byte[] certBuffer;

        if (ACTION_INSTALL_CERT.equals(action)) {
            try {
                certBuffer = intent.getByteArrayExtra(EXTRA_CERT_DATA);
                // Verify cert is not currently installed.
                if (dpm.hasCaCertInstalled(null, certBuffer)) {
                    throw new RuntimeException("Cert already on device?");
                }
                if (!dpm.installCaCert(null, certBuffer)) {
                    throw new RuntimeException("installCaCert returned false.");
                }
                if (!dpm.hasCaCertInstalled(null, certBuffer)) {
                    throw new RuntimeException("Cannot find cert after installation.");
                }
                sendResult(context, true, null);
            } catch (Exception e) {
                Log.e(TAG, "Exception raised duing ACTION_INSTALL_CERT", e);
                sendResult(context, false, e);
            }
        } else if (ACTION_REMOVE_CERT.equals(action)) {
            try {
                certBuffer = intent.getByteArrayExtra(EXTRA_CERT_DATA);
                if (!dpm.hasCaCertInstalled(null, certBuffer)) {
                    throw new RuntimeException("Trying to uninstall a non-existent cert.");
                }
                dpm.uninstallCaCert(null, certBuffer);
                sendResult(context, !dpm.hasCaCertInstalled(null, certBuffer), null);
            } catch (Exception e) {
                Log.e(TAG, "Exception raised duing ACTION_REMOVE_CERT", e);
                sendResult(context, false, e);
            }
        } else if (ACTION_VERIFY_CERT.equals(action)) {
            try {
                certBuffer = intent.getByteArrayExtra(EXTRA_CERT_DATA);
                sendResult(context, containsCertificate(dpm.getInstalledCaCerts(null), certBuffer),
                        null);
            } catch (Exception e) {
                Log.e(TAG, "Exception raised duing ACTION_VERIFY_CERT", e);
                sendResult(context, false, e);
            }
        } else if (ACTION_INSTALL_KEYPAIR.equals(action)) {
            String alias = intent.getStringExtra(EXTRA_KEY_ALIAS);
            String key = intent.getStringExtra(EXTRA_KEY_DATA);
            String cert = intent.getStringExtra(EXTRA_CERT_DATA);
            try {
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(
                        Base64.decode(key, Base64.DEFAULT));
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PrivateKey privatekey = kf.generatePrivate(keySpec);

                Certificate certificate = CertificateFactory.getInstance("X.509")
                        .generateCertificate(
                                new Base64InputStream(new ByteArrayInputStream(cert.getBytes()),
                                        Base64.DEFAULT));
                // Unfortunately there is no programmatically way to check if the given private key
                // is indeed in the key store as a unprivileged app. So we just rely on
                // installKeyPair() returning true as the success criteria of this test. Separate
                // CTS keychain tests will make sure the API's behaviour is correct.
                // Note: installKeyPair() will silently return false if there is no lockscreen
                // password, however the test setup should have set one already.
                sendResult(context, dpm.installKeyPair(null, privatekey, certificate,  alias),
                        null);
            } catch (Exception e) {
                Log.e(TAG, "Exception raised duing ACTION_INSTALL_KEYPAIR", e);
                sendResult(context, false, e);
            }
        }
    }


    private void sendResult(Context context, boolean succeed, Exception e) {
        Intent intent = new Intent();
        intent.setAction(ACTION_CERT_OPERATION_DONE);
        intent.putExtra(EXTRA_RESULT_VALUE, succeed);
        if (e != null) {
            intent.putExtra(EXTRA_RESULT_EXCEPTION, e);
        }
        context.sendBroadcast(intent);
    }

    private static boolean containsCertificate(List<byte[]> certificates, byte[] toMatch)
            throws CertificateException {
        Certificate certificateToMatch = readCertificate(toMatch);
        for (byte[] certBuffer : certificates) {
            Certificate cert = readCertificate(certBuffer);
            if (certificateToMatch.equals(cert)) {
                return true;
            }
        }
        return false;
    }

    private static Certificate readCertificate(byte[] certBuffer) throws CertificateException {
        final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        return certFactory.generateCertificate(new ByteArrayInputStream(certBuffer));
    }

}
