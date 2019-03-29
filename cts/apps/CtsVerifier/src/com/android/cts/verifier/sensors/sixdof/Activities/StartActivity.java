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
package com.android.cts.verifier.sensors.sixdof.Activities;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.sixdof.Utils.ReportExporter;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

/**
 * Launcher activity that gives brief instructions of tests.
 */
public class StartActivity extends PassFailButtons.Activity {
    private Button mBtnStart;

    // Unique code that ensures we get the result from the activity that want to get a result
    private static final int REQUEST_CODE_6DOF = 5555;

    public enum ResultCode {
        FAILED_PAUSE_AND_RESUME,
        FAILED,
        PASSED
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        mBtnStart = (Button) findViewById(R.id.btnStart);
        mBtnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startPhase1();
            }
        });

        // If there is no 6DoF sensor advertised, pass trivially.
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_POSE_6DOF) == null) {
            StartActivity.this.setTestResultAndFinish(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void startPhase1() {
        Intent startPhase1 = new Intent(this, TestActivity.class);
        startActivityForResult(startPhase1, REQUEST_CODE_6DOF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to.
        if (requestCode == REQUEST_CODE_6DOF) {
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, R.string.test_failed, Toast.LENGTH_SHORT).show();
            } else { // RESULT_OK
                ResultCode result = (ResultCode) data.getSerializableExtra(TestActivity.EXTRA_RESULT_ID);
                if (result == ResultCode.FAILED_PAUSE_AND_RESUME) {
                    Toast.makeText(this, R.string.failed_pause_resume, Toast.LENGTH_SHORT).show();
                } else if (result == ResultCode.FAILED) {
                    Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show();
                } else if (result == ResultCode.PASSED) {
                    Toast.makeText(this, R.string.passed, Toast.LENGTH_SHORT).show();
                }

                String testReport = data.getStringExtra(TestActivity.EXTRA_REPORT);
                new ReportExporter(this, testReport).execute();
            }
        }
    }
}
