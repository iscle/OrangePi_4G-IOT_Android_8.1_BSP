/*
 * Copyright (C) 2015 Google Inc.
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
import android.location.GpsStatus;
import android.util.Log;

import java.util.List;

/**
 * Test for {@link GnssMeasurement}s without location registration.
 *
 * Test steps:
 * 1. Register a listener for {@link GnssMeasurementsEvent}s.
 * 2. Check {@link GnssMeasurementsEvent} status: if the status is not
 *    {@link GnssMeasurementsEvent#STATUS_READY}, the test will be skipped because one of the
 *    following reasons:
 *          2.1 the device does not support the feature,
 *          2.2 GPS is disabled in the device,
 *          2.3 Location is disabled in the device.
 * 3. If at least one {@link GnssMeasurementsEvent} is received, the test will pass.
 * 2. If no {@link GnssMeasurementsEvent} are received, then check whether the device is deep indoor.
 *    This is done by performing the following steps:
 *          2.1 Register for location updates, and {@link GpsStatus} events.
 *          2.2 Wait for {@link TestGpsStatusListener#TIMEOUT_IN_SEC}.
 *          2.3 If no {@link GpsStatus} is received this will mean that the device is located
 *              indoor. Test will be skipped.
 *          2.4 If we receive a {@link GpsStatus}, it mean that {@link GnssMeasurementsEvent}s are
 *              provided only if the application registers for location updates as well:
 *                  2.4.1 The test will pass with a warning for the M release.
 *                  2.4.2 The test might fail in a future Android release, when this requirement
 *                        becomes mandatory.
 */
public class GnssMeasurementRegistrationTest extends GnssTestCase {

    private static final String TAG = "GnssMeasRegTest";
    private static final int EVENTS_COUNT = 5;
    private static final int GPS_EVENTS_COUNT = 1;
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
     * Test GPS measurements registration.
     */
    public void testGnssMeasurementRegistration() throws Exception {
        // Checks if GPS hardware feature is present, skips test (pass) if not,
        // and hard asserts that Location/GPS (Provider) is turned on if is Cts Verifier.
        if (!TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager,
                TAG, MIN_HARDWARE_YEAR_MEASUREMENTS_REQUIRED, isCtsVerifierTest())) {
            return;
        }

        // Register for GPS measurements.
        mMeasurementListener = new TestGnssMeasurementListener(TAG, GPS_EVENTS_COUNT);
        mTestLocationManager.registerGnssMeasurementCallback(mMeasurementListener);

        mMeasurementListener.await();
        if (!mMeasurementListener.verifyStatus(isMeasurementTestStrict())) {
            // If test is strict verifyStatus will assert conditions are good for further testing.
            // Else this returns false and, we arrive here, and then return from here (pass.)
            return;
        }

        List<GnssMeasurementsEvent> events = mMeasurementListener.getEvents();
        Log.i(TAG, "Number of GnssMeasurement events received = " + events.size());

        if (!events.isEmpty()) {
           // Test passes if we get at least 1 pseudorange.
           Log.i(TAG, "Received GPS measurements. Test Pass.");
           return;
        }

        SoftAssert.failAsWarning(
                TAG,
                "GPS measurements were not received without registering for location updates. "
                + "Trying again with Location request.");

        // Register for location updates.
        mLocationListener = new TestLocationListener(EVENTS_COUNT);
        mTestLocationManager.requestLocationUpdates(mLocationListener);

        // Wait for location updates
        mLocationListener.await();
        Log.i(TAG, "Location received = " + mLocationListener.isLocationReceived());

        events = mMeasurementListener.getEvents();
        Log.i(TAG, "Number of GnssMeasurement events received = " + events.size());

        // If no measurements found, fail if strict (Verifier + 2016+)
        SoftAssert.failOrWarning(isMeasurementTestStrict(),
                "Did not receive any GnssMeasurement events.  Retry outdoors?",
                !events.isEmpty());
    }
}
