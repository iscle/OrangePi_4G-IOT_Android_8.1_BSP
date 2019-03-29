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

package android.location.cts;

import android.location.GnssMeasurement;
import android.location.GnssStatus;
import android.os.Parcel;

public class GnssMeasurementTest extends GnssTestCase {
    public void testDescribeContents() {
        GnssMeasurement measurement = new GnssMeasurement();
        measurement.describeContents();
    }

    public void testReset() {
        GnssMeasurement measurement = new GnssMeasurement();
        measurement.reset();
    }

    private static void setTestValues(GnssMeasurement measurement) {
        measurement.setAccumulatedDeltaRangeMeters(1.0);
        measurement.setAccumulatedDeltaRangeState(2);
        measurement.setAccumulatedDeltaRangeUncertaintyMeters(3.0);
        measurement.setCarrierCycles(4);
        measurement.setCarrierFrequencyHz(5.0f);
        measurement.setCarrierPhase(6.0);
        measurement.setCarrierPhaseUncertainty(7.0);
        measurement.setCn0DbHz(8.0);
        measurement.setConstellationType(GnssStatus.CONSTELLATION_GALILEO);
        measurement.setMultipathIndicator(GnssMeasurement.MULTIPATH_INDICATOR_DETECTED);
        measurement.setPseudorangeRateMetersPerSecond(9.0);
        measurement.setPseudorangeRateUncertaintyMetersPerSecond(10.0);
        measurement.setReceivedSvTimeNanos(11);
        measurement.setReceivedSvTimeUncertaintyNanos(12);
        measurement.setSnrInDb(13.0);
        measurement.setState(14);
        measurement.setSvid(15);
        measurement.setTimeOffsetNanos(16.0);
    }

    private static void verifyTestValues(GnssMeasurement measurement) {
        assertEquals(1.0, measurement.getAccumulatedDeltaRangeMeters());
        assertEquals(2, measurement.getAccumulatedDeltaRangeState());
        assertEquals(3.0, measurement.getAccumulatedDeltaRangeUncertaintyMeters());
        assertEquals(4, measurement.getCarrierCycles());
        assertEquals(5.0f, measurement.getCarrierFrequencyHz());
        assertEquals(6.0, measurement.getCarrierPhase());
        assertEquals(7.0, measurement.getCarrierPhaseUncertainty());
        assertEquals(8.0, measurement.getCn0DbHz());
        assertEquals(GnssStatus.CONSTELLATION_GALILEO, measurement.getConstellationType());
        assertEquals(GnssMeasurement.MULTIPATH_INDICATOR_DETECTED,
                measurement.getMultipathIndicator());
        assertEquals(9.0, measurement.getPseudorangeRateMetersPerSecond());
        assertEquals(10.0, measurement.getPseudorangeRateUncertaintyMetersPerSecond());
        assertEquals(11, measurement.getReceivedSvTimeNanos());
        assertEquals(12, measurement.getReceivedSvTimeUncertaintyNanos());
        assertEquals(13.0, measurement.getSnrInDb());
        assertEquals(14, measurement.getState());
        assertEquals(15, measurement.getSvid());
        assertEquals(16.0, measurement.getTimeOffsetNanos());
    }

    public void testWriteToParcel() {
        GnssMeasurement measurement = new GnssMeasurement();
        setTestValues(measurement);
        Parcel parcel = Parcel.obtain();
        measurement.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssMeasurement newMeasurement = GnssMeasurement.CREATOR.createFromParcel(parcel);
        verifyTestValues(newMeasurement);
    }

    public void testSet() {
        GnssMeasurement measurement = new GnssMeasurement();
        setTestValues(measurement);
        GnssMeasurement newMeasurement = new GnssMeasurement();
        newMeasurement.set(measurement);
        verifyTestValues(newMeasurement);
    }

    public void testSetReset() {
        GnssMeasurement measurement = new GnssMeasurement();
        setTestValues(measurement);

        assertTrue(measurement.hasCarrierCycles());
        measurement.resetCarrierCycles();
        assertFalse(measurement.hasCarrierCycles());

        assertTrue(measurement.hasCarrierFrequencyHz());
        measurement.resetCarrierFrequencyHz();
        assertFalse(measurement.hasCarrierFrequencyHz());

        assertTrue(measurement.hasCarrierPhase());
        measurement.resetCarrierPhase();
        assertFalse(measurement.hasCarrierPhase());

        assertTrue(measurement.hasCarrierPhaseUncertainty());
        measurement.resetCarrierPhaseUncertainty();
        assertFalse(measurement.hasCarrierPhaseUncertainty());

        assertTrue(measurement.hasSnrInDb());
        measurement.resetSnrInDb();
        assertFalse(measurement.hasSnrInDb());
    }
}
