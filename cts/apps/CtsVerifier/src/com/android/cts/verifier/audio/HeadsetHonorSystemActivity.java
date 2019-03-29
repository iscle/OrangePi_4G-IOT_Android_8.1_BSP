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

package com.android.cts.verifier.audio;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import android.content.Context;

import android.os.Bundle;
import android.os.Handler;

import android.util.Log;

import android.view.View;
import android.view.View.OnClickListener;

import android.widget.Button;
//import android.widget.TextView;

abstract class HeadsetHonorSystemActivity extends PassFailButtons.Activity {
    private static final String TAG = "HeadsetHonorSystemActivity";

    private OnBtnClickListener mBtnClickListener = new OnBtnClickListener();

    abstract protected void enableTestButtons(boolean enabled);

    private void recordHeadsetPortFound(boolean found) {
        getReportLog().addValue(
                "User Reported Headset Port",
                found ? 1.0 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    protected void setup() {
        // The "Honor" system buttons
        ((Button)findViewById(R.id.audio_general_headset_no)).
            setOnClickListener(mBtnClickListener);
        ((Button)findViewById(R.id.audio_general_headset_yes)).
            setOnClickListener(mBtnClickListener);

        enableTestButtons(false);
    }

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.audio_general_headset_no:
                    Log.i(TAG, "User denies Headset Port existence");
                    enableTestButtons(false);
                    recordHeadsetPortFound(false);
                    break;

                case R.id.audio_general_headset_yes:
                    Log.i(TAG, "User confirms Headset Port existence");
                    enableTestButtons(true);
                    recordHeadsetPortFound(true);
                    break;
            }
        }
    }

}