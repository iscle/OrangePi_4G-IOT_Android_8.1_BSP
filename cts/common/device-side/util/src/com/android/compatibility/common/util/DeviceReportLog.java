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

package com.android.compatibility.common.util;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import com.android.compatibility.common.util.ReportLog;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Handles adding results to the report for device side tests.
 *
 * NOTE: tests MUST call {@link #submit(Instrumentation)} if and only if the test passes in order to
 * send the results to the runner.
 */
public class DeviceReportLog extends ReportLog {
    private static final String TAG = DeviceReportLog.class.getSimpleName();
    private static final String RESULT = "COMPATIBILITY_TEST_RESULT";
    private static final int INST_STATUS_ERROR = -1;
    private static final int INST_STATUS_IN_PROGRESS = 2;

    private ReportLogDeviceInfoStore store;

    public DeviceReportLog(String reportLogName, String streamName) {
        super(reportLogName, streamName);
        try {
            // dir value must match the src-dir value configured in ReportLogCollector target
            // preparer in cts/tools/cts-tradefed/res/config/cts-preconditions.xml
            final File dir = new File(Environment.getExternalStorageDirectory(), "report-log-files");
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                throw new IOException("External storage is not mounted");
            } else if (!dir.mkdirs() && !dir.isDirectory()) {
                throw new IOException("Cannot create directory for device info files");
            } else {
                File jsonFile = new File(dir, mReportLogName + ".reportlog.json");
                store = new ReportLogDeviceInfoStore(jsonFile, mStreamName);
                store.open();
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not create report log file.", e);
        }
    }

    /**
     * Adds a double metric to the report.
     */
    @Override
    public void addValue(String source, String message, double value, ResultType type,
            ResultUnit unit) {
        super.addValue(source, message, value, type, unit);
        try {
            store.addResult(message, value);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Adds a double metric to the report.
     */
    @Override
    public void addValue(String message, double value, ResultType type, ResultUnit unit) {
        super.addValue(message, value, type, unit);
        try {
            store.addResult(message, value);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Adds a double array of metrics to the report.
     */
    @Override
    public void addValues(String source, String message, double[] values, ResultType type,
            ResultUnit unit) {
        super.addValues(source, message, values, type, unit);
        try {
            store.addArrayResult(message, values);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Adds a double array of metrics to the report.
     */
    @Override
    public void addValues(String message, double[] values, ResultType type, ResultUnit unit) {
        super.addValues(message, values, type, unit);
        try {
            store.addArrayResult(message, values);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Adds an int metric to the report.
     */
    @Override
    public void addValue(String message, int value, ResultType type, ResultUnit unit) {
        try {
            store.addResult(message, value);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Adds a long metric to the report.
     */
    @Override
    public void addValue(String message, long value, ResultType type, ResultUnit unit) {
        try {
            store.addResult(message, value);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Adds a float metric to the report.
     */
    @Override
    public void addValue(String message, float value, ResultType type, ResultUnit unit) {
        try {
            store.addResult(message, value);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Adds a boolean metric to the report.
     */
    @Override
    public void addValue(String message, boolean value, ResultType type, ResultUnit unit) {
        try {
            store.addResult(message, value);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Adds a String metric to the report.
     */
    @Override
    public void addValue(String message, String value, ResultType type, ResultUnit unit) {
        try {
            store.addResult(message, value);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Adds an int array of metrics to the report.
     */
    @Override
    public void addValues(String message, int[] values, ResultType type, ResultUnit unit) {
        try {
            store.addArrayResult(message, values);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Adds a long array of metrics to the report.
     */
    @Override
    public void addValues(String message, long[] values, ResultType type, ResultUnit unit) {
        try {
            store.addArrayResult(message, values);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Adds a float array of metrics to the report.
     */
    @Override
    public void addValues(String message, float[] values, ResultType type, ResultUnit unit) {
        try {
            store.addArrayResult(message, values);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Adds a boolean array of metrics to the report.
     */
    @Override
    public void addValues(String message, boolean[] values, ResultType type, ResultUnit unit) {
        try {
            store.addArrayResult(message, values);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Adds a String List of metrics to the report.
     */
    @Override
    public void addValues(String message, List<String> values, ResultType type, ResultUnit unit) {
        try {
            store.addListResult(message, values);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Sets the summary double metric of the report.
     *
     * NOTE: messages over {@value Metric#MAX_MESSAGE_LENGTH} chars will be trimmed.
     */
    @Override
    public void setSummary(String message, double value, ResultType type, ResultUnit unit) {
        super.setSummary(message, value, type, unit);
        try {
            store.addResult(message, value);
        } catch (Exception e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    /**
     * Closes report file and submits report to instrumentation.
     */
    public void submit(Instrumentation instrumentation) {
        try {
            store.close();
            Bundle output = new Bundle();
            output.putString(RESULT, serialize(this));
            instrumentation.sendStatus(INST_STATUS_IN_PROGRESS, output);
        } catch (Exception e) {
            Log.e(TAG, "ReportLog Submit Failed", e);
            instrumentation.sendStatus(INST_STATUS_ERROR, null);
        }
    }

    /**
     * Closes report file. Static functions that do not have access to instrumentation can
     * use this to close report logs. Summary, if present, is not reported to instrumentation, hence
     * does not appear in the result XML.
     */
    public void submit() {
        try {
            store.close();
        } catch (Exception e) {
            Log.e(TAG, "ReportLog Submit Failed", e);
        }
    }
}
