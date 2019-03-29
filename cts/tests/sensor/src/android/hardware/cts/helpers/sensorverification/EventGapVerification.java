package android.hardware.cts.helpers.sensorverification;

import junit.framework.Assert;

import android.hardware.Sensor;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ISensorVerification} which verifies that there are no missing events. This is done by
 * checking the last received sensor timestamp and checking that it is within 1.8 * the expected
 * period.
 */
public class EventGapVerification extends AbstractSensorVerification {
    public static final String PASSED_KEY = "missing_event_passed";

    // Fail if no events are delivered within 1.8 times the expected interval
    private static final double THRESHOLD = 1.8;

    // Number of indices to print in assert message before truncating
    private static final int TRUNCATE_MESSAGE_LENGTH = 3;

    // Number of events to truncate (discard) from the initial events received
    private static final int TRUNCATE_EVENTS_COUNT = 1;

    // Number of event gaps to tolerate is 2% of total number of events received rounded up to next
    // integer or 20, whichever is smaller.
    private static final int EVENT_GAP_THRESHOLD_MAX = 20;
    private static final double EVENT_GAP_TOLERANCE = 0.02;

    private final int mExpectedDelayUs;

    private final List<IndexedEventPair> mEventGaps = new LinkedList<IndexedEventPair>();
    private TestSensorEvent mPreviousEvent = null;
    private int mEventCount = 0;

    /**
     * Construct a {@link EventGapVerification}
     *
     * @param expectedDelayUs the expected period in us.
     */
    public EventGapVerification(int expectedDelayUs) {
        mExpectedDelayUs = expectedDelayUs;
    }

    /**
     * Get the default {@link EventGapVerification}.
     *
     * @param environment the test environment
     * @return the verification or null if the verification is not a continuous mode sensor.
     */
    public static EventGapVerification getDefault(TestSensorEnvironment environment) {
        if (environment.getSensor().getReportingMode() != Sensor.REPORTING_MODE_CONTINUOUS) {
            return null;
        }
        return new EventGapVerification(environment.getExpectedSamplingPeriodUs());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        if (environment.isSensorSamplingRateOverloaded()) {
            // the verification is not reliable on environments under load
            stats.addValue(PASSED_KEY, "skipped (under load)");
            return;
        }

        final int count = mEventGaps.size();
        // Ensure the threshold is rounded up.
        double eventGapThreshold =
                Math.ceil(Math.min(EVENT_GAP_THRESHOLD_MAX, mEventCount * EVENT_GAP_TOLERANCE));
        boolean pass = count <= eventGapThreshold;

        stats.addValue(PASSED_KEY, pass);
        stats.addValue(SensorStats.EVENT_GAP_COUNT_KEY, count);
        stats.addValue(SensorStats.EVENT_GAP_POSITIONS_KEY, getIndexArray(mEventGaps));

        if (!pass) {
            StringBuilder sb = new StringBuilder();
            sb.append(count).append(" events gaps: ");
            for (int i = 0; i < Math.min(count, TRUNCATE_MESSAGE_LENGTH); i++) {
                IndexedEventPair info = mEventGaps.get(i);
                sb.append(String.format("position=%d, delta_time=%.2fms; ", info.index,
                        nanosToMillis(info.event.timestamp - info.previousEvent.timestamp)));
            }
            if (count > TRUNCATE_MESSAGE_LENGTH) {
                sb.append(count - TRUNCATE_MESSAGE_LENGTH).append(" more; ");
            }
            sb.append(String.format("(expected <%.2fms)",
                    (double)(THRESHOLD * mExpectedDelayUs)/1000.0));
            Assert.fail(sb.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventGapVerification clone() {
        return new EventGapVerification(mExpectedDelayUs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        if (mEventCount >= TRUNCATE_EVENTS_COUNT) {
            if (mPreviousEvent != null) {
                long deltaNs = event.timestamp - mPreviousEvent.timestamp;
                long deltaUs = TimeUnit.MICROSECONDS.convert(deltaNs, TimeUnit.NANOSECONDS);
                if (deltaUs > mExpectedDelayUs * THRESHOLD) {
                    mEventGaps.add(new IndexedEventPair(mEventCount, event, mPreviousEvent));
                }
            }
            mPreviousEvent = event;
        }
        mEventCount++;
    }
}
