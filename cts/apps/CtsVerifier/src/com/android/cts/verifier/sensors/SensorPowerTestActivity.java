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

import com.android.cts.verifier.sensors.base.SensorCtsVerifierTestActivity;
import com.android.cts.verifier.sensors.helpers.PowerTestHostLink;
import com.android.cts.verifier.sensors.helpers.SensorTestScreenManipulator;
import com.android.cts.verifier.sensors.reporting.SensorTestDetails;

import junit.framework.Assert;

public class SensorPowerTestActivity
        extends SensorCtsVerifierTestActivity
        implements PowerTestHostLink.HostToDeviceInterface {

    private PowerTestHostLink mHostLink;
    private SensorTestScreenManipulator mScreenManipulator;

    public SensorPowerTestActivity() {
        super(SensorPowerTestActivity.class);
    }

    @Override
    public void waitForUserAcknowledgement(final String message) throws InterruptedException {
        appendText(message);
        waitForUserToContinue();
    }

    @Override
    public void raiseError(String testName, String message) throws Exception {
        getTestLogger().logTestFail(testName, message);
        throw new RuntimeException(message);
    }

    @Override
    public void logText(String text) {
        appendText(text);
    }

    @Override
    public void logTestResult(SensorTestDetails testDetails) {
        getTestLogger().logTestDetails(testDetails);
    }

    @Override
    public void turnScreenOff() {
        mScreenManipulator.turnScreenOffOnNextPowerDisconnect();
    }

    @Override
    protected void activitySetUp() throws InterruptedException {
        mScreenManipulator = new SensorTestScreenManipulator(this);
        mScreenManipulator.initialize(this);
    }

    @Override
    protected void activityCleanUp() throws InterruptedException {
        if (mHostLink != null) {
            mHostLink.close();
        }
        mScreenManipulator.close();
    }

    public String testSensorsPower() throws Throwable {
        if (mHostLink == null) {
            // prepare Activity screen to show instructions to the operator
            clearText();

            // ask the operator to set up the host
            appendText("Connect the device to the host machine via the USB passthrough.");
            appendText("Execute the following script (the command is available in CtsVerifier.zip):");
            appendText("    # python power/execute_power_tests.py --power_monitor <implementation> --run");
            appendText("where \"<implementation>\" is the power monitor implementation being used, for example \"monsoon\"");
            try {
                mHostLink = new PowerTestHostLink(this, this);

                appendText("Waiting for connection from Host...");

                // this will block until first connection from host,
                // and then allow the host to execute tests one by on
                // until it issues an "EXIT" command to break out
                // of the run loop. The host will run all associated tests
                // sequentially here:
                PowerTestHostLink.PowerTestResult testResult = mHostLink.run();

                SensorTestDetails testDetails = new SensorTestDetails(
                        getApplicationContext(),
                        "SensorPowerTest",
                        testResult.passedCount,
                        testResult.skippedCount,
                        testResult.failedCount);
                Assert.assertEquals(testDetails.getSummary(), 0, testResult.failedCount);
                return testDetails.getSummary();
            } finally {
                mHostLink.close();
                mHostLink = null;
            }
        } else {
            throw new IllegalStateException("Attempt to run test twice");
        }
    }
}
