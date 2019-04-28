/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.certinstaller;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserManager;
import android.preference.PreferenceActivity;
import android.provider.DocumentsContract;
import android.security.Credentials;
import android.security.KeyChain;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import libcore.io.IoUtils;
import libcore.io.Streams;

/**
 * The main class for installing certificates to the system keystore. It reacts
 * to the public {@link Credentials#INSTALL_ACTION} intent.
 */
public class CertInstallerMain extends PreferenceActivity {
    private static final String TAG = "CertInstaller";

    private static final int REQUEST_INSTALL = 1;
    private static final int REQUEST_OPEN_DOCUMENT = 2;

    private static final String INSTALL_CERT_AS_USER_CLASS = ".InstallCertAsUser";

    public static final String WIFI_CONFIG = "wifi-config";
    public static final String WIFI_CONFIG_DATA = "wifi-config-data";
    public static final String WIFI_CONFIG_FILE = "wifi-config-file";

    private static Map<String,String> MIME_MAPPINGS = new HashMap<>();

    static {
            MIME_MAPPINGS.put("application/x-x509-ca-cert", KeyChain.EXTRA_CERTIFICATE);
            MIME_MAPPINGS.put("application/x-x509-user-cert", KeyChain.EXTRA_CERTIFICATE);
            MIME_MAPPINGS.put("application/x-x509-server-cert", KeyChain.EXTRA_CERTIFICATE);
            MIME_MAPPINGS.put("application/x-pem-file", KeyChain.EXTRA_CERTIFICATE);
            MIME_MAPPINGS.put("application/pkix-cert", KeyChain.EXTRA_CERTIFICATE);
            MIME_MAPPINGS.put("application/x-pkcs12", KeyChain.EXTRA_PKCS12);
            MIME_MAPPINGS.put("application/x-wifi-config", WIFI_CONFIG);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED);

        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        if (userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_CREDENTIALS)) {
            finish();
            return;
        }

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (Credentials.INSTALL_ACTION.equals(action)
                || Credentials.INSTALL_AS_USER_ACTION.equals(action)) {
            Bundle bundle = intent.getExtras();

            /*
             * There is a special INSTALL_AS_USER action that this activity is
             * aliased to, but you have to have a permission to call it. If the
             * caller got here any other way, remove the extra that we allow in
             * that INSTALL_AS_USER path.
             */
            String calledClass = intent.getComponent().getClassName();
            String installAsUserClassName = getPackageName() + INSTALL_CERT_AS_USER_CLASS;
            if (bundle != null && !installAsUserClassName.equals(calledClass)) {
                bundle.remove(Credentials.EXTRA_INSTALL_AS_UID);
            }

            // If bundle is empty of any actual credentials, ask user to open.
            // Otherwise, pass extras to CertInstaller to install those credentials.
            // Either way, we use KeyChain.EXTRA_NAME as the default name if available.
            if (bundle == null
                    || bundle.isEmpty()
                    || (bundle.size() == 1
                        && (bundle.containsKey(KeyChain.EXTRA_NAME)
                            || bundle.containsKey(Credentials.EXTRA_INSTALL_AS_UID)))) {
                final String[] mimeTypes = MIME_MAPPINGS.keySet().toArray(new String[0]);
                final Intent openIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                openIntent.setType("*/*");
                openIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                openIntent.putExtra(DocumentsContract.EXTRA_SHOW_ADVANCED, true);
                startActivityForResult(openIntent, REQUEST_OPEN_DOCUMENT);
            } else {
                final Intent installIntent = new Intent(this, CertInstaller.class);
                installIntent.putExtras(intent);
                startActivityForResult(installIntent, REQUEST_INSTALL);
            }
        } else if (Intent.ACTION_VIEW.equals(action)) {
            startInstallActivity(intent.getType(), intent.getData());
        }
    }

    private void startInstallActivity(String mimeType, Uri uri) {
        if (mimeType == null) {
            mimeType = getContentResolver().getType(uri);
        }

        String target = MIME_MAPPINGS.get(mimeType);
        if (target == null) {
            throw new IllegalArgumentException("Unknown MIME type: " + mimeType);
        }

        if (WIFI_CONFIG.equals(target)) {
            startWifiInstallActivity(mimeType, uri);
        }
        else {
            InputStream in = null;
            try {
                in = getContentResolver().openInputStream(uri);

                final byte[] raw = Streams.readFully(in);
                startInstallActivity(target, raw);

            } catch (IOException e) {
                Log.e(TAG, "Failed to read certificate: " + e);
                Toast.makeText(this, R.string.cert_read_error, Toast.LENGTH_LONG).show();
            } finally {
                IoUtils.closeQuietly(in);
            }
        }
    }

    private void startInstallActivity(String target, byte[] value) {
        Intent intent = new Intent(this, CertInstaller.class);
        intent.putExtra(target, value);

        startActivityForResult(intent, REQUEST_INSTALL);
    }

    private void startWifiInstallActivity(String mimeType, Uri uri) {
        Intent intent = new Intent(this, WiFiInstaller.class);
        try (BufferedInputStream in =
                     new BufferedInputStream(getContentResolver().openInputStream(uri))) {
            byte[] data = Streams.readFully(in);
            intent.putExtra(WIFI_CONFIG_FILE, uri.toString());
            intent.putExtra(WIFI_CONFIG_DATA, data);
            intent.putExtra(WIFI_CONFIG, mimeType);
            startActivityForResult(intent, REQUEST_INSTALL);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read wifi config: " + e);
            Toast.makeText(this, R.string.cert_read_error, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OPEN_DOCUMENT) {
            if (resultCode == RESULT_OK) {
                startInstallActivity(null, data.getData());
            } else {
                finish();
            }
        } else if (requestCode == REQUEST_INSTALL) {
            setResult(resultCode);
            finish();
        } else {
            Log.w(TAG, "unknown request code: " + requestCode);
        }
    }
}
