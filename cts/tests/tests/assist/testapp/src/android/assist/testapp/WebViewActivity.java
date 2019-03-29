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

package android.assist.testapp;

import android.app.Activity;
import android.assist.common.MyWebView;
import android.assist.common.Utils;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class WebViewActivity extends Activity {
    static final String TAG = "WebViewActivity";

    private String mTestCaseName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "TestApp created");
        mTestCaseName = getIntent().getStringExtra(Utils.TESTCASE_TYPE);
        setContentView(R.layout.webview);
        MyWebView webview = (MyWebView) findViewById(R.id.webview);
        webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url){
                sendBroadcast(new Intent(Utils.APP_3P_HASRESUMED));
            }
        });
        webview.myLoadData(Utils.WEBVIEW_HTML_URL, Utils.WEBVIEW_HTML, "text/html", "UTF-8");
        webview.setLocaleList(Utils.WEBVIEW_LOCALE_LIST);
        //webview.loadUrl(
        //        "https://android-developers.blogspot.com/2015/08/m-developer-preview-3-final-sdk.html");
    }
}
