/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.text.cts;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

import com.android.compatibility.common.util.NullWebViewUtils;

public class EmojiCtsActivity extends Activity {
    private WebView mWebView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.webview_layout);
            mWebView = (WebView) findViewById(R.id.web_page);
        } catch (Exception e) {
            NullWebViewUtils.determineIfWebViewAvailable(this, e);
        }
    }

    public WebView getWebView() {
        return mWebView;
    }

    @Override
    public void onDestroy() {
        if (mWebView != null) {
            mWebView.destroy();
        }
        super.onDestroy();
    }
}
