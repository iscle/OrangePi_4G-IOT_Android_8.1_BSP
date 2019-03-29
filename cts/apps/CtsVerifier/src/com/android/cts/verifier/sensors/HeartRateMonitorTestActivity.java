/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.cts.verifier.sensors;

import android.app.AlertDialog;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * CTS Verifier case for verifying correct integration of heart rate monitor.
 * If a user is wearing a device with an HRM, the value is between <> and <>
 */
public class HeartRateMonitorTestActivity extends PassFailButtons.Activity {
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private SensorListener mSensorListener;
    private AlertDialog mNoHeartRateWarningDialog;
    private TextView mSensorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.snsr_hrm);
        setInfoResources(R.string.snsr_heartrate_test, R.string.snsr_heartrate_test_info, 0);
        setPassFailButtonClickListeners();

        mSensorText = (TextView) findViewById(R.id.sensor_value);

        mSensorManager = (SensorManager) getApplicationContext().getSystemService(
                Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        mSensorListener = new SensorListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mSensorManager.registerListener(mSensorListener, mSensor,
                SensorManager.SENSOR_DELAY_UI)) {
            showNoHeartRateWarningDialog();
            setTestResultAndFinish(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(mSensorListener, mSensor);
    }

    private void showNoHeartRateWarningDialog() {
        if (mNoHeartRateWarningDialog == null) {
            mNoHeartRateWarningDialog = new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.snsr_heartrate_test_no_heartrate_title)
                    .setMessage(R.string.snsr_heartrate_test_no_heartrate_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
        }
        if (!mNoHeartRateWarningDialog.isShowing()) {
            mNoHeartRateWarningDialog.show();
        }
    }

    private class SensorListener implements SensorEventListener {
        private static final double MIN_HEART_RATE = 40;
        private static final double MAX_HEART_RATE = 200;
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            float value = sensorEvent.values[0];
            if (value > MAX_HEART_RATE || value < MIN_HEART_RATE) {
                updateWidgets(value, sensorEvent.accuracy, R.drawable.fs_error);
            } else {
                updateWidgets(value, sensorEvent.accuracy, R.drawable.fs_good);
            }
        }

        void updateWidgets(float value, float accuracy, int icon) {
            TextView sensorText = (TextView) findViewById(R.id.sensor_value);
            TextView sensorAccuracyText = (TextView) findViewById(R.id.sensor_accuracy_value);

            sensorText.setText(String.format("%+.2f", value));
            sensorText.setCompoundDrawablesWithIntrinsicBounds(0, 0, icon, 0);
            sensorAccuracyText.setText(String.format("%+.2f", accuracy));
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
        }
    }
}
