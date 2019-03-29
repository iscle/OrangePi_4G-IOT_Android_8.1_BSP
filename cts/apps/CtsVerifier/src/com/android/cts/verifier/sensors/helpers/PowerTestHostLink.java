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
 * limitations under the License.
 */

package com.android.cts.verifier.sensors.helpers;

import com.android.cts.verifier.sensors.reporting.SensorTestDetails;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.StringTokenizer;

/**
 * This class handles communication with the host to respond to commands.
 * The command/response link is through a TCP socket on the host side, forwarded via adb to a local
 * socket on the device. The system uses a standard "accept-read_command-send_response-close" to
 * execute commands sent from the host.
 *
 * CAUTION: The local socket name (SOCKET_NAME below) must match that used by the host to set up
 * the adb-forwarding.
 */
public class PowerTestHostLink {
    private static final String TAG = "PowerTestHostLink";

    /**
     * Host-to-device bridge will use a Listener instance to drive the test via the CtsVerifier
     * running on the device.
     */
    public interface HostToDeviceInterface {
        void logTestResult(SensorTestDetails testDetails);
        void raiseError(String testName, String message) throws Exception;
        void waitForUserAcknowledgement(String message) throws InterruptedException;
        void logText(String text);
        void turnScreenOff();
    }

    /** This is a data-only message to communicate result of a power test */
    public class PowerTestResult{
        public int passedCount = 0;
        public int skippedCount = 0;
        public int failedCount = 0;
    }

    /**
     * Standard response types back to host. Host-side code must match these definitions.
     */
    private final static String RESPONSE_OK = "OK";
    private final static String RESPONSE_ERR = "ERR";
    private final static String RESPONSE_UNAVAILABLE = "UNAVAILABLE";

    /**
     * Socket name for host adb forwarded communications. Must match naem in host-side code.
     */
    public final static String SOCKET_NAME = "/android/cts/powertest";

    private volatile boolean mStopThread;
    private final SensorManager mSensorManager;
    private final HostToDeviceInterface mHostToDeviceExecutor;
    private final PowerTestResult mTestResult = new PowerTestResult();

