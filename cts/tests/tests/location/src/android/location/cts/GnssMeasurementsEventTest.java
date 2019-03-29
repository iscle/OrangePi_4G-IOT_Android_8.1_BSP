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

import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.os.Parcel;

import java.util.Collection;
import java.util.Iterator;

public class GnssMeasurementsEventTest extends GnssTestCase {
    public void testDescribeContents() {
        GnssClock clock = new GnssClock();
        GnssMeasurement m1 = new GnssMeasurement();
        GnssMeasurement m2 = new GnssMeasurement();
        GnssMeasurementsEvent event = new GnssMeasurementsEvent(
                clock, new GnssMeasurement[] {m1, m2});
        event.describeContents();
    }

    public void testWriteToParcel() {
        GnssClock clock = new GnssClock();
        clock.setLeapSecond(100);
        GnssMeasurement m1 = new GnssMeasurement();
        m1.setConstellationType(GnssStatus.CONSTELLATION_GLONASS);
        GnssMeasurement m2 = new GnssMeasurement();
        m2.setReceivedSvTimeNanos(43999);
        GnssMeasurementsEvent event = new GnssMeasurementsEvent(
                clock, new GnssMeasurement[] {m1, m2});
        Parcel parcel = Parcel.obtain();
        event.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssMeasurementsEvent newEvent = GnssMeasurementsEvent.CREATOR.createFromParcel(parcel);
        assertEquals(100, newEvent.getClock().getLeapSecond());
        Collection<GnssMeasurement> measurements = newEvent.getMeasurements();
        assertEquals(2, measurements.size());
        Iterator<GnssMeasurement> iterator = measurements.iterator();
        GnssMeasurement newM1 = iterator.next();
        assertEquals(GnssStatus.CONSTELLATION_GLONASS, newM1.getConstellationType());
        GnssMeasurement newM2 = iterator.next();
        assertEquals(43999, newM2.getReceivedSvTimeNanos());
    }
}
