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

package android.os.health.cts;

import android.os.Parcel;
import android.os.health.TimerStat;
import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Provides test cases for android.os.health.TimerStat.
 */
public class TimerStatTest extends TestCase {
    /**
     * Tests empty constructor and get methods.
     */
    @SmallTest
    public void testEmptyConstructor() throws Exception {
        TimerStat timer = new TimerStat();

        Assert.assertEquals(0, timer.getCount());
        Assert.assertEquals(0, timer.getTime());
    }

    /**
     * Tests setCount.
     */
    @SmallTest
    public void testSetCount() throws Exception {
        TimerStat timer = new TimerStat(Integer.MAX_VALUE, Long.MAX_VALUE);

        Assert.assertEquals(Integer.MAX_VALUE, timer.getCount());

        timer.setCount(12);

        Assert.assertEquals(12, timer.getCount());
        Assert.assertEquals(Long.MAX_VALUE, timer.getTime());
    }

    /**
     * Tests setTime.
     */
    @SmallTest
    public void testSetTime() throws Exception {
        TimerStat timer = new TimerStat(Integer.MAX_VALUE, Long.MAX_VALUE);

        Assert.assertEquals(Integer.MAX_VALUE, timer.getCount());

        timer.setTime(5000);

        Assert.assertEquals(Integer.MAX_VALUE, timer.getCount());
        Assert.assertEquals(5000, timer.getTime());
    }

    /**
     * Tests writeToParcel and reading a parcel.
     */
    @SmallTest
    public void testParceling() throws Exception {
        TimerStat timer = new TimerStat(Integer.MAX_VALUE, Long.MAX_VALUE);

        Assert.assertEquals(Integer.MAX_VALUE, timer.getCount());
        Assert.assertEquals(Long.MAX_VALUE, timer.getTime());
        Assert.assertEquals(0, timer.describeContents());

        Parcel parcel = Parcel.obtain();
        timer.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);

        TimerStat readback = new TimerStat(parcel);

        Assert.assertEquals(Integer.MAX_VALUE, readback.getCount());
        Assert.assertEquals(Long.MAX_VALUE, readback.getTime());
    }
}

