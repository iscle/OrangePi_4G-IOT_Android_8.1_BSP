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

package com.android.cts.normalapp;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.android.cts.util.TestResult;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class NormalWebActivity extends Activity {
    private static final String ACTION_START_ACTIVITY =
            "com.android.cts.ephemeraltest.START_ACTIVITY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean canAccessInstantApp = false;
        String exception = null;
        try {
            canAccessInstantApp = tryAccessingInstantApp();
        } catch (Throwable t) {
            exception = t.getClass().getName();
        }

        TestResult.getBuilder()
                .setPackageName("com.android.cts.normalapp")
                .setComponentName("NormalWebActivity")
                .setStatus("PASS")
                .setException(exception)
                .setEphemeralPackageInfoExposed(canAccessInstantApp)
                .build()
                .broadcast(this);
        finish();
    }

    private boolean tryAccessingInstantApp() {
        try {
            final PackageInfo info = getPackageManager()
                    .getPackageInfo("com.android.cts.ephemeralapp1", 0 /*flags*/);
            return (info != null);
        } catch (Throwable t) {
            return false;
        }
    }
}
