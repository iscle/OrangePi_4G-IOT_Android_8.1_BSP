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

import android.location.GnssNavigationMessage;
import android.util.Log;

import java.util.List;

/**
 * Test the {@link GnssNavigationMessage} without location registration.
 *
 * Test steps:
 * 1. Register for {@link GnssNavigationMessage}s.
 * 2. Wait for {@link #EVENTS_COUNT} events to arrive.
 * 3. Check {@link GnssNavigationMessage} status: if the status is not
 *    {@link GnssNavigationMessage#Callback#STATUS_READY}, the test will be skipped because one of the
 *    following reasons:
 *          3.1 the device does not support the feature,
 *          3.2 GPS is disabled in the device,
 *          3.3 Location is disabled in the device.
 * 4. If at least one {@link GnssNavigationMessage} is received, the test will pass.
 * 5. If no {@link GnssNavigationMessage}s are received, then check whether the device is
 *    deep indoor. This is done by performing the following steps:
 *          2.1 Register for location updates, and {@link GpsStatus} events.
 *          2.2 Wait for {@link TestGpsStatusListener#TIMEOUT_IN_SEC}.
 *          2.3 If no {@link GpsStatus} is received this will mean that the device is located
 *              indoor. Test will be skipped.
 *          2.4 If we receive a {@link GpsStatus}, it mean that {@link GnssNavigationMessage}s
 *              are provided only if the application registers for location updates as well:
 *                  2.4.1 The test will pass with a warning for the M release.
 *                  2.4.2 The test might fail in a future Android release, when this requirement
 *                        becomes mandatory.
 */
public class GnssNavigationMessageRegistrationTest extends GnssTestCase {

    private static final String TAG = "GpsNavMsgRegTest";
    private static final int EVENTS_COUNT = 5;
    private TestGnssNavigationMessageListener mTestGnssNavigationMessageListener;
    private TestLocationListener mLocationListener;
    private TestGpsStatusListener mGpsStatusListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestLocationManager = new TestLocationManager(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        // Unregister GnssNavigationMessageListener
        if (mTestGnssNavigationMessageListener != null) {
            mTestLocationManager
                    .unregisterGnssNavigationMessageCallback(mTestGnssNavigationMessageListener);
            mTestGnssNavigationMessageListener = null;
        }
        if (mLocationListener != null) {
            mTestLocationManager.removeLocationUpdates(mLocationListener);
        }
        if (mGpsStatusListener != null) {
            mTestLocationManager.removeGpsStatusListener(mGpsStatusListener);
        }
        super.tearDown();
    }

    /**
     * Tests that one can listen for {@link GnssNavigationMessage}s for collection purposes.
     * It only performs sanity checks for the Navigation messages received.
     */
    public void testGnssNavigationMessageRegistration() throws Exception {
        // Checks if GPS hardware feature is present, skips test (pass) if not,
        // and hard asserts that Location/GPS (Provider) is turned on if is Cts Verifier.
        if (!TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager,
                TAG, MIN_HARDWARE_YEAR_MEASUREMENTS_REQUIRED, isCtsVerifierTest())) {
            return;
        }

        // Register Gps Navigation Message Listener.
        mTestGnssNavigationMessageListener =
                new TestGnssNavigationMessageListener(TAG, EVENTS_COUNT);
        mTestLocationManager.registerGnssNavigationMessageCallback(mTestGnssNavigationMessageListener);

        mTestGnssNavigationMessageListener.await();
        if (!mTestGnssNavigationMessageListener.verifyState()) {
            return;
        }

        List<GnssNavigationMessage> events = mTestGnssNavigationMessageListener.getEvents();
        if (!events.isEmpty()) {
            // Verify mandatory GnssNavigationMessage field values.
            TestMeasurementUtil.verifyGnssNavMessageMandatoryField(mTestLocationManager, events);
            // Test passes if we get at least 1 GPS Navigation Message event.
            Log.i(TAG, "Received GPS Navigation Message. Test Pass.");
            return;
        }

        // If no {@link GnssNavigationMessage}s are received, then check whether the device is
        // deep indoor.
        Log.i(TAG, "Did not receive any GPS Navigation Message. Test if device is deep indoor.");

        // Register for location updates.
        mLocationListener = new TestLocationListener(EVENTS_COUNT);
        mTestLocationManager.requestLocationUpdates(mLocationListener);

        // Wait for location updates
        mLocationListener.await();
        Log.i(TAG, "Location received = " + mLocationListener.isLocationReceived());

        // Register for Gps Status updates
        mGpsStatusListener = new TestGpsStatusListener(EVENTS_COUNT, mTestLocationManager);
        mTestLocationManager.addGpsStatusListener(mGpsStatusListener);

        // Wait for Gps Status updates
        mGpsStatusListener.await();
        if (!mGpsStatusListener.isGpsStatusReceived()) {
            // Skip the Test. No Satellites are visible. Device may be Indoor
            Log.i(TAG, "No Satellites are visible. Device may be Indoor. Skipping Test.");
            return;
        }

        SoftAssert.failAsWarning(
                TAG,
                "GPS Navigation Messages were not received without registering for location" +
                        " updates.");
    }
}
