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
package com.google.android.car.diagnosticverifier;

import android.app.Activity;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.diagnostic.CarDiagnosticEvent;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.hardware.CarSensorManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.JsonWriter;
import android.util.Log;
import android.widget.TextView;

import com.google.android.car.diagnosticverifier.DiagnosticVerifier.VerificationResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * The test app that does the verification of car diagnostic event data. It first reads the
 * truth (golden) event data from a JSON file upon starting. Then a broadcast intent such as:
 *
 *     am broadcast -a com.google.android.car.diagnosticverifier.action.START_LISTEN
 *
 * will activate the car diagnostics listener. The test app will receive events from diagnostic API.
 * Once it receives all the events, a broadcast intent with "stop" action such as:
 *
 *     am broadcast -a com.google.android.car.diagnosticverifier.action.STOP_LISTEN
 *
 * will deactivate the listener and start the verification process (see {@link DiagnosticVerifier}).
 *
 * Verification result will be output to a JSON file on device.
 */
public class MainActivity extends Activity {
    public static final String TAG = "DiagnosticVerifier";

    public static final String ACTION_START_LISTEN =
            "com.google.android.car.diagnosticverifier.action.START_LISTEN";
    public static final String ACTION_STOP_LISTEN =
            "com.google.android.car.diagnosticverifier.action.STOP_LISTEN";

    private static final String DEFAULT_JSON_PATH = "/data/local/tmp/diag.json";

    private static final String JSON_PATH_KEY = "jsonPath";
    private static final String JSON_RESULT = "verification_result.json";

    private Car mCar;
    private CarDiagnosticManager mCarDiagnosticManager;
    private DiagnosticListener mDiagnosticListener;
    private BroadcastReceiver mBroadcastReceiver;
    private DiagnosticVerifier mVerifier;
    private TextView mStatusBar;
    private RecyclerView mRecyclerView;
    private VerificationResultAdapter mResultAdapter;
    private boolean mListening = false;

    private final ServiceConnection mCarConnectionListener =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder iBinder) {
                    Log.d(TAG, "Connected to " + name.flattenToString());
                    try {
                        mCarDiagnosticManager =
                                (CarDiagnosticManager) mCar.getCarManager(Car.DIAGNOSTIC_SERVICE);
                    } catch (CarNotConnectedException e) {
                        Log.e(TAG, "Failed to get a connection", e);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.d(TAG, "Disconnected from " + name.flattenToString());

                    mCar = null;
                    mCarDiagnosticManager = null;
                }
            };

    class DiagnosticListener implements CarDiagnosticManager.OnDiagnosticEventListener {

        @Override
        public void onDiagnosticEvent(CarDiagnosticEvent carDiagnosticEvent) {
            Log.v(TAG, "Received Car Diagnostic Event: " + carDiagnosticEvent.toString());
            mVerifier.receiveEvent(carDiagnosticEvent);
        }
    }

    class VerifierMsgReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Received intent with action: " + action);
            if (ACTION_START_LISTEN.equals(action)) {
                try {
                    startListen();
                } catch (CarNotConnectedException e) {
                    Log.e(TAG, "Failed to listen for car diagnostic event", e);
                }
            } else if (ACTION_STOP_LISTEN.equals(action)) {
                stopListen();
                verify();
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.verifier_activity);

        mStatusBar = (TextView) findViewById(R.id.status_bar);

        //Setting up RecyclerView to show verification result messages
        mRecyclerView = (RecyclerView) findViewById(R.id.verification_results);
        LinearLayoutManager layoutManager =
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(layoutManager);
        mResultAdapter = new VerificationResultAdapter();
        mRecyclerView.setAdapter(mResultAdapter);

        //Connect to car service
        mCar = Car.createCar(this, mCarConnectionListener);
        mCar.connect();

        //Initialize broadcast intent receiver
        mBroadcastReceiver = new VerifierMsgReceiver();
        IntentFilter filter = new IntentFilter(ACTION_START_LISTEN);
        filter.addAction(ACTION_STOP_LISTEN);
        this.registerReceiver(mBroadcastReceiver, filter);

        //Read golden diagnostics JSON file
        String jsonPath = this.getIntent().getStringExtra(JSON_PATH_KEY);
        if (jsonPath == null || jsonPath.isEmpty()) {
            jsonPath = DEFAULT_JSON_PATH;
        }
        List<CarDiagnosticEvent> events;
        try {
            events = DiagnosticJsonConverter.readFromJson(new FileInputStream(jsonPath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read diagnostic JSON file", e);
        }
        Log.d(TAG, String.format("Read %d events from JSON file %s.", events.size(), jsonPath));

        mVerifier = new DiagnosticVerifier(events);
    }

    @Override
    protected void onDestroy() {
        if (mCar != null) {
            mCar.disconnect();
        }
        mVerifier = null;
        this.unregisterReceiver(mBroadcastReceiver);
    }

    private void startListen() throws CarNotConnectedException {
        if (mListening) {
            return;
        }
        if (mDiagnosticListener == null) {
            mDiagnosticListener = new DiagnosticListener();
        }
        Log.i(TAG, "Start listening for car diagnostics events");
        mCarDiagnosticManager.registerListener(
                mDiagnosticListener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                CarSensorManager.SENSOR_RATE_NORMAL);
        mCarDiagnosticManager.registerListener(
                mDiagnosticListener,
                CarDiagnosticManager.FRAME_TYPE_FREEZE,
                CarSensorManager.SENSOR_RATE_NORMAL);

        mListening = true;
        mStatusBar.setText(R.string.status_receiving);
    }

    private void stopListen() {
        Log.i(TAG, "Stop listening for car diagnostics events");
        mCarDiagnosticManager.unregisterListener(mDiagnosticListener);
        mListening = false;
    }

    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private File getResultJsonFile() throws IOException {
        if (!isExternalStorageWritable()) {
            throw new IOException("External storage is not writable. Cannot save content");
        }

        File resultJson = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), JSON_RESULT);
        if (!resultJson.getParentFile().mkdirs()) {
            Log.w(TAG, "Parent directory may already exist");
        }
        return resultJson;
    }

    private void verify() {
        Log.d(TAG, "Start verifying car diagnostics events");
        mStatusBar.setText(R.string.status_verifying);
        List<VerificationResult> results = mVerifier.verify();
        mStatusBar.setText(R.string.status_done);

        if (results.isEmpty()) {
            Log.d(TAG, "Verification result is empty.");
            return;
        }

        List<String> resultMessages = new ArrayList<>();
        try {
            File resultJson = getResultJsonFile();
            JsonWriter writer = new JsonWriter(
                new OutputStreamWriter(new FileOutputStream(resultJson)));

            writer.beginArray();
            for (VerificationResult result : results) {
                resultMessages.add("Test case: " + result.testCase);
                resultMessages.add("Result: " + result.success);
                resultMessages.add(result.errorMessage);
                result.writeToJson(writer);
            }
            writer.endArray();
            writer.flush();
            writer.close();
            Log.i(TAG, "Verification result: " + resultJson.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to save verification result.", e);
        }
        mResultAdapter.setResultMessages(resultMessages);
    }
}

