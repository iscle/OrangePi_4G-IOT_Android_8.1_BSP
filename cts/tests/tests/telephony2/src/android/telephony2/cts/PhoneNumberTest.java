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

package android.telephony2.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.telephony.TelephonyManager;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class PhoneNumberTest extends InstrumentationTestCase {
    private static final String TAG = "PhoneNumberTest";

    private void setDefaultSmsApp(boolean setToSmsApp) {
        StringBuilder command = new StringBuilder();
        command.append("appops set ");
        command.append(getInstrumentation().getContext().getPackageName());
        command.append(" WRITE_SMS ");
        command.append(setToSmsApp ? "allow" : "default");

        ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation()
                .executeShellCommand(command.toString());

        InputStream is = new FileInputStream(pfd.getFileDescriptor());
        try {
            final byte[] buffer = new byte[8192];
            while ((is.read(buffer)) != -1);
        } catch (IOException e) {
            Log.e(TAG, "Error managing default SMS app", e);
        }
    }

    public void testGetLine1Number() {
        Context context = getInstrumentation().getContext();
        PackageManager packageManager = context.getPackageManager();
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        TelephonyManager tm = context.getSystemService(TelephonyManager.class);

        setDefaultSmsApp(true);

        // This shouldn't crash.
        tm.getLine1Number();

        setDefaultSmsApp(false);

        // This should throw now.
        try {
            tm.getLine1Number();
            fail("Should throw SecurityException");
        } catch (SecurityException expected) {
        }
    }
}
