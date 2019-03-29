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
import android.os.Parcel;

public class GnssClockTest extends GnssTestCase {
    public void testDescribeContents() {
        GnssClock clock = new GnssClock();
        clock.describeContents();
    }

    public void testReset() {
        GnssClock clock = new GnssClock();
        clock.reset();
    }

    private static void setTestValues(GnssClock clock) {
        clock.setBiasNanos(1.0);
        clock.setBiasUncertaintyNanos(2.0);
        clock.setDriftNanosPerSecond(3.0);
        clock.setDriftUncertaintyNanosPerSecond(4.0);
        clock.setFullBiasNanos(5);
        clock.setHardwareClockDiscontinuityCount(6);
        clock.setLeapSecond(7);
        clock.setTimeNanos(8);
        clock.setTimeUncertaintyNanos(9.0);
    }

    private static void verifyTestValues(GnssClock clock) {
        assertEquals(1.0, clock.getBiasNanos());
        assertEquals(2.0, clock.getBiasUncertaintyNanos());
        assertEquals(3.0, clock.getDriftNanosPerSecond());
        assertEquals(4.0, clock.getDriftUncertaintyNanosPerSecond());
        assertEquals(5, clock.getFullBiasNanos());
        assertEquals(6, clock.getHardwareClockDiscontinuityCount());
        assertEquals(7, clock.getLeapSecond());
        assertEquals(8, clock.getTimeNanos());
        assertEquals(9.0, clock.getTimeUncertaintyNanos());
    }

    public void testWriteToParcel() {
        GnssClock clock = new GnssClock();
        setTestValues(clock);
        Parcel parcel = Parcel.obtain();
        clock.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssClock newClock = GnssClock.CREATOR.createFromParcel(parcel);
        verifyTestValues(newClock);
    }

    public void testSet() {
        GnssClock clock = new GnssClock();
        setTestValues(clock);
        GnssClock newClock = new GnssClock();
        newClock.set(clock);
        verifyTestValues(newClock);
    }

    public void testHasAndReset() {
        GnssClock clock = new GnssClock();
        setTestValues(clock);

        assertTrue(clock.hasBiasNanos());
        clock.resetBiasNanos();
        assertFalse(clock.hasBiasNanos());

        assertTrue(clock.hasBiasUncertaintyNanos());
        clock.resetBiasUncertaintyNanos();
        assertFalse(clock.hasBiasUncertaintyNanos());

        assertTrue(clock.hasDriftNanosPerSecond());
        clock.resetDriftNanosPerSecond();
        assertFalse(clock.hasDriftNanosPerSecond());

        assertTrue(clock.hasDriftUncertaintyNanosPerSecond());
        clock.resetDriftUncertaintyNanosPerSecond();
        assertFalse(clock.hasDriftUncertaintyNanosPerSecond());

        assertTrue(clock.hasFullBiasNanos());
        clock.resetFullBiasNanos();
        assertFalse(clock.hasFullBiasNanos());

        assertTrue(clock.hasLeapSecond());
        clock.resetLeapSecond();
        assertFalse(clock.hasLeapSecond());

        assertTrue(clock.hasTimeUncertaintyNanos());
        clock.resetTimeUncertaintyNanos();
        assertFalse(clock.hasTimeUncertaintyNanos());
    }
}
