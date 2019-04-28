/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.stream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import com.android.car.stream.notifications.StreamNotificationListenerService;

import java.util.ArrayList;
import java.util.List;

/**
 * A trampoline activity that checks if all permissions necessary are granted.
 */
public class PermissionsActivity extends Activity {
    private static final String TAG = "PermissionsActivity";
    private static final String NOTIFICATION_LISTENER_ENABLED = "enabled_notification_listeners";

    public static final int CAR_PERMISSION_REQUEST_CODE = 1013; // choose a unique number

    private static final String[] PERMISSIONS = new String[]{
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean permissionsCheckOnly = getIntent().getExtras()
                .getBoolean(StreamConstants.STREAM_PERMISSION_CHECK_PERMISSIONS_ONLY);

        if (permissionsCheckOnly) {
            boolean allPermissionsGranted = hasNotificationListenerPermission()
                    && arePermissionGranted(PERMISSIONS);
            setResult(allPermissionsGranted ? RESULT_OK : RESULT_CANCELED);
            finish();
            return;
        }

        if (!hasNotificationListenerPermission()) {
            showNotificationListenerSettings();
        } else {
            maybeRequestPermissions();
        }
    }

    private void maybeRequestPermissions() {
        boolean permissionGranted = arePermissionGranted(PERMISSIONS);
        if (!permissionGranted) {
            requestPermissions(PERMISSIONS, CAR_PERMISSION_REQUEST_CODE);
        } else {
            startService(new Intent(this, StreamService.class));
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAR_PERMISSION_REQUEST_CODE) {
            List<String> granted = new ArrayList<>();
            List<String> notGranted = new ArrayList<>();
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int grantResult = grantResults[i];
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    granted.add(permission);
                } else {
                    notGranted.add(permission);
                }
            }

            if (notGranted.size() > 0) {
                StringBuilder stb = new StringBuilder();
                for (String s : notGranted) {
                    stb.append(" ").append(s);
                }
                showDialog(getString(R.string.permission_not_granted, stb.toString()));
            } else {
                showDialog(getString(R.string.all_permission_granted));
                startService(new Intent(this, StreamService.class));
            }

            if (arePermissionGranted(PERMISSIONS)) {
                setResult(Activity.RESULT_OK);
            }
            finish();
        }
    }

    private void showDialog(String message) {
        new AlertDialog.Builder(this /* context */)
                .setTitle(getString(R.string.permission_dialog_title))
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(
                        getString(R.string.permission_dialog_positive_button_text),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        })
                .show();
    }

    private boolean arePermissionGranted(String[] permissions) {
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Permission is not granted: " + permission);
                return false;
            }
        }
        return true;
    }

    private boolean hasNotificationListenerPermission() {
        ComponentName notificationListener = new ComponentName(this,
                StreamNotificationListenerService.class);
        String listeners = Settings.Secure.getString(getContentResolver(),
                NOTIFICATION_LISTENER_ENABLED);
        return listeners != null && listeners.contains(notificationListener.flattenToString());
    }

    private void showNotificationListenerSettings() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.car_notification_permission_dialog_title))
                .setMessage(getString(R.string.car_notification_permission_dialog_text))
                .setCancelable(false)
                .setNeutralButton(getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                Intent settingsIntent =
                                        new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                                startActivity(settingsIntent);
                            }
                        })
                .create();
        dialog.show();
    }
}
