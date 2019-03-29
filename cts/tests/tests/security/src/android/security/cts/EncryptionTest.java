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

package android.security.cts;

import com.android.compatibility.common.util.PropertyUtil;

import android.platform.test.annotations.SecurityTest;
import android.test.AndroidTestCase;
import junit.framework.TestCase;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SecurityTest
public class EncryptionTest extends AndroidTestCase {

    static {
        System.loadLibrary("ctssecurity_jni");
    }

    private static final int MIN_API_LEVEL = 23;

    private static final String TAG = "EncryptionTest";

    private static native boolean deviceIsEncrypted();

    private static native boolean aesIsFast();

    private boolean hasLowRAM() {
        ActivityManager activityManager =
            (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);

        return activityManager.isLowRamDevice();
    }

    private boolean isRequired() {
        // Optional before MIN_API_LEVEL or if the device has low RAM
        return PropertyUtil.getFirstApiLevel() >= MIN_API_LEVEL && !hasLowRAM();
    }

    public void testEncryption() throws Exception {
        if (!isRequired() || deviceIsEncrypted()) {
            return;
        }

        // Required if performance is sufficient
        assertFalse("Device encryption is required", aesIsFast());
    }
}
