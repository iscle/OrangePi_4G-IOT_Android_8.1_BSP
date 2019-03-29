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
package com.android.compatibility.common.tradefed.testtype;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.util.RunUtil;

public class FailureListener extends ResultForwarder {

    private static final int DEFAULT_MAX_LOGCAT_BYTES = 500 * 1024; // 500K
    /* Arbitrary upper limit for mMaxLogcatBytes, per b/30720850 */
    public static final int LOGCAT_BYTE_LIMIT = 20 * 1024 * 1024; // 20 MB

    private ITestDevice mDevice;
    private boolean mBugReportOnFailure;
    private boolean mLogcatOnFailure;
    private boolean mScreenshotOnFailure;
    private boolean mRebootOnFailure;
    private int mMaxLogcatBytes;

    public FailureListener(ITestInvocationListener listener, ITestDevice device,
            boolean bugReportOnFailure, boolean logcatOnFailure, boolean screenshotOnFailure,
            boolean rebootOnFailure, int maxLogcatBytes) {
        super(listener);
        mDevice = device;
        mBugReportOnFailure = bugReportOnFailure;
        mLogcatOnFailure = logcatOnFailure;
        mScreenshotOnFailure = screenshotOnFailure;
        mRebootOnFailure = rebootOnFailure;
        if (maxLogcatBytes < 0 ) {
            CLog.w("FailureListener could not set %s to '%d', using default value %d",
                    CompatibilityTest.LOGCAT_ON_FAILURE_SIZE_OPTION, maxLogcatBytes,
                    DEFAULT_MAX_LOGCAT_BYTES);
            mMaxLogcatBytes = DEFAULT_MAX_LOGCAT_BYTES;
        } else if (maxLogcatBytes > LOGCAT_BYTE_LIMIT) {
            CLog.w("Value %d for %s exceeds limit %d, using limit value", maxLogcatBytes,
                    CompatibilityTest.LOGCAT_ON_FAILURE_SIZE_OPTION, LOGCAT_BYTE_LIMIT);
            mMaxLogcatBytes = LOGCAT_BYTE_LIMIT;
        } else {
            mMaxLogcatBytes = maxLogcatBytes;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        super.testFailed(test, trace);
        CLog.i("FailureListener.testFailed %s %b %b %b",
                test.toString(), mBugReportOnFailure, mLogcatOnFailure, mScreenshotOnFailure);
        if (mScreenshotOnFailure) {
            try {
                try (InputStreamSource screenSource = mDevice.getScreenshot()) {
                    super.testLog(String.format("%s-screenshot", test.toString()), LogDataType.PNG,
                        screenSource);
                }
            } catch (DeviceNotAvailableException e) {
                CLog.e(e);
                CLog.e("Device %s became unavailable while capturing screenshot",
                    mDevice.getSerialNumber());
            }
        }
        if (mBugReportOnFailure) {
            int api = -1;
            try {
                api = mDevice.getApiLevel();
            } catch (DeviceNotAvailableException e) {
                // ignore, it will be raised later.
            }
            if (api < 24) {
                try (InputStreamSource fallback = mDevice.getBugreport()) {
                    super.testLog(String.format("%s-bugreport", test.toString()),
                            LogDataType.BUGREPORT, fallback);
                }
            } else {
                try (InputStreamSource bugSource = mDevice.getBugreportz()) {
                    if (bugSource != null) {
                        super.testLog(String.format("%s-bugreportz", test.toString()),
                                LogDataType.BUGREPORTZ, bugSource);
                    } else {
                        CLog.e("Failed to capture bugreport for %s", test.toString());
                    }
                }
            }
        }
        if (mLogcatOnFailure) {
            // sleep 2s to ensure test failure stack trace makes it into logcat capture
            RunUtil.getDefault().sleep(2 * 1000);
            try (InputStreamSource logSource = mDevice.getLogcat(mMaxLogcatBytes)) {
                super.testLog(String.format("%s-logcat", test.toString()), LogDataType.LOGCAT,
                        logSource);
            }
        }
        if (mRebootOnFailure) {
            try {
                // Rebooting on all failures can hide legitimate issues and platform instabilities,
                // therefore only allowed on "user-debug" and "eng" builds.
                if ("user".equals(mDevice.getProperty("ro.build.type"))) {
                    CLog.e("Reboot-on-failure should only be used during development," +
                            " this is a\" user\" build device");
                } else {
                    mDevice.reboot();
                }
            } catch (DeviceNotAvailableException e) {
                CLog.e(e);
                CLog.e("Device %s became unavailable while rebooting",
                        mDevice.getSerialNumber());
            }
        }
    }

}
