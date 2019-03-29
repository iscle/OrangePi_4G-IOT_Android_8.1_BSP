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

package com.android.cts.verifier.wifiaware;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Base class for Aware tests.
 */
public abstract class BaseTestActivity extends PassFailButtons.Activity implements
        BaseTestCase.Listener {
    /*
     * Handles to GUI elements.
     */
    private TextView mAwareInfo;
    private ProgressBar mAwareProgress;

    /*
     * Test case to be executed
     */
    private BaseTestCase mTestCase;

    private Handler mHandler = new Handler();

    protected abstract BaseTestCase getTestCase(Context context);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.aware_main);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        // Get UI component.
        mAwareInfo = (TextView) findViewById(R.id.aware_info);
        mAwareProgress = (ProgressBar) findViewById(R.id.aware_progress);

        // Initialize test components.
        mTestCase = getTestCase(this);

        // keep screen on while this activity is front view.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTestCase.start(this);
        mAwareProgress.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mTestCase.stop();
        mAwareProgress.setVisibility(View.GONE);
    }


    @Override
    public void onTestStarted() {
        // nop
    }

    @Override
    public void onTestMsgReceived(String msg) {
        if (msg == null) {
            return;
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mAwareInfo.append(msg);
                mAwareInfo.append("\n");
            }
        });
    }

    @Override
    public void onTestSuccess() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                getPassButton().setEnabled(true);
                mAwareProgress.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onTestFailed(String reason) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (reason != null) {
                    mAwareInfo.append(reason);
                }
                mAwareProgress.setVisibility(View.GONE);
            }
        });
    }
}
