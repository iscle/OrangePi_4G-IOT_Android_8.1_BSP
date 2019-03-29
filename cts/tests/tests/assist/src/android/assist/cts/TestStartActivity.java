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

package android.assist.cts;

import android.app.Activity;
import android.assist.common.Utils;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ScrollView;
import android.widget.TextView;

public class TestStartActivity extends Activity {
    static final String TAG = "TestStartActivity";

    private ScrollView mScrollView;
    private TextView mTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, " in onCreate");
        // Set the respective view we want compared with the test activity
        String testName = getIntent().getStringExtra(Utils.TESTCASE_TYPE);
        switch (testName) {
            case Utils.ASSIST_STRUCTURE:
                setContentView(R.layout.test_app);
                setTitle(R.string.testAppTitle);
                return;
            case Utils.TEXTVIEW:
                setContentView(R.layout.text_view);
                mTextView =  (TextView) findViewById(R.id.text_view);
                mScrollView = (ScrollView) findViewById(R.id.scroll_view);
                setTitle(R.string.textViewActivityTitle);
                return;
            case Utils.LARGE_VIEW_HIERARCHY:
                setContentView(R.layout.multiple_text_views);
                setTitle(R.string.testAppTitle);
                return;
            case Utils.WEBVIEW:
                if (getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_WEBVIEW)) {
                    setContentView(R.layout.webview);
                    setTitle(R.string.webViewActivityTitle);
                    WebView webview = (WebView) findViewById(R.id.webview);
                    webview.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            sendBroadcast(new Intent(Utils.TEST_ACTIVITY_LOADED));
                        }
                    });
                    webview.loadData(Utils.WEBVIEW_HTML, "text/html", "UTF-8");
                }
                return;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, " in onResume");
    }

    public void startTest(String testCaseName) {
        Log.i(TAG, "Starting test activity for TestCaseType = " + testCaseName);
        Intent intent = new Intent();
        intent.putExtra(Utils.TESTCASE_TYPE, testCaseName);
        intent.setAction("android.intent.action.START_TEST_" + testCaseName);
        intent.setComponent(new ComponentName("android.assist.service",
                "android.assist." + Utils.getTestActivity(testCaseName)));
        startActivity(intent);
    }

    public void start3pApp(String testCaseName) {
        Intent intent = new Intent();
        intent.putExtra(Utils.TESTCASE_TYPE, testCaseName);
        intent.setAction("android.intent.action.TEST_APP_" + testCaseName);
        intent.setComponent(Utils.getTestAppComponent(testCaseName));
        startActivity(intent);
    }

    public void start3pAppWithColor(String testCaseName, int color) {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.TEST_APP_" + testCaseName);
        intent.putExtra(Utils.SCREENSHOT_COLOR_KEY, color);
        intent.setComponent(Utils.getTestAppComponent(testCaseName));
        startActivity(intent);
    }

    @Override
    protected void onPause() {
        Log.i(TAG, " in onPause");
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, " in onStart");
    }

    @Override protected void onRestart() {
        super.onRestart();
        Log.i(TAG, " in onRestart");
    }

    @Override
    protected void onStop() {
        Log.i(TAG, " in onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, " in onDestroy");
        super.onDestroy();
    }

    public void scrollText(int scrollX, int scrollY, boolean scrollTextView,
            boolean scrollScrollView) {
        if (scrollTextView) {
            if (scrollX < 0 || scrollY < 0) {
                scrollX = mTextView.getWidth();
                scrollY = mTextView.getLayout().getLineTop(mTextView.getLineCount()) - mTextView.getHeight();
            }
            Log.i(TAG, "Scrolling text view to " + scrollX + ", " + scrollY);
            mTextView.scrollTo(scrollX, scrollY);
        } else if (scrollScrollView) {
            if (scrollX < 0 || scrollY < 0) {
                Log.i(TAG, "Scrolling scroll view to bottom right");
                mScrollView.fullScroll(View.FOCUS_DOWN);
                mScrollView.fullScroll(View.FOCUS_RIGHT);
            } else {
                Log.i(TAG, "Scrolling scroll view to " + scrollX + ", " + scrollY);
                mScrollView.scrollTo(scrollX, scrollY);
            }
        }
    }
}
