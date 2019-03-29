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
import static org.junit.Assert.assertTrue;

import android.graphics.Point;
import android.graphics.PointF;
import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PointFTest {
    private PointF mPointF;

    @Test
    public void testConstructor() {
        mPointF = new PointF();
        mPointF = new PointF(10.0f, 10.0f);

        Point point = new Point(10, 10);
        mPointF = new PointF(point);
    }

    @Test
    public void testNegate() {
        mPointF = new PointF(10, 10);
        mPointF.negate();
        assertEquals(-10.0f, mPointF.x, 0.0f);
        assertEquals(-10.0f, mPointF.y, 0.0f);
    }

    @Test
    public void testLength1() {
        mPointF = new PointF(0.3f, 0.4f);
        assertEquals(0.5f, mPointF.length(), 0.0f);
    }

    @Test
    public void testLength2() {
        assertEquals(0.5f, PointF.length(0.3f, 0.4f), 0.0f);
    }

    @Test
    public void testSet1() {
        mPointF = new PointF();
        mPointF.set(0.3f, 0.4f);
        assertEquals(0.3f, mPointF.x, 0.0f);
        assertEquals(0.4f, mPointF.y, 0.0f);
    }

    @Test
    public void testSet2() {
        mPointF = new PointF();
        PointF pointF = new PointF(0.3f, 0.4f);
        mPointF.set(pointF);
        assertEquals(0.3f, mPointF.x, 0.0f);
        assertEquals(0.4f, mPointF.y, 0.0f);
    }

    @Test
    public void testEquals() {
        mPointF = new PointF(0.3f, 0.4f);
        assertTrue(mPointF.equals(0.3f, 0.4f));
        assertFalse(mPointF.equals(0.4f, 0.3f));
    }

    @Test
    public void testOffset() {
        mPointF = new PointF(10.0f, 10.0f);
        mPointF.offset(1.0f, 1.1f);
        assertEquals(11.0f, mPointF.x, 0.0f);
        assertEquals(11.1f, mPointF.y, 0.0f);
    }

    @Test
    public void testDescribeContents() {
        mPointF = new PointF(10.0f, 20.0f);
        assertEquals(0, mPointF.describeContents());
    }

    @Test
    public void testParceling() {
        mPointF = new PointF(10.0f, 20.0f);
        Parcel p = Parcel.obtain();
        mPointF.writeToParcel(p, 0);
        p.setDataPosition(0);

        mPointF = new PointF();
        mPointF.readFromParcel(p);
        assertEquals(10.0f, mPointF.x, 0.0f);
        assertEquals(20.0f, mPointF.y, 0.0f);
    }
}
