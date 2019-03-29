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
import android.os.Parcel;
import android.util.Log;

import java.util.List;

/**
 * Test the {@link GnssNavigationMessage} values.
 *
 * Test steps:
 * 1. Register for {@link GnssNavigationMessage}s.
 * 2. Wait for {@link #EVENTS_COUNT} events to arrive.
 * 3. Check {@link GnssNavigationMessage} status: if the status is not
 *    {@link GnssNavigationMessage.Callback#STATUS_READY}, the test will be skipped because one of
 *    the following reasons:
 *          3.1 the device does not support the feature,
 *          3.2 GPS is disabled in the device,
 *          3.3 Location is disabled in the device.
 * 4. Verify {@link GnssNavigationMessage}s (all mandatory fields), the test will fail if any of the
 *    mandatory fields is not populated or in the expected range.
 */
public class GnssNavigationMessageTest extends GnssTestCase {

    private static final String TAG = "GpsNavMsgTest";
    private static final int EVENTS_COUNT = 5;
    private TestGnssNavigationMessageListener mTestGnssNavigationMessageListener;

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
        super.tearDown();
    }

    /**
     * Tests that one can listen for {@link GnssNavigationMessage}s for collection purposes.
     * It only performs sanity checks for the Navigation messages received.
     * This tests uses actual data retrieved from GPS HAL.
     */
    public void testGnssNavigationMessageMandatoryFieldRanges() throws Exception {
        // Checks if GPS hardware feature is present, skips test (pass) if not,
        // and hard asserts that Location/GPS (Provider) is turned on if is Cts Verifier.
        if (!TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager,
                TAG, MIN_HARDWARE_YEAR_MEASUREMENTS_REQUIRED, isCtsVerifierTest())) {
            return;
        }

        // Register Gps Navigation Message Listener.
        mTestGnssNavigationMessageListener =
                new TestGnssNavigationMessageListener(TAG, EVENTS_COUNT);
        mTestLocationManager
                .registerGnssNavigationMessageCallback(mTestGnssNavigationMessageListener);

        boolean success = mTestGnssNavigationMessageListener.await();

        if (!mTestGnssNavigationMessageListener.verifyState()) {
            return;
        }
        SoftAssert.failOrWarning(isMeasurementTestStrict(),
            "Time elapsed without getting enough navigation messages."
                + " Possibly, the test has been run deep indoors."
                + " Consider retrying test outdoors.",
            success);


        List<GnssNavigationMessage> events = mTestGnssNavigationMessageListener.getEvents();

        // Verify mandatory GnssNavigationMessage field values.
        TestMeasurementUtil.verifyGnssNavMessageMandatoryField(mTestLocationManager, events);
    }

    private static void setTestValues(GnssNavigationMessage message) {
        message.setData(new byte[] {1, 2, 3, 4});
        message.setMessageId(5);
        message.setStatus(GnssNavigationMessage.STATUS_PARITY_REBUILT);
        message.setSubmessageId(6);
        message.setSvid(7);
        message.setType(GnssNavigationMessage.TYPE_GPS_L2CNAV);
    }

    private static void verifyTestValues(GnssNavigationMessage message) {
        byte[] data = message.getData();
        assertEquals(4, data.length);
        assertEquals(1, data[0]);
        assertEquals(2, data[1]);
        assertEquals(3, data[2]);
        assertEquals(4, data[3]);
        assertEquals(5, message.getMessageId());
        assertEquals(GnssNavigationMessage.STATUS_PARITY_REBUILT, message.getStatus());
        assertEquals(6, message.getSubmessageId());
        assertEquals(7, message.getSvid());
        assertEquals(GnssNavigationMessage.TYPE_GPS_L2CNAV, message.getType());
    }

    public void testDescribeContents() {
        GnssNavigationMessage message = new GnssNavigationMessage();
        message.describeContents();
    }

    public void testWriteToParcel() {
        GnssNavigationMessage message = new GnssNavigationMessage();
        setTestValues(message);
        Parcel parcel = Parcel.obtain();
        message.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssNavigationMessage newMessage =
                GnssNavigationMessage.CREATOR.createFromParcel(parcel);
        verifyTestValues(newMessage);
    }

    public void testReset() {
        GnssNavigationMessage message = new GnssNavigationMessage();
        message.reset();
    }

    public void testSet() {
        GnssNavigationMessage message = new GnssNavigationMessage();
        setTestValues(message);
        GnssNavigationMessage newMessage = new GnssNavigationMessage();
        newMessage.set(message);
        verifyTestValues(newMessage);
    }
}
