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
import android.assist.common.Utils;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

public class TestApp extends Activity {
    static final String TAG = "TestApp";

    private String mTestCaseName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "TestApp created");
        mTestCaseName = getIntent().getStringExtra(Utils.TESTCASE_TYPE);
        switch (mTestCaseName) {
            case Utils.LARGE_VIEW_HIERARCHY:
                setContentView(R.layout.multiple_text_views);
                return;
            default:
                setContentView(R.layout.test_app);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "TestApp has resumed");
        final View layout = findViewById(android.R.id.content);
        ViewTreeObserver vto = layout.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                layout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                sendBroadcast(new Intent(Utils.APP_3P_HASRESUMED));
            }
        });
    }

    public void onEnterAnimationComplete() {
        Log.i(TAG, "TestApp onEnterAnimationComplete ");
        sendBroadcast(new Intent(Utils.APP_3P_HASDRAWED));
    }

}