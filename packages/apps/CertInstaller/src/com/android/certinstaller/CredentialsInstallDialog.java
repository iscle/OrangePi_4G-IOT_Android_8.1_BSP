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

package com.android.certinstaller;

import android.app.AlertDialog;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.os.Bundle;
import android.content.DialogInterface;
import android.widget.TextView;

public class CredentialsInstallDialog extends Activity {
    private static final String NETWORK_NAME = "network_name";
    private static final String INSTALL_STATE = "install_state";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayDialog();
    }

    public void displayDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View layout = getLayoutInflater().inflate(R.layout.credentials_installed_dialog, null);
        builder.setView(layout);
        Bundle bundle = getIntent().getExtras();
        int installState = getIntent().getIntExtra(INSTALL_STATE, 0);
        TextView text = (TextView) layout.findViewById(R.id.credential_installed_content);
        if (installState == WiFiInstaller.INSTALL_SUCCESS) {
            String networkName = bundle.getString(NETWORK_NAME);
            text.setText(String.format(getResources().getString(R.string.install_done), networkName));
            builder.setTitle(getResources().getString(R.string.install_done_title));
        } else if (installState == WiFiInstaller.INSTALL_FAIL){
            text.setText(getResources().getString(R.string.wifi_installer_fail));
            builder.setTitle(R.string.wifi_installer_fail_title);
        } else if (installState == WiFiInstaller.INSTALL_FAIL_NO_WIFI) {
            text.setText(getResources().getString(R.string.wifi_installer_fail_no_wifi));
            builder.setTitle(R.string.wifi_installer_fail_no_wifi_title);
        }
        builder.setPositiveButton(R.string.done_label, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });
        builder.create().show();
    }
}
