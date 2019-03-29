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

package com.android.cts.verifier.sensors;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.cts.helpers.SensorTestStateNotSupportedException;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.method.LinkMovementMethod;
import android.text.Html;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.TextView;

import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.base.SensorCtsVerifierTestActivity;
import com.android.cts.verifier.sensors.helpers.OpenCVLibrary;

import junit.framework.Assert;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

/**
 * This test (Rotation Vector - Computer Vision Cross Check, or RXCVXCheck for short) verifies that
 * mobile device can detect the orientation of itself in a relatively accurate manner.
 *
 * Currently only ROTATION_VECTOR sensor is used.
 *
 */
public class RVCVXCheckTestActivity
        extends SensorCtsVerifierTestActivity {
    public RVCVXCheckTestActivity() {
        super(RVCVXCheckTestActivity.class);
    }

    CountDownLatch mRecordActivityFinishedSignal = null;

    private static final int REQ_CODE_TXCVRECORD = 0x012345678;
    private static final boolean TEST_USING_DEBUGGING_DATA = false;
    private static final String PATH_DEBUGGING_DATA = "/sdcard/RXCVRecData/150313-014443/";

    private String mRecPath;

    RVCVXCheckAnalyzer.AnalyzeReport mReport = null;

    private boolean mRecordSuccessful = false;
    private boolean mOpenCVLoadSuccessful = false;

    private static class Criterion {
        public static final float roll_rms_error = 0.15f;
        public static final float pitch_rms_error = 0.15f;
        public static final float yaw_rms_error = 0.25f;

        public static final float roll_max_error = 0.35f;
        public static final float pitch_max_error = 0.35f;
        public static final float yaw_max_error = 0.5f;

        public static final float sensor_period_stdev = 0.25e-3f;
    };


    /**
     * The activity setup collects all the required data for test cases.
     * This approach allows to test all sensors at once.
     */
    @Override
    protected void activitySetUp() throws InterruptedException {

        mRecPath = "";

        // Test result is fail by default.
        getReportLog().setSummary(
                "Initialize failed", 0, ResultType.NEUTRAL, ResultUnit.NONE);

        showDetailedTutorialLink();

        showUserMessage("Loading OpenCV Library...");

        if (!OpenCVLibrary.load(this,
                false /*allowLocal*/, true/*allowPackage*/, false/*allowInstall*/)) {
            // cannot load opencv library
            showUserMessage("Cannot load OpenCV library! Please follow instruction and install " +
                    "OpenCV Manager 3.0.0 and start this test again.");
            waitForUserToContinue();
            clearText();
            return;
        }
        showUserMessage("OpenCV Library Successfully Loaded.");
        showOpenCVLibaryLicenseDisplayButton();

        mOpenCVLoadSuccessful = true;

        if (TEST_USING_DEBUGGING_DATA) {
            mRecPath = PATH_DEBUGGING_DATA;

            // assume the data is there already
            mRecordSuccessful = true;
        } else {
            showUserMessage("Take the test as instructed below:\n" +
                "1. Print out the test pattern and place it on a "+
                   "horizontal surface.\n" +
                "2. Start the test and align the yellow square on the screen "+
                   "roughly to the yellow sqaure.\n" +
                "3. Follow the prompt to orbit the device around the test " +
                   "pattern while aiming the field of view at the test pattern" +
                   "at the same time.\n" +
                "4. Wait patiently for the analysis to finish.");

            waitForUserToContinue();

            // prepare sync signal
            mRecordActivityFinishedSignal = new CountDownLatch(1);

            // record both sensor and camera
            Intent intent = new Intent(this, RVCVRecordActivity.class);
            startActivityForResult(intent, REQ_CODE_TXCVRECORD);

            // wait for record finish
            mRecordActivityFinishedSignal.await();

            if ("".equals(mRecPath)) {
                showUserMessage("Recording failed or exited prematurely.");
                waitForUserToContinue();
            } else {
                showUserMessage("Recording is done!");
                showUserMessage("Result are in path: " + mRecPath);
                mRecordSuccessful = true;
            }
        }


        if (!mRecordSuccessful) {
            getReportLog().setSummary(
                    "Record failed", 0, ResultType.NEUTRAL, ResultUnit.NONE);
        } else {
            showUserMessage("Please wait for the analysis ... \n"+
                            "It may take a few minutes, you will be noted when "+
                            "its finished by sound and vibration. ");

            // Analysis of recorded video and sensor data using RVCXAnalyzer
            RVCVXCheckAnalyzer analyzer = new RVCVXCheckAnalyzer(mRecPath);

            // acquire a partial wake lock just in case CPU fall asleep
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "RVCVXCheckAnalyzer");

            wl.acquire();
            mReport = analyzer.processDataSet();
            wl.release();

            playSound();
            vibrate(500);

            if (mReport == null) {
                showUserMessage("Analysis failed due to unknown reason!");
            } else {
                if (mReport.error) {
                    getReportLog().setSummary("Analysis failed: "+mReport.reason, 0,
                            ResultType.NEUTRAL, ResultUnit.NONE);

                    showUserMessage("Analysis failed: " + mReport.reason);
                } else {
                    getReportLog().setSummary(
                            "Analysis succeed", 1, ResultType.NEUTRAL, ResultUnit.NONE);

                    getReportLog().addValue("Roll error RMS", mReport.roll_rms_error,
                            ResultType.LOWER_BETTER, ResultUnit.RADIAN);
                    getReportLog().addValue("Pitch error RMS", mReport.pitch_rms_error,
                            ResultType.LOWER_BETTER, ResultUnit.RADIAN);
                    getReportLog().addValue("Yaw error RMS", mReport.yaw_rms_error,
                            ResultType.LOWER_BETTER, ResultUnit.RADIAN);

                    getReportLog().addValue("Roll error MAX", mReport.roll_max_error,
                            ResultType.LOWER_BETTER, ResultUnit.RADIAN);
                    getReportLog().addValue("Pitch error MAX", mReport.pitch_max_error,
                            ResultType.LOWER_BETTER, ResultUnit.RADIAN);
                    getReportLog().addValue("Yaw error MAX", mReport.yaw_max_error,
                            ResultType.LOWER_BETTER, ResultUnit.RADIAN);

                    getReportLog().addValue("Number of frames", mReport.n_of_frame,
                            ResultType.NEUTRAL, ResultUnit.COUNT);
                    getReportLog().addValue("Number of valid frames", mReport.n_of_valid_frame,
                            ResultType.NEUTRAL, ResultUnit.COUNT);

                    getReportLog().addValue("Sensor period mean", mReport.sensor_period_avg*1000,
                            ResultType.NEUTRAL, ResultUnit.MS);
                    getReportLog().addValue("Sensor period stdev", mReport.sensor_period_stdev*1000,
                            ResultType.NEUTRAL, ResultUnit.MS);
                    getReportLog().addValue("Time offset", mReport.optimal_delta_t*1000,
                            ResultType.NEUTRAL, ResultUnit.MS);
                    getReportLog().addValue("Yaw offset", mReport.yaw_offset,
                            ResultType.NEUTRAL, ResultUnit.RADIAN);

                    showUserMessage(String.format("Analysis finished!\n" +
                                    "Roll error (Rms, max) = %4.3f, %4.3f rad\n" +
                                    "Pitch error (Rms, max) = %4.3f, %4.3f rad\n" +
                                    "Yaw error (Rms, max) = %4.3f, %4.3f rad\n" +
                                    "N of Frame (valid, total) = %d, %d\n" +
                                    "Sensor period (mean, stdev) = %4.3f, %4.3f ms\n" +
                                    "Time offset: %4.3f s \n" +
                                    "Yaw offset: %4.3f rad \n\n",
                            mReport.roll_rms_error, mReport.roll_max_error,
                            mReport.pitch_rms_error, mReport.pitch_max_error,
                            mReport.yaw_rms_error, mReport.yaw_max_error,
                            mReport.n_of_valid_frame, mReport.n_of_frame,
                            mReport.sensor_period_avg * 1000.0, mReport.sensor_period_stdev*1000.0,
                            mReport.optimal_delta_t, mReport.yaw_offset));
                    showUserMessage("Please click next after details reviewed.");
                    waitForUserToContinue();
                }
            }
        }
        clearText();
    }

    /**
    Receiving the results from the RVCVRecordActivity, which is a patch where the recorded
    video and sensor data is stored.
    */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == REQ_CODE_TXCVRECORD) {
            // Make sure the request was successful

            if (resultCode == RESULT_OK) {
                mRecPath = data.getData().getPath();
            }

            // notify it is finished
            mRecordActivityFinishedSignal.countDown();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Test cases.
     *
     * Test cases is numbered to make sure they are executed in the right order.
     */

    public String test00OpenCV() throws Throwable {

        String message = "OpenCV is loaded";
        Assert.assertTrue("OpenCV library cannot be loaded. If OpenCV Manager is just installed, " +
                          "please restart this test.", mOpenCVLoadSuccessful);
        return message;
    }


    public String test01Recording() throws Throwable {

        loadOpenCVSuccessfulOrSkip();

        String message = "Record is successful.";
        Assert.assertTrue("Record is not successful.", mRecordSuccessful);
        return message;
    }

    public String test02Analysis() throws Throwable {

        loadOpenCVSuccessfulOrSkip();
        recordSuccessfulOrSkip();

        String message = "Analysis result: " + mReport.reason;
        Assert.assertTrue(message, (mReport!=null && !mReport.error));
        return message;
    }

    public String test1RollAxis() throws Throwable {

        loadOpenCVSuccessfulOrSkip();
        recordSuccessfulOrSkip();
        analyzeSuccessfulOrSkip();

        String message = "Test Roll Axis Accuracy";

        Assert.assertEquals("Roll RMS error", 0.0, mReport.roll_rms_error,
                Criterion.roll_rms_error);
        Assert.assertEquals("Roll max error", 0.0, mReport.roll_max_error,
                Criterion.roll_max_error);
        return message;
    }

    public String test2PitchAxis() throws Throwable {

        loadOpenCVSuccessfulOrSkip();
        recordSuccessfulOrSkip();
        analyzeSuccessfulOrSkip();

        String message = "Test Pitch Axis Accuracy";

        Assert.assertEquals("Pitch RMS error", 0.0, mReport.pitch_rms_error,
                Criterion.pitch_rms_error);
        Assert.assertEquals("Pitch max error", 0.0, mReport.pitch_max_error,
                Criterion.pitch_max_error);
        return message;
    }

    public String test3YawAxis() throws Throwable {

        loadOpenCVSuccessfulOrSkip();
        recordSuccessfulOrSkip();
        analyzeSuccessfulOrSkip();

        String message = "Test Yaw Axis Accuracy";

        Assert.assertEquals("Yaw RMS error", 0.0, mReport.yaw_rms_error,
                Criterion.yaw_rms_error);
        Assert.assertEquals("Yaw max error", 0.0, mReport.yaw_max_error,
                Criterion.yaw_max_error);
        return message;
    }

    private void loadOpenCVSuccessfulOrSkip() throws SensorTestStateNotSupportedException {
        if (!mOpenCVLoadSuccessful)
            throw new SensorTestStateNotSupportedException("Skipped due to OpenCV cannot be loaded");
    }

    private void recordSuccessfulOrSkip() throws SensorTestStateNotSupportedException {
        if (!mRecordSuccessful)
            throw new SensorTestStateNotSupportedException("Skipped due to record failure.");
    }

    private void analyzeSuccessfulOrSkip() throws SensorTestStateNotSupportedException {
        if (mReport == null || mReport.error)
            throw new SensorTestStateNotSupportedException("Skipped due to CV Analysis failure.");
    }

    /*
     *  This function serves as a proxy as appendText is marked to be deprecated.
     *  When appendText is removed, this function will have a different implementation.
     *
     */
    private void showUserMessage(String s) {
        appendText(s);
    }

    private void showDetailedTutorialLink() {
        TextView textView = new TextView(this);
        textView.setText(Html.fromHtml(
                    "Detailed test instructions can be found at " +
                    "<A href=\"http://goo.gl/xTwB4d\">http://goo.gl/xTwB4d</a><br>"));
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setPadding(10, 0, 0, 0);
        getTestLogger().logCustomView(textView);
    }

    private void showOpenCVLibaryLicenseDisplayButton() {
        Button btnLicense = new Button(this);
        btnLicense.setText("View OpenCV Library BSD License Agreement");
        // Avoid default all cap text on button.
        btnLicense.setTransformationMethod(null);
        btnLicense.setLayoutParams(new LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT));

        // load
        Resources res = getResources();
        InputStream rawStream = res.openRawResource(R.raw.opencv_library_license);
        byte[] byteArray;
        try {
            byteArray = new byte[rawStream.available()];
            rawStream.read(byteArray);
        } catch (IOException e) {
            e.printStackTrace();
            byteArray = "Unable to load license text.".getBytes();
        }
        final String licenseText = new String(byteArray);

        btnLicense.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                AlertDialog dialog =
                    new AlertDialog.Builder(RVCVXCheckTestActivity.this)
                        .setTitle("OpenCV Library BSD License")
                        .setMessage(licenseText)
                        .setPositiveButton("Acknowledged", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            })
                        .show();

                TextView textView = (TextView) dialog.findViewById(android.R.id.message);
                textView.setTextSize(9);
            }
        });
        getTestLogger().logCustomView(btnLicense);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // GlSurfaceView is not necessary for this test
        closeGlSurfaceView();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

    }
}
