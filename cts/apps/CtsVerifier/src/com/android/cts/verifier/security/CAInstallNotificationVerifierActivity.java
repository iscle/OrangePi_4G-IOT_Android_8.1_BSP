/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.cts.verifier.security;

import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.security.KeyChain;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.android.cts.verifier.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.TestListActivity;
import com.android.cts.verifier.TestListAdapter.TestListItem;
import com.android.cts.verifier.TestResult;

public class CAInstallNotificationVerifierActivity extends DialogTestListActivity {
    static final String TAG = CAInstallNotificationVerifierActivity.class.getSimpleName();

    private static final String CERT_ASSET_NAME = "myCA.cer";

    // From @hidden field in android.provider.Settings
    private static final String ACTION_TRUSTED_CREDENTIALS_USER
            = "com.android.settings.TRUSTED_CREDENTIALS_USER";

    public CAInstallNotificationVerifierActivity() {
        super(R.layout.cainstallnotify_main, R.string.cacert_test, R.string.cacert_info,
                R.string.cacert_info);
    }

    @Override
    protected void setupTests(final ArrayTestListAdapter testAdapter) {
        testAdapter.add(new InstallCertItem(this,
                R.string.cacert_install_cert,
                "install_cert",
                KeyChain.createInstallIntent()));
        testAdapter.add(new DialogTestListItem(this,
                R.string.cacert_check_cert_in_settings,
                "check_cert",
                R.string.cacert_check_cert_in_settings,
                new Intent(ACTION_TRUSTED_CREDENTIALS_USER)));
        testAdapter.add(new DialogTestListItem(this,
                R.string.cacert_remove_screen_lock,
                "remove_screen_lock",
                R.string.cacert_remove_screen_lock,
                new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD)));
        testAdapter.add(new DialogTestListItem(this,
                R.string.cacert_check_notification,
                "check_notification") {
                    @Override
                    public void performTest(DialogTestListActivity activity) {
                        setTestResult(this, TestResult.TEST_RESULT_PASSED);
                    }
                });
        testAdapter.add(new DialogTestListItem(this,
                R.string.cacert_dismiss_notification,
                "dismiss_notification") {
                    @Override
                    public void performTest(DialogTestListActivity activity) {
                        setTestResult(this, TestResult.TEST_RESULT_PASSED);
                    }
                });
    }

    private class InstallCertItem extends DialogTestListItem {
        public InstallCertItem(Context context, int nameResId, String testId, Intent intent) {
            super(context, nameResId, testId, nameResId, intent);
        }

        @Override
        public void performTest(DialogTestListActivity activity) {
            final File certStagingFile = new File("/sdcard/", CERT_ASSET_NAME);
            InputStream is = null;
            FileOutputStream os = null;
            try {
                try {
                    is = getAssets().open(CERT_ASSET_NAME);
                    os = new FileOutputStream(certStagingFile);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) > 0) {
                        os.write(buffer, 0, length);
                    }
                } finally {
                    if (is != null) is.close();
                    if (os != null) os.close();
                    certStagingFile.setReadable(true, false);
                }
            } catch (IOException ioe) {
                Log.w(TAG, "Problem moving cert file to /sdcard/", ioe);
                return;
            }
            super.performTest(activity);
        }
    }
}
