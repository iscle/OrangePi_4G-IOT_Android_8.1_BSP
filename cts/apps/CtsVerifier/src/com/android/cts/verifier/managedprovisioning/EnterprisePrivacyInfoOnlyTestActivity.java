/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.verifier.managedprovisioning;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * A test Activity that has an info text and pass/fail buttons, nothing else.
 */
public class EnterprisePrivacyInfoOnlyTestActivity extends PassFailButtons.Activity {
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_INFO = "info";

    private String mTestId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.enterprise_privacy_negative_test);
        setPassFailButtonClickListeners();

        final Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_ID)
                || !intent.hasExtra(EXTRA_TITLE)
                || !intent.hasExtra(EXTRA_INFO)) {
            throw new IllegalArgumentException(
                    "Intent must have EXTRA_ID, EXTRA_TITLE & EXTRA_INFO");
        }

        mTestId = intent.getStringExtra(EXTRA_ID);
        setTitle(intent.getIntExtra(EXTRA_TITLE, -1));

        final TextView info = (TextView) findViewById(R.id.info);
        info.setText(intent.getIntExtra(EXTRA_INFO, -1));
    }

    @Override
    protected void onResume() {
        super.onResume();
        setResult(RESULT_CANCELED);
    }

    @Override
    public String getTestId() {
        return mTestId;
    }
}
