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

package com.android.cts.verifier.sample;

import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestResult;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileOutputStream;

/**
 * A sample CTS Verifier test case for testing file transfers using bluetooth sharing.
 *
 * This test assumes bluetooth is turned on and the device is already paired with a second device.
 * Note: the second device need not be an Android device; it could be a laptop or desktop.
 */
public class SampleTestActivity extends PassFailButtons.Activity {

    /**
     * The name of the test file being transferred.
     */
    private static final String FILE_NAME = "test.txt";

    /**
     * The content of the test file being transferred.
     */
    private static final String TEST_STRING = "Sample Test String";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the UI.
        setContentView(R.layout.pass_fail_sample);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.sample_test, R.string.sample_test_info, -1);
        // Get the share button and attach the listener.
        Button shareBtn = (Button) findViewById(R.id.sample_share_btn);
        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    createFileAndShare();
                    recordMetricsExample();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void recordMetricsExample() {
        double[] metricValues = new double[] {1, 11, 21, 1211, 111221};

        // Record metric results
        getReportLog().setSummary("Sample Summary", 1.0, ResultType.HIGHER_BETTER,
                ResultUnit.BYTE);
        getReportLog().addValues("Sample Values", metricValues, ResultType.NEUTRAL,
                ResultUnit.FPS);

        // Alternatively, activities can invoke TestResult directly to record metrics
        ReportLog reportLog = new PassFailButtons.CtsVerifierReportLog();
        reportLog.setSummary("Sample Summary", 1.0, ResultType.HIGHER_BETTER, ResultUnit.BYTE);
        reportLog.addValues("Sample Values", metricValues, ResultType.NEUTRAL, ResultUnit.FPS);
        TestResult.setPassedResult(this, "manualSample", "manualDetails", reportLog);
    }

    /**
     * Creates a temporary file containing the test string and then issues the intent to share it.
     *
     * @throws Exception
     */
    private void createFileAndShare() throws Exception {
        // Use the external cache directory so the file will be deleted when the app is uninstalled
        // and the file can be accessed by other apps, such as the sharing app.
        File dir = getExternalCacheDir ();
        // Create the file with the given name.
        File file = new File(dir, FILE_NAME);
        FileOutputStream outputStream = null;
        try {
            // Write the test string to the test file.
            outputStream = new FileOutputStream(file);
            outputStream.write(TEST_STRING.getBytes());

            // Create the share intent.
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            startActivity(intent);
        } finally {
            // Clean up.
            if (outputStream != null) {
                outputStream.close();
            }
        }

    }
}