    public PowerTestHostLink(Context context, HostToDeviceInterface listener) {
        Log.d(TAG, " +++ Begin of localSocketServer() +++ ");
        mHostToDeviceExecutor = listener;
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    /**
     * Ensure connection to host is closed; stop accepting requests.
     **/
    public void close() {
        mStopThread = true;
    }

    /**
     * Run the suite of tests via the host, responding to host requests.
     *
     * @return number of failed test cases
     * @throws Exception
     */
    public PowerTestResult run() throws Exception {
        // define buffer to receive data from host
        final int BUFFER_SIZE = 4096;
        byte[] buffer = new byte[BUFFER_SIZE];

        LocalServerSocket serverSocket = createSocket();
        if (null == serverSocket) {
            mStopThread = true;
        }

        InputStream streamIn;
        OutputStream streamOut;
        LocalSocket receiverSocket;
        while (!mStopThread) {

            try {
                Log.d(TAG, "localSocketServer accept...");
                receiverSocket = serverSocket.accept();
                Log.d(TAG, "Got new connection");
            } catch (IOException e) {
                Log.d(TAG, "localSocketServer accept() failed !!!", e);
                continue;
            }

            try {
                streamIn = receiverSocket.getInputStream();
            } catch (IOException e) {
                Log.d(TAG, "getInputStream() failed !!!", e);
                continue;
            }

            try {
                streamOut = receiverSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "getOutputStream() failed", e);
                continue;
            }

            Log.d(TAG, "The client connected to LocalServerSocket");

            try {
                int total = 0;
                // command and response handshake, so read all data
                while (streamIn.available() > 0 || total == 0) {
                    if (total < BUFFER_SIZE) {
                        int bytesRead = streamIn.read(buffer, total,
                                (BUFFER_SIZE - total));
                        if (bytesRead > 0) {
                            total += bytesRead;
                        }
                    } else {
                        Log.e(TAG, "Message too long: truncating");
                    }
                }
                String clientRequest = new String(buffer);
                clientRequest = clientRequest.substring(0, total);
                if (clientRequest.length() > 0) {

                    Log.d(TAG, "Client requested: " + clientRequest);
                    try {
                        String response = processClientRequest(clientRequest);
                        if (response != null) {
                            Log.d(TAG, "Sending response " + response);
                            streamOut.write(response.getBytes(), 0, response.length());
                        }
                        // null response means response is deferred awaiting user response
                    } catch (Exception e) {
                        Log.e(TAG, "Error executing " + clientRequest, e);
                        streamOut.write(RESPONSE_ERR.getBytes(), 0, RESPONSE_ERR.length());
                    }
                }
                receiverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "There is an exception when reading from or writing to socket", e);
                break;
            }
        }
        Log.d(TAG, "The LocalSocketServer thread is going to stop !!!");

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.d(TAG, "Exception on close of server socket", e);
            }
        }
        mHostToDeviceExecutor.logText("Device disconnected.");
        Log.d(TAG, "Returning " + mTestResult.passedCount + "passed " + mTestResult.skippedCount +
                "skipped " + mTestResult.failedCount + "failed.");
        return mTestResult;
    }

    private  String processClientRequest(String request) throws Exception {
        // the following constants need to match the definitions in execute_power_tests.py
        final String REQUEST_EXTERNAL_STORAGE = "EXTERNAL STORAGE?";
        final String REQUEST_EXIT = "EXIT";
        final String REQUEST_RAISE = "RAISE ";
        final String REQUEST_USER_RESPONSE = "USER RESPONSE ";
        final String REQUEST_SET_TEST_RESULT = "SET TEST RESULT ";
        final String REQUEST_SENSOR_ON = "SENSOR ON ";
        final String REQUEST_SENSOR_OFF = "SENSOR OFF";
        final String REQUEST_SENSOR_AVAILABILITY = "SENSOR? ";
        final String REQUEST_SCREEN_OFF = "SCREEN OFF";
        final String REQUEST_SHOW_MESSAGE = "MESSAGE ";

        String response = RESPONSE_ERR;
        // Queries must appear first and then commands to direct actions after in these statements
        if (request.startsWith(REQUEST_SENSOR_AVAILABILITY)) {
            final String sensor = request.substring(REQUEST_SENSOR_AVAILABILITY.length());
            final int sensorId = getSensorId(sensor);
            if (mSensorManager.getDefaultSensor(sensorId) == null) {
                response = RESPONSE_UNAVAILABLE;
            } else {
                response = RESPONSE_OK;
            }
        } else if (request.startsWith(REQUEST_EXTERNAL_STORAGE)){
            response = SensorCtsHelper.getSensorTestDataDirectory("power/").getAbsolutePath();
            Log.d(TAG,"External storage is " + response);
        } else if (request.startsWith(REQUEST_SCREEN_OFF)) {
            try {
                mHostToDeviceExecutor.turnScreenOff();
                response = RESPONSE_OK;
            } catch (SecurityException e) {
                Log.e(TAG, "Error Turning screen off", e);
                response = RESPONSE_ERR;
            }
        } else if (request.startsWith(REQUEST_SENSOR_ON)) {
            String sensorList = request.substring(REQUEST_SENSOR_ON.length()).trim();
            response = handleSensorSwitchCommand(sensorList, true);
        } else if (request.startsWith(REQUEST_SENSOR_OFF)) {
            String sensorList = request.substring(REQUEST_SENSOR_ON.length()).trim();
            response = handleSensorSwitchCommand(sensorList, false);
        } else if (request.startsWith(REQUEST_SHOW_MESSAGE)) {
            final String message = request.substring(REQUEST_SHOW_MESSAGE.length());
            mHostToDeviceExecutor.logText(message);
            response = RESPONSE_OK;
        } else if (request.startsWith(REQUEST_USER_RESPONSE)) {
            String message = request.substring(REQUEST_USER_RESPONSE.length());
            mHostToDeviceExecutor.waitForUserAcknowledgement(message);
            response = RESPONSE_OK;
        } else if (request.startsWith(REQUEST_SET_TEST_RESULT)) {
            String testResult = request.substring(REQUEST_SET_TEST_RESULT.length());
            response = handleSetTestResultCmd(testResult);
        } else if (request.startsWith(REQUEST_RAISE)) {
            String command = request.substring(REQUEST_RAISE.length());
            StringTokenizer tokenizer = new StringTokenizer(command);
            try {
                final String testName = tokenizer.nextToken();
                final String message = request.substring(7 + testName.length());
                mHostToDeviceExecutor.raiseError(testName, message);
                response = RESPONSE_OK;
            } catch (Exception e) {
                Log.e(TAG, "Invalid RAISE command received (bad arguments): " + request);
                response = RESPONSE_ERR;
            }
        } else if (request.startsWith(REQUEST_EXIT)) {
            mStopThread = true;
            response = RESPONSE_OK;
        } else {
            Log.e(TAG, "Unknown request: " + request);
        }
        return response;
    }

    private String handleSetTestResultCmd(final String request) {
        String response;
        StringTokenizer tokenizer = new StringTokenizer(request, " ");
        String testName = "";
        SensorTestDetails.ResultCode resultCode = SensorTestDetails.ResultCode.FAIL;
        String message = "";

        try {
            testName = tokenizer.nextToken();
            final String resultToken = tokenizer.nextToken();

            if (resultToken.equals("PASS")) {
                resultCode = SensorTestDetails.ResultCode.PASS;
                ++mTestResult.passedCount;
                message = "PASSED: ";
                response = RESPONSE_OK;
            } else if (resultToken.equals("FAIL")) {
                resultCode = SensorTestDetails.ResultCode.FAIL;
                ++mTestResult.failedCount;
                message = "FAILED: ";
                response = RESPONSE_OK;
            } else if (resultToken.equals("SKIPPED")) {
                resultCode = SensorTestDetails.ResultCode.SKIPPED;
                ++mTestResult.skippedCount;
                message = "SKIPPED: ";
                response = RESPONSE_OK;
            } else {
                response = RESPONSE_ERR;
            }
            while (tokenizer.hasMoreTokens()) {
                message += tokenizer.nextToken() + " ";
            }
            Log.i(TAG, message);
        } catch (Exception e) {
            Log.e(TAG, "Improperly formatted command received: " + request, e);
            response = RESPONSE_ERR;
        }
        String fullMessage = testName + " " + message;
        mHostToDeviceExecutor.logTestResult(
                new SensorTestDetails(testName, resultCode, fullMessage));
        return response;
    }

    private String handleSensorSwitchCommand(String sensorList, boolean switchOn) {
        String response;
        try {
            StringTokenizer tokenizer = new StringTokenizer(sensorList, " ");
            int n = tokenizer.countTokens();
            if (n == 0) {
                response = switchAllSensors(switchOn);
            } else {
                String sensorName = tokenizer.nextToken();
                String requestRate = "";
                if (n > 1) {
                    requestRate = tokenizer.nextToken();
                }
                if (sensorName.equals("ALL")) {
                    response = switchAllSensors(switchOn);
                } else {
                    int sensorId = getSensorId(sensorName);
                    if (sensorId >= 0) {
                        response = switchSensor(sensorId, switchOn, requestRate);
                    } else {
                        Log.e(TAG, "Unknown sensor in request: " + sensorName);
                        response = RESPONSE_UNAVAILABLE;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Improperly formatted command received on setting sensor state");
            response = RESPONSE_ERR;
        }
        return response;
    }

    private static int getSensorId(String sensorName) {
        int sensorId = -1;

        if (sensorName.compareToIgnoreCase("ACCELEROMETER") == 0) {
            sensorId = Sensor.TYPE_ACCELEROMETER;
        } else if (sensorName.compareToIgnoreCase("AMBIENT_TEMPERATURE") == 0) {
            sensorId = Sensor.TYPE_AMBIENT_TEMPERATURE;
        } else if (sensorName.compareToIgnoreCase("GAME_ROTATION_VECTOR") == 0) {
            sensorId = Sensor.TYPE_GAME_ROTATION_VECTOR;
        } else if (sensorName.compareToIgnoreCase("GEOMAGNETIC_ROTATION_VECTOR") == 0) {
            sensorId = Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR;
        } else if (sensorName.compareToIgnoreCase("GRAVITY") == 0) {
            sensorId = Sensor.TYPE_GRAVITY;
        } else if (sensorName.compareToIgnoreCase("GYROSCOPE") == 0) {
            sensorId = Sensor.TYPE_GYROSCOPE;
        } else if (sensorName.compareToIgnoreCase("LIGHT") == 0) {
            sensorId = Sensor.TYPE_LIGHT;
        } else if (sensorName.compareToIgnoreCase("MAGNETIC_FIELD") == 0) {
            sensorId = Sensor.TYPE_MAGNETIC_FIELD;
        } else if (sensorName.compareToIgnoreCase("PRESSURE") == 0) {
            sensorId = Sensor.TYPE_PRESSURE;
        } else if (sensorName.compareToIgnoreCase("PROXIMITY") == 0) {
            sensorId = Sensor.TYPE_PROXIMITY;
        } else if (sensorName.compareToIgnoreCase("RELATIVE_HUMIDITY") == 0) {
            sensorId = Sensor.TYPE_RELATIVE_HUMIDITY;
        } else if (sensorName.compareToIgnoreCase("ROTATION_VECTOR") == 0) {
            sensorId = Sensor.TYPE_ROTATION_VECTOR;
        } else if (sensorName.compareToIgnoreCase("SIGNIFICANT_MOTION") == 0) {
            sensorId = Sensor.TYPE_SIGNIFICANT_MOTION;
        } else if (sensorName.compareToIgnoreCase("STEP_COUNTER") == 0) {
            sensorId = Sensor.TYPE_STEP_COUNTER;
        } else if (sensorName.compareToIgnoreCase("STEP_DETECTOR") == 0) {
            sensorId = Sensor.TYPE_STEP_DETECTOR;
        }

        return sensorId;
    }

    private String switchSensor(int sensorId, boolean switchOn) {
        return switchSensor(sensorId, switchOn, "SENSOR_DELAY_NORMAL");
    }

    private String switchSensor(int sensorId, boolean switchOn, String requestFrequency) {
        String response;
        int rateUs = SensorManager.SENSOR_DELAY_NORMAL;

        if (requestFrequency.compareToIgnoreCase("SENSOR_DELAY_FASTEST") == 0) {
            rateUs = SensorManager.SENSOR_DELAY_FASTEST;
        } else if (requestFrequency.compareToIgnoreCase("SENSOR_DELAY_GAME") == 0) {
            rateUs = SensorManager.SENSOR_DELAY_GAME;
        } else if (requestFrequency.compareToIgnoreCase("SENSOR_DELAY_UI") == 0) {
            rateUs = SensorManager.SENSOR_DELAY_UI;
        }

        if (switchOn) {
            mSensorManager.registerListener(mSensorEventListener,
                    mSensorManager.getDefaultSensor(sensorId), rateUs);
            response = RESPONSE_OK;
            Log.v(TAG, "Switching ON " + String.valueOf(sensorId) + " | " + String.valueOf(rateUs));
        } else {
            mSensorManager.unregisterListener(mSensorEventListener,
                    mSensorManager.getDefaultSensor(sensorId));
            response = RESPONSE_OK;
            Log.v(TAG, "Switching  OFF " + String.valueOf(sensorId));
        }

        return response;
    }

    private String switchAllSensors(boolean on) {
        List<Sensor> allSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        String response = RESPONSE_OK;
        for (Sensor sensor : allSensors) {
            if (sensor.getType() >= Sensor.TYPE_DEVICE_PRIVATE_BASE) {
                continue;
            }
            response = switchSensor(sensor.getType(), on);
            if (response == null) {
                response = RESPONSE_ERR;
            }
        }
        return response;
    }

    private LocalServerSocket createSocket() {
        try {
            return new LocalServerSocket(SOCKET_NAME);
        } catch (IOException e) {
            Log.e(TAG, "LocalSocketServer creation failure.", e);
            return null;
        }
    }

    private SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        @Override
        public void onSensorChanged(SensorEvent event) {}
    };
}
