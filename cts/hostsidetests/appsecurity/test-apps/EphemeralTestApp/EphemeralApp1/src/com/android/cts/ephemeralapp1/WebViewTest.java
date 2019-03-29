/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.ephemeralapp1;

import android.content.pm.PackageManager;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;

import android.webkit.WebView;
import android.webkit.cts.WebViewOnUiThread;

public class WebViewTest extends ActivityInstrumentationTestCase2<WebViewTestActivity> {

    private WebView mWebView;
    private WebViewOnUiThread mOnUiThread;

    public WebViewTest() {
        super("com.android.cts.ephemeralapp1", WebViewTestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!hasWebViewFeature()) {
            return;
        }
        final WebViewTestActivity activity = getActivity();
        mWebView = activity.getWebView();
        mOnUiThread = new WebViewOnUiThread(this, mWebView);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
        super.tearDown();
    }

    @UiThreadTest
    public void testWebViewLoads() throws Exception {
        // Webview is not supported on Watches
        if (!hasWebViewFeature()) {
            return;
        }
        mOnUiThread.loadUrlAndWaitForCompletion("about:blank");
    }

    private boolean hasWebViewFeature() {
        return getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WEBVIEW);
    }
}
