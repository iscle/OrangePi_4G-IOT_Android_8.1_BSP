/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.graphics.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Point;
import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PointTest {
    private Point mPoint;

    @Test
    public void testConstructor() {
        mPoint = new Point();
        mPoint = new Point(10, 10);

        Point point = new Point(10, 10);
        mPoint = new Point(point);
    }

    @Test
    public void testSet() {
        mPoint = new Point();
        mPoint.set(3, 4);
        assertEquals(3, mPoint.x);
        assertEquals(4, mPoint.y);
    }

    @Test
    public void testEquals1() {
        mPoint = new Point(3, 4);
        assertTrue(mPoint.equals(3, 4));
        assertFalse(mPoint.equals(4, 3));
    }

    @Test
    public void testEquals2() {
        mPoint = new Point(3, 4);
        Point point = new Point(3, 4);
        assertTrue(mPoint.equals(point));
        point = new Point(4, 3);
        assertFalse(mPoint.equals(point));
    }

    @Test
    public void testHashCode() {
        mPoint = new Point(10, 10);
        Point p = new Point(100, 10);
        assertTrue(p.hashCode() != mPoint.hashCode());
    }

    @Test
    public void testToString() {
        mPoint = new Point();
        assertNotNull(mPoint.toString());
    }

    @Test
    public void testOffset() {
        mPoint = new Point(10, 10);
        mPoint.offset(1, 1);
        assertEquals(11, mPoint.x);
        assertEquals(11, mPoint.y);
    }

    @Test
    public void testNegate() {
        mPoint = new Point(10, 10);
        mPoint.negate();
        assertEquals(-10, mPoint.x);
        assertEquals(-10, mPoint.y);
    }

    @Test
    public void testDescribeContents() {
        mPoint = new Point(10, 20);
        assertEquals(0, mPoint.describeContents());
    }

    @Test
    public void testParceling() {
        mPoint = new Point(10, 20);
        Parcel p = Parcel.obtain();
        mPoint.writeToParcel(p, 0);
        p.setDataPosition(0);

        mPoint = new Point();
        mPoint.readFromParcel(p);
        assertEquals(10, mPoint.x);
        assertEquals(20, mPoint.y);
    }
}
