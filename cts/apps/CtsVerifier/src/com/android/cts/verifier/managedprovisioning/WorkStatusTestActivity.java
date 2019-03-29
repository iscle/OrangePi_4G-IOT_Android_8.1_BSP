/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.android.cts.verifier.R;

/**
 * Test activity for work status tests.
 */
public class WorkStatusTestActivity extends Activity {
    public static final String ACTION_WORK_STATUS_ICON
            = "com.android.cts.verifier.managedprovisioning.WORK_STATUS_ICON";
    public static final String ACTION_WORK_STATUS_TOAST
            = "com.android.cts.verifier.managedprovisioning.WORK_STATUS_TOAST";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.provisioning_cross_profile);

        findViewById(R.id.button_finish).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                WorkStatusTestActivity.this.finish();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        String action = getIntent().getAction();
        TextView textView = (TextView) findViewById(R.id.text);
        if (ACTION_WORK_STATUS_ICON.equals(action)) {
            textView.setText(R.string.provisioning_byod_work_status_icon_activity);
        } else if (ACTION_WORK_STATUS_TOAST.equals(action)) {
            textView.setText(R.string.provisioning_byod_work_status_toast_activity);
        }
    }
}
