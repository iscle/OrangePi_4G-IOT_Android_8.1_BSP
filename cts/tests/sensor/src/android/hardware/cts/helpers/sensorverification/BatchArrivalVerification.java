
package android.hardware.cts.helpers.sensorverification;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.hardware.Sensor;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.hardware.cts.helpers.sensorverification.AbstractSensorVerification.IndexedEventPair;
import android.os.SystemClock;
import android.provider.Settings.System;

import junit.framework.Assert;

/**
 * A {@link ISensorVerification} which verifies that there are no missing events. This is done by
 * checking the last received sensor timestamp and checking that it is within 1.8 * the expected
 * period.
 */
public class BatchArrivalVerification extends AbstractSensorVerification {
    public static final String PASSED_KEY = "missing_event_passed";

    // Batch arrival tolerance is 5 seconds.
    private static final int BATCH_ARRIVAL_TOLERANCE_US = 5000000;

    // Number of indices to print in assert message before truncating
    private static final int TRUNCATE_MESSAGE_LENGTH = 3;

    // Number of events to truncate (discard) from the initial events received
    private static final int TRUNCATE_EVENTS_COUNT = 100;

    private final long mExpectedBatchArrivalTimeUs;

    private final List<IndexedEventPair> mFailures = new LinkedList<IndexedEventPair>();
    private TestSensorEvent mFirstEvent = null;
    private int mIndex = 0;
    private final long mEstimatedTestStartTimeMs;

    /**
     * Construct a {@link EventGapVerification}
     *
     * @param expectedDelayUs the expected period in us.
     */
    public BatchArrivalVerification(long expectedBatchArrivalTimeUs) {
         mExpectedBatchArrivalTimeUs = expectedBatchArrivalTimeUs;
         mEstimatedTestStartTimeMs = SystemClock.elapsedRealtime();
    }

    /**
     * Get the default {@link EventGapVerification}.
     *
     * @param environment the test environment
     * @return the verification or null if the verification is not a continuous mode sensor.
     */
    public static BatchArrivalVerification getDefault(TestSensorEnvironment environment) {
        if (environment.getSensor().getReportingMode() != Sensor.REPORTING_MODE_CONTINUOUS) {
            return null;
        }
        long fifoMaxEventCount = environment.getSensor().getFifoMaxEventCount();
        int maximumExpectedSamplingPeriodUs = environment.getMaximumExpectedSamplingPeriodUs();
        long reportLatencyUs = environment.getMaxReportLatencyUs();
        if (fifoMaxEventCount > 0 && maximumExpectedSamplingPeriodUs != Integer.MAX_VALUE) {
            long fifoBasedReportLatencyUs =
                    fifoMaxEventCount * maximumExpectedSamplingPeriodUs;
            // If the device goes into suspend mode during the test and the sensor under test is
            // a non wake-up sensor, the FIFO will keep overwriting itself and the reportLatency
            // of each event will be equal to the time it takes to fill up the FIFO.
            if (environment.isDeviceSuspendTest() && !environment.getSensor().isWakeUpSensor()) {
                reportLatencyUs = fifoBasedReportLatencyUs;
            } else {
                // In this case the sensor under test is either a wake-up sensor OR it
                // is a non wake-up sensor but the device does not go into suspend.
                // So the expected delay of a sensor_event is the minimum of the
                // fifoBasedReportLatencyUs and the requested latency by the application.
                reportLatencyUs = Math.min(reportLatencyUs, fifoBasedReportLatencyUs);
            }
        }
        long expectedBatchArrivalTimeUs = reportLatencyUs + SystemClock.elapsedRealtime() * 1000;
        return new BatchArrivalVerification(expectedBatchArrivalTimeUs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        final int count = mFailures.size();
        stats.addValue(PASSED_KEY, count == 0);
        stats.addValue(SensorStats.DELAYED_BATCH_DELIVERY, count);

        if (count > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(count).append(" BatchArrivalDelayed: ");
            for (int i = 0; i < Math.min(count, TRUNCATE_MESSAGE_LENGTH); i++) {
                IndexedEventPair info = mFailures.get(i);
                sb.append(String.format("expectedBatchArrival=%dms actualBatchArrivalTime=%dms "+
                                        "estimedTestStartTime=%dms diff=%dms tolerance=%dms",
                                         (mExpectedBatchArrivalTimeUs)/1000,
                                         info.event.receivedTimestamp/(1000 * 1000),
                                         mEstimatedTestStartTimeMs,
                                         (mExpectedBatchArrivalTimeUs -
                                          info.event.receivedTimestamp/1000)/1000,
                                         BATCH_ARRIVAL_TOLERANCE_US/1000)

                          );

            }
            if (count > TRUNCATE_MESSAGE_LENGTH) {
                sb.append(count - TRUNCATE_MESSAGE_LENGTH).append(" more; ");
            }
            Assert.fail(sb.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BatchArrivalVerification clone() {
        return new BatchArrivalVerification(mExpectedBatchArrivalTimeUs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        if (mFirstEvent == null) {
            mFirstEvent = event;
        }
        if (mIndex == 1) {
            if (Math.abs(mFirstEvent.receivedTimestamp/1000 - mExpectedBatchArrivalTimeUs) >
                BATCH_ARRIVAL_TOLERANCE_US) {
                mFailures.add(new IndexedEventPair(1, mFirstEvent, null));
            }
        }
        ++mIndex;
    }
}
