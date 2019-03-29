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

package android.hardware.cts.helpers.sensorverification;

import junit.framework.Assert;

import android.hardware.Sensor;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;

import java.util.LinkedList;
import java.util.List;

/**
 * A {@link ISensorVerification} which verifies that all events are received in the correct order.
 */
public class EventOrderingVerification extends AbstractSensorVerification {
    public static final String PASSED_KEY = "event_out_of_order_passed";

    // Number of indices to print in assert message before truncating
    private static final int TRUNCATE_MESSAGE_LENGTH = 3;

    private Long mMaxTimestamp = null;
    private final List<IndexedEventPair> mOutOfOrderEvents = new LinkedList<IndexedEventPair>();
    private TestSensorEvent mPreviousEvent = null;
    private int mIndex = 0;

    /**
     * Get the default {@link EventOrderingVerification} for a sensor.
     *
     * @param environment the test environment
     * @return the verification or null if the verification does not apply to the sensor.
     */
    @SuppressWarnings("deprecation")
    public static EventOrderingVerification getDefault(TestSensorEnvironment environment) {
        int reportingMode = environment.getSensor().getReportingMode();
        if (reportingMode == Sensor.REPORTING_MODE_ONE_SHOT)
            return null;
        return new EventOrderingVerification();
    }

    /**
     * Verify that the events are in the correct order.  Add {@value #PASSED_KEY},
     * {@value SensorStats#EVENT_OUT_OF_ORDER_COUNT_KEY}, and
     * {@value SensorStats#EVENT_OUT_OF_ORDER_POSITIONS_KEY} keys to {@link SensorStats}.
     *
     * @throws AssertionError if the verification failed.
     */
    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        verify(stats);
    }

    /**
     * Visible for unit tests only.
     */
    void verify(SensorStats stats) {
        final int count = mOutOfOrderEvents.size();
        stats.addValue(PASSED_KEY, count == 0);
        stats.addValue(SensorStats.EVENT_OUT_OF_ORDER_COUNT_KEY, count);
        stats.addValue(
                SensorStats.EVENT_OUT_OF_ORDER_POSITIONS_KEY,
                getIndexArray(mOutOfOrderEvents));

        if (count > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(count).append(" events out of order: ");
            for (int i = 0; i < Math.min(count, TRUNCATE_MESSAGE_LENGTH); i++) {
                IndexedEventPair info = mOutOfOrderEvents.get(i);
                sb.append(String.format("position=%d, previous_ts=%.2fms, current_ts=%.2fms",
                            info.index, nanosToMillis(info.previousEvent.timestamp),
                            nanosToMillis(info.event.timestamp)));
            }
            if (count > TRUNCATE_MESSAGE_LENGTH) {
                sb.append(count - TRUNCATE_MESSAGE_LENGTH).append(" more");
            } else {
                // Delete the trailing "; "
                sb.delete(sb.length() - 2, sb.length());
            }

            Assert.fail(sb.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventOrderingVerification clone() {
        return new EventOrderingVerification();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        if (mPreviousEvent == null) {
            mMaxTimestamp = event.timestamp;
        } else {
            if (event.timestamp <= mMaxTimestamp) {
                mOutOfOrderEvents.add(new IndexedEventPair(mIndex, event, mPreviousEvent));
            } else {
                mMaxTimestamp = event.timestamp;
            }
        }

        mPreviousEvent = event;
        mIndex++;
    }
}
