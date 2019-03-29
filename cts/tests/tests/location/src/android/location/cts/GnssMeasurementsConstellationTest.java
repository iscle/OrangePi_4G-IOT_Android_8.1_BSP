/*
 * Copyright (C) 2016 Google Inc.
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

package android.location.cts;

import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.util.Log;

import java.util.List;

import junit.framework.Assert;

/**
 * Test for {@link GnssMeasurement}s without location registration.
 *
 * Test steps:
 * 1. Register a listener for {@link GnssMeasurementsEvent}s and location updates.
 * 2. Check {@link GnssMeasurementsEvent} status: if the status is not
 *    {@link GnssMeasurementsEvent#STATUS_READY}, the test will be skipped because one of the
 *    following reasons:
 *          2.1 the device does not support the feature,
 *          2.2 GPS is disabled in the device,
 *          // TODO: This is true only for cts, for verifier mode we need to modify
 *                   TestGnssMeasurementListener to fail the test.
 *          2.3 Location is disabled in the device.
 * 3. If no {@link GnssMeasurementsEvent} is received then test is skipped in cts mode and fails in
 *    cts verifier mode.
 * 4. Check if one of the received measurements has constellation other than GPS.
 */
public class GnssMeasurementsConstellationTest extends GnssTestCase {

    private static final String TAG = "GnssConsTypeTest";
    private static final int EVENTS_COUNT = 5;
    private static final int GPS_EVENTS_COUNT = 3;
    private TestLocationListener mLocationListener;
    private TestGnssMeasurementListener mMeasurementListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTestLocationManager = new TestLocationManager(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        // Unregister listeners
        if (mLocationListener != null) {
            mTestLocationManager.removeLocationUpdates(mLocationListener);
        }
        if (mMeasurementListener != null) {
            mTestLocationManager.unregisterGnssMeasurementCallback(mMeasurementListener);
        }
        super.tearDown();
    }

    /**
     * Test Gnss multi constellation supported.
     */
    public void testGnssMultiConstellationSupported() throws Exception {
        // Checks if GPS hardware feature is present, skips test (pass) if not,
        // and hard asserts that Location/GPS (Provider) is turned on if is Cts Verifier.
        if (!TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager,
                TAG, MIN_HARDWARE_YEAR_MEASUREMENTS_REQUIRED, isCtsVerifierTest())) {
            return;
        }

        // Register for GPS measurements.
        mMeasurementListener = new TestGnssMeasurementListener(TAG, GPS_EVENTS_COUNT);
        mTestLocationManager.registerGnssMeasurementCallback(mMeasurementListener);

        // Register for location updates.
        mLocationListener = new TestLocationListener(EVENTS_COUNT);
        mTestLocationManager.requestLocationUpdates(mLocationListener);

        mMeasurementListener.await();
        if (!mMeasurementListener.verifyStatus(isMeasurementTestStrict())) {
            return;
        }

        List<GnssMeasurementsEvent> events = mMeasurementListener.getEvents();
        Log.i(TAG, "Number of GnssMeasurement events received = " + events.size());

        SoftAssert.failOrWarning(isMeasurementTestStrict(),
                "Did not receive any GnssMeasurement events.  Retry outdoors?",
                !events.isEmpty());

        SoftAssert softAssert = new SoftAssert(TAG);
        for (GnssMeasurementsEvent event : events) {
            // Verify Gps Event mandatory fields are in required ranges
            assertNotNull("GnssMeasurementEvent cannot be null.", event);
            long timeInNs = event.getClock().getTimeNanos();

            softAssert.assertTrue("time_ns: clock value",
                    timeInNs,
                    "X >= 0",
                    String.valueOf(timeInNs),
                    timeInNs >= 0L);
            boolean isExpectedConstellationType = false;
            int constellationType = 0;
            for (GnssMeasurement measurement : event.getMeasurements()) {
                constellationType = measurement.getConstellationType();

                // Checks if constellation type is other than CONSTELLATION_GPS
                // && CONSTELLATION_UNKNOWN.
                if (constellationType != GnssStatus.CONSTELLATION_GPS
                        && constellationType != GnssStatus.CONSTELLATION_UNKNOWN) {
                    isExpectedConstellationType = true;
                    break;
                }
            }

            // If test is running in CtsVerifier and multi constellation is not supported, then
            // throw MultiConstellationNotSupportedException which is used to indicate warning.
            if (isCtsVerifierTest() && !isExpectedConstellationType) {
                throw new MultiConstellationNotSupportedException(
                        "\n\n WARNING: Device does not support Multi-constellation. " +
                                "Device only supports GPS. " +
                                "This will be mandatory starting from Android-O.\n");
            }

            // In cts test just log it as warning if multi constellation is not supported
            softAssert.assertTrueAsWarning(
                    "Constellation type is other than CONSTELLATION_GPS",
                    timeInNs,
                    "ConstellationType != CONSTELLATION_GPS " +
                            "&& constellationType != GnssStatus.CONSTELLATION_UNKNOWN",
                    String.valueOf(constellationType),
                    isExpectedConstellationType);

        }
        softAssert.assertAll();
    }
}
