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

package android.hardware.cts;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorAdditionalInfo;
import android.hardware.SensorEventCallback;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Checks Sensor Additional Information feature functionality.
 */
public class SensorAdditionalInfoTest extends SensorTestCase {
    private static final String TAG = "SensorAdditionalInfoTest";
    private static final int ALLOWED_ADDITIONAL_INFO_DELIVER_SEC = 3;
    private static final int REST_PERIOD_BEFORE_TEST_SEC = 3;

    private SensorManager mSensorManager;

    @Override
    protected void setUp() throws Exception {
        mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
    }

    public void testSensorAdditionalInfo() {
        if (mSensorManager == null) {
            return;
        }

        List<Sensor> list = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        List<String> errors = new ArrayList<String>();
        for (Sensor s : list) {
            // skip vendor sensor types and those that do not support additional info
            // one-shot and on-change sensors are not supported as well
            if (s.getType() >= Sensor.TYPE_DEVICE_PRIVATE_BASE ||
                    !s.isAdditionalInfoSupported() ||
                    s.getReportingMode() == Sensor.REPORTING_MODE_ONE_SHOT ||
                    s.getReportingMode() == Sensor.REPORTING_MODE_ON_CHANGE) {
                continue;
            }
            try {
                runSensorAdditionalInfoTest(s);
            } catch (AssertionError e) {
                errors.add("Sensor: " + s.getName() + ", error: " + e.getMessage());
            }
        }
        if (errors.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("Failed for following reasons: [");
            int i = 0;
            for (String error : errors) {
                sb.append(String.format("%d. %s; ", i++, error));
            }
            sb.append("]");
            fail(sb.toString());
        }
    }

    private void runSensorAdditionalInfoTest(Sensor s) throws AssertionError {
        waitBeforeTestStarts();

        AdditionalInfoVerifier verifier = new AdditionalInfoVerifier(s);
        verifier.reset(false /*flushPending*/);

        assertTrue(String.format("Register sensor listener for %s failed.", s.getName()),
                mSensorManager.registerListener(verifier, s, SensorManager.SENSOR_DELAY_NORMAL));
        try {
            assertTrue("Missing additional info at registration: (" + verifier.getState() + ")",
                    verifier.verify());

            verifier.reset(true /*flushPending*/);
            assertTrue("Flush sensor failed.", mSensorManager.flush(verifier));
            assertTrue("Missing additional info after flushing: (" + verifier.getState() + ")",
                    verifier.verify());
        } finally {
            mSensorManager.unregisterListener(verifier);
        }
    }

    private void waitBeforeTestStarts() {
        // wait for sensor system to come to a rest after previous test to avoid flakiness.
        try {
            SensorCtsHelper.sleep(REST_PERIOD_BEFORE_TEST_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private class AdditionalInfoVerifier extends SensorEventCallback {
        private boolean mBeginFrame = false;
        private boolean mEndFrame = false;
        private boolean mFlushPending = false;
        private CountDownLatch mDone;
        private final Sensor mSensor;

        public AdditionalInfoVerifier(Sensor s) {
            mSensor = s;
        }

        @Override
        public void onFlushCompleted(Sensor sensor) {
            if (sensor == mSensor) {
                mFlushPending = false;
            }
        }

        @Override
        public void onSensorAdditionalInfo(SensorAdditionalInfo info) {
            if (info.sensor == mSensor && !mFlushPending) {
                if (info.type == SensorAdditionalInfo.TYPE_FRAME_BEGIN) {
                    mBeginFrame = true;
                } else if (info.type == SensorAdditionalInfo.TYPE_FRAME_END && mBeginFrame) {
                    mEndFrame = true;
                    mDone.countDown();
                }
            }
        }

        public void reset(boolean flushPending) {
            mFlushPending = flushPending;
            mBeginFrame = false;
            mEndFrame = false;
            mDone = new CountDownLatch(1);
        }

        public boolean verify() {
            boolean ret;
            try {
                ret = mDone.await(ALLOWED_ADDITIONAL_INFO_DELIVER_SEC, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                ret = false;
            }
            return ret;
        }

        public String getState() {
            return "fp=" + mFlushPending +", b=" + mBeginFrame + ", e=" + mEndFrame;
        }
    }
}

