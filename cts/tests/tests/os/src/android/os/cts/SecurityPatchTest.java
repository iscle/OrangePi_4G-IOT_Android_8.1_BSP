/*
 * Copyright (C) 2016 Google Inc.
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

package android.os.cts;

import com.android.compatibility.common.util.ApiLevelUtil;

import android.os.Build;
import android.os.SystemProperties;
import android.test.InstrumentationTestCase;
import android.util.Log;

/**
 * Tests for Security Patch String settings
 */
public class SecurityPatchTest extends InstrumentationTestCase {

    private static final String TAG = SecurityPatchTest.class.getSimpleName();
    private static final String SECURITY_PATCH_ERROR =
            "ro.build.version.security_patch should be in the format \"YYYY-MM-DD\". Found \"%s\"";
    private static final String SECURITY_PATCH_DATE_ERROR =
            "ro.build.version.security_patch should be \"%d-%02d\" or later. Found \"%s\"";
    private static final int SECURITY_PATCH_YEAR = 2016;
    private static final int SECURITY_PATCH_MONTH = 12;

    private boolean mSkipTests = false;

    @Override
    protected void setUp() {
        mSkipTests = (ApiLevelUtil.isBefore(Build.VERSION_CODES.M));
    }

    /** Security patch string must exist in M or higher **/
    public void testSecurityPatchFound() {
        if (mSkipTests) {
            Log.w(TAG, "Skipping M+ Test.");
            return;
        }

        String buildSecurityPatch = Build.VERSION.SECURITY_PATCH;
        String error = String.format(SECURITY_PATCH_ERROR, buildSecurityPatch);
        assertTrue(error, !buildSecurityPatch.isEmpty());
    }

    /** Security patch should be of the form YYYY-MM-DD in M or higher */
    public void testSecurityPatchFormat() {
        if (mSkipTests) {
            Log.w(TAG, "Skipping M+ Test.");
            return;
        }

        String buildSecurityPatch = Build.VERSION.SECURITY_PATCH;
        String error = String.format(SECURITY_PATCH_ERROR, buildSecurityPatch);

        assertEquals(error, 10, buildSecurityPatch.length());
        assertTrue(error, Character.isDigit(buildSecurityPatch.charAt(0)));
        assertTrue(error, Character.isDigit(buildSecurityPatch.charAt(1)));
        assertTrue(error, Character.isDigit(buildSecurityPatch.charAt(2)));
        assertTrue(error, Character.isDigit(buildSecurityPatch.charAt(3)));
        assertEquals(error, '-', buildSecurityPatch.charAt(4));
        assertTrue(error, Character.isDigit(buildSecurityPatch.charAt(5)));
        assertTrue(error, Character.isDigit(buildSecurityPatch.charAt(6)));
        assertEquals(error, '-', buildSecurityPatch.charAt(7));
        assertTrue(error, Character.isDigit(buildSecurityPatch.charAt(8)));
        assertTrue(error, Character.isDigit(buildSecurityPatch.charAt(9)));
    }

    /** Security patch should no older than the month this test was updated in M or higher **/
    public void testSecurityPatchDate() {
        if (mSkipTests) {
            Log.w(TAG, "Skipping M+ Test.");
            return;
        }

        String buildSecurityPatch = Build.VERSION.SECURITY_PATCH;
        String error = String.format(SECURITY_PATCH_DATE_ERROR,
                                     SECURITY_PATCH_YEAR,
                                     SECURITY_PATCH_MONTH,
                                     buildSecurityPatch);

        int declaredYear = 0;
        int declaredMonth = 0;

        try {
            declaredYear = Integer.parseInt(buildSecurityPatch.substring(0,4));
            declaredMonth = Integer.parseInt(buildSecurityPatch.substring(5,7));
        } catch (Exception e) {
            assertTrue(error, false);
        }

        assertTrue(error, declaredYear >= SECURITY_PATCH_YEAR);
        assertTrue(error, (declaredYear > SECURITY_PATCH_YEAR) ||
                          (declaredMonth >= SECURITY_PATCH_MONTH));
    }
}
