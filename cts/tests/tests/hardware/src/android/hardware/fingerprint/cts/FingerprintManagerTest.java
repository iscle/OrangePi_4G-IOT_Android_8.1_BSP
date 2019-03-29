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

package android.hardware.fingerprint.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintManager.AuthenticationCallback;
import android.os.CancellationSignal;
import android.platform.test.annotations.Presubmit;
import android.test.AndroidTestCase;

/**
 * Basic test cases for FingerprintManager
 */
public class FingerprintManagerTest extends AndroidTestCase {
    private enum AuthState {
        AUTH_UNKNOWN, AUTH_ERROR, AUTH_FAILED, AUTH_SUCCEEDED
    }
    private AuthState mAuthState = AuthState.AUTH_UNKNOWN;
    private FingerprintManager mFingerprintManager;

    boolean mHasFingerprintManager;
    private AuthenticationCallback mAuthCallback = new AuthenticationCallback() {

        @Override
        public void onAuthenticationError(int errorCode, CharSequence errString) {
        }

        @Override
        public void onAuthenticationFailed() {
            mAuthState = AuthState.AUTH_SUCCEEDED;
        }

        @Override
        public void onAuthenticationSucceeded(
                android.hardware.fingerprint.FingerprintManager.AuthenticationResult result) {
            mAuthState = AuthState.AUTH_SUCCEEDED;
        }
    };

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mAuthState = AuthState.AUTH_UNKNOWN;

        PackageManager pm = getContext().getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            mHasFingerprintManager = true;
            mFingerprintManager = (FingerprintManager)
                    getContext().getSystemService(Context.FINGERPRINT_SERVICE);
        }
    }

    @Presubmit
    public void test_hasFingerprintHardware() {
        if (!mHasFingerprintManager) {
            return; // skip test if no fingerprint feature
        }
        assertTrue(mFingerprintManager.isHardwareDetected());
    }

    public void test_hasEnrolledFingerprints() {
        if (!mHasFingerprintManager) {
            return; // skip test if no fingerprint feature
        }
        boolean hasEnrolledFingerprints = mFingerprintManager.hasEnrolledFingerprints();
        assertTrue(!hasEnrolledFingerprints);
    }

    public void test_authenticateNullCallback() {
        if (!mHasFingerprintManager) {
            return; // skip test if no fingerprint feature
        }
        boolean exceptionTaken = false;
        CancellationSignal cancelAuth = new CancellationSignal();
        try {
            mFingerprintManager.authenticate(null, cancelAuth, 0, null, null);
        } catch (IllegalArgumentException e) {
            exceptionTaken = true;
        } finally {
            assertTrue(mAuthState == AuthState.AUTH_UNKNOWN);
            assertTrue(exceptionTaken);
            cancelAuth.cancel();
        }
    }

    public void test_authenticate() {
        if (!mHasFingerprintManager) {
            return; // skip test if no fingerprint feature
        }
        boolean exceptionTaken = false;
        CancellationSignal cancelAuth = new CancellationSignal();
        try {
            mFingerprintManager.authenticate(null, cancelAuth, 0, mAuthCallback, null);
        } catch (IllegalArgumentException e) {
            exceptionTaken = true;
        } finally {
            assertFalse(exceptionTaken);
            // We should never get out of this state without user interaction
            assertTrue(mAuthState == AuthState.AUTH_UNKNOWN);
            cancelAuth.cancel();
        }
    }
}
