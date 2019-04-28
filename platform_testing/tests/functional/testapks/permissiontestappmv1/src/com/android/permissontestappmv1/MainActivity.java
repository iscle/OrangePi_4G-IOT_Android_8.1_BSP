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

package com.android.permissiontestappmv1;

import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.view.View;
import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.EditText;
import android.widget.Toast;
import android.view.inputmethod.InputMethodManager;
import android.content.ContentResolver;
import android.Manifest;
import android.app.Activity;

public class MainActivity extends Activity {

    private static final int READ_PERMISSION_RESULT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Button buttonGetPermission = (Button) findViewById(R.id.buttonGetPermission);
        buttonGetPermission.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showContacts();
            }
        });
    }

    /**
      * This method asks for 'READ_CONTACTS' permission on button 'Get Contact Permission' press
      * Method does nothing with Contact content
      */
    private void showContacts() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_CONTACTS)) {
            }
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.READ_CONTACTS
            }, READ_PERMISSION_RESULT);
            return;
        }
    }
}
