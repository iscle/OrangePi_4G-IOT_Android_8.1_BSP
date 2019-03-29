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
package com.android.cts.apprestrictions.targetapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserManager;

/**
 * Test activity for {@link android.app.admin.DevicePolicyManager#setApplicationRestrictions}.
 *
 * The actual test will set restrictions for this package, and the purpose of this
 * activity is to retrieve those restrictions and relay them back to the test for validation.
 */
public class ApplicationRestrictionsActivity extends Activity {

    private static final String ACTION_RESTRICTIONS_VALUE =
            "com.android.cts.apprestrictions.targetapp.RESTRICTIONS_VALUE";

    private UserManager mUserManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mUserManager = (UserManager) getSystemService(Context.USER_SERVICE);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Bundle restrictions = mUserManager.getApplicationRestrictions(getPackageName());
        sendBroadcast(new Intent(ACTION_RESTRICTIONS_VALUE)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra("value", restrictions));

        finish();
    }
}
