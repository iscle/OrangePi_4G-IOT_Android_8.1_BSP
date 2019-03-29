/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.android.cts.verifierusbcompanion;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/**
 * UI of this app
 */
public class Main extends Activity implements TestCompanion.TestObserver {
    private TextView mStatusMessage;
    private Button mDeviceTestButton;
    private Button mAccessoryTestButton;
    private Button mAbortButton;
    private TestCompanion mCurrentTest;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);

        mStatusMessage = (TextView) findViewById(R.id.status_message);
        mDeviceTestButton = (Button) findViewById(R.id.deviceTest);
        mAccessoryTestButton = (Button) findViewById(R.id.accessoryTest);
        mAbortButton = (Button) findViewById(R.id.abort);

        mStatusMessage.setText(getString(R.string.status_no_test));

        mDeviceTestButton.setOnClickListener(view -> runDeviceTest());
        mAccessoryTestButton.setOnClickListener(view -> runAccessoryTest());
        mAbortButton.setOnClickListener(view -> abortCurrentTest());
    }

    /**
     * Abort the current test companion.
     */
    private void abortCurrentTest() {
        mCurrentTest.requestAbort();
    }

    /**
     * Run the {@link DeviceTestCompanion}
     */
    private void runDeviceTest() {
        runTestCompanion(new DeviceTestCompanion(this, this));
    }

    /**
     * Run the {@link AccessoryTestCompanion}
     */
    private void runAccessoryTest() {
        runTestCompanion(new AccessoryTestCompanion(this, this));
    }

    /**
     * Run a test.
     * @param test The test to run
     */
    private void runTestCompanion(@NonNull TestCompanion test) {
        mAbortButton.setVisibility(View.VISIBLE);
        mDeviceTestButton.setVisibility(View.GONE);
        mAccessoryTestButton.setVisibility(View.GONE);

        mCurrentTest = test;
        test.start();
    }

    /**
     * Reset the UI after a test
     */
    private void resetUI() {
        mAbortButton.setVisibility(View.GONE);
        mDeviceTestButton.setVisibility(View.VISIBLE);
        mAccessoryTestButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onStatusUpdate(@NonNull CharSequence status) {
        mStatusMessage.setText(status);
    }

    @Override
    public void onSuccess() {
        mStatusMessage.setText(getString(R.string.status_finished));
        resetUI();
    }

    @Override
    public void onFail(@NonNull CharSequence error) {
        mStatusMessage.setText(error);
        resetUI();
    }

    @Override
    public void onAbort() {
        mStatusMessage.setText(getString(R.string.status_abort));
        resetUI();
    }
}
