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
package com.android.compatibility.common.util;

/**
 * Represents a single test result.
 */
public class TestResult implements ITestResult {

    private final ICaseResult mParent;
    private final String mTestName;
    private TestStatus mResult;
    private String mMessage;
    private String mStackTrace;
    private ReportLog mReport;
    private String mBugReport;
    private String mLog;
    private String mScreenshot;
    private boolean mIsRetry;
    private boolean mSkipped;

    /**
     * Create a {@link TestResult} for the given test name.
     */
    public TestResult(ICaseResult parent, String name) {
        mParent = parent;
        mTestName = name;
        reset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return mTestName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFullName() {
        return String.format("%s#%s", mParent.getName(), getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestStatus getResultStatus() {
        return mResult;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setResultStatus(TestStatus status) {
        mResult = status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMessage() {
        return mMessage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMessage(String message) {
        mMessage = message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStackTrace() {
        return mStackTrace;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStackTrace(String stackTrace) {
        mStackTrace = sanitizeStackTrace(stackTrace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReportLog getReportLog() {
        return mReport;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setReportLog(ReportLog report) {
        mReport = report;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBugReport() {
        return mBugReport;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBugReport(String path) {
        mBugReport = path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLog() {
        return mLog;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLog(String path) {
        mLog = path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getScreenshot() {
        return mScreenshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setScreenshot(String path) {
        mScreenshot = path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void failed(String trace) {
        mSkipped = false;
        setResultStatus(TestStatus.FAIL);
        int index = trace.indexOf('\n');
        if (index < 0) {
            // Trace is a single line, just set the message to be the same as the stacktrace.
            setMessage(trace);
        } else {
            setMessage(trace.substring(0, index));
        }
        setStackTrace(trace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void passed(ReportLog report) {
        mSkipped = false;
        if (getResultStatus() != TestStatus.FAIL) {
            setResultStatus(TestStatus.PASS);
            if (report != null) {
                setReportLog(report);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void skipped() {
        if (getResultStatus() == null) {
            mSkipped = true;
            setResultStatus(TestStatus.PASS);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSkipped() {
        return mSkipped;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        mResult = null;
        mMessage = null;
        mStackTrace = null;
        mReport = null;
        mBugReport = null;
        mLog = null;
        mScreenshot = null;
        mIsRetry = false;
        mSkipped = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRetry(boolean isRetry) {
        mIsRetry = isRetry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRetry() {
        return mIsRetry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeResult() {
        setResultStatus(TestStatus.FAIL);
        setStackTrace("");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(ITestResult another) {
        return getName().compareTo(another.getName());
    }

    /**
     * Strip out any invalid XML characters that might cause the report to be unviewable.
     * http://www.w3.org/TR/REC-xml/#dt-character
     */
    static String sanitizeStackTrace(String trace) {
        if (trace != null) {
            return trace.replaceAll("[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD]", "");
        } else {
            return null;
        }
    }

}
