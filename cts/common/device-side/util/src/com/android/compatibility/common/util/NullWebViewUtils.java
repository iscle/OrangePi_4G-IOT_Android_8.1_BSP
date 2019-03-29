/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.compatibility.common.util;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Utilities to enable the android.webkit.* CTS tests (and others that rely on a functioning
 * android.webkit.WebView implementation) to determine whether a functioning WebView is present
 * on the device or not.
 *
 * Test cases that require android.webkit.* classes should wrap their first usage of WebView in a
 * try catch block, and pass any exception that is thrown to
 * NullWebViewUtils.determineIfWebViewAvailable. The return value of
 * NullWebViewUtils.isWebViewAvailable will then determine if the test should expect to be able to
 * use a WebView.
 */
public class NullWebViewUtils {

    private static boolean sWebViewUnavailable;

    /**
     * @param context Current Activity context, used to query the PackageManager.
     * @param t       An exception thrown by trying to invoke android.webkit.* APIs.
     */
    public static void determineIfWebViewAvailable(Context context, Throwable t) {
        sWebViewUnavailable = !hasWebViewFeature(context) && checkCauseWasUnsupportedOperation(t);
    }

    /**
     * After calling determineIfWebViewAvailable, this returns whether a WebView is available on the
     * device and wheter the test can rely on it.
     * @return True iff. PackageManager determined that there is no WebView on the device and the
     *         exception thrown from android.webkit.* was UnsupportedOperationException.
     */
    public static boolean isWebViewAvailable() {
        return !sWebViewUnavailable;
    }

    private static boolean hasWebViewFeature(Context context) {
        // Query the system property that determins if there is a functional WebView on the device.
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_WEBVIEW);
    }

    private static boolean checkCauseWasUnsupportedOperation(Throwable t) {
        if (t == null) return false;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t instanceof UnsupportedOperationException;
    }

    /**
     * Some CTS tests (by design) first use android.webkit.* from a background thread. This helper
     * allows the test to catch the UnsupportedOperationException from that background thread, and
     * then query the result from the test main thread.
     */
    public static class NullWebViewFromThreadExceptionHandler
            implements Thread.UncaughtExceptionHandler {
        private Throwable mPendingException;

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            mPendingException = e;
        }

        public boolean isWebViewAvailable(Context context) {
            return hasWebViewFeature(context) ||
                    !checkCauseWasUnsupportedOperation(mPendingException);
        }
    }
}
