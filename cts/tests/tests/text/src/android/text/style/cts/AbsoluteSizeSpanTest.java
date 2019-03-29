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

package android.text.style.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AbsoluteSizeSpanTest {
    @Test
    public void testConstructor() {
        new AbsoluteSizeSpan(0);
        new AbsoluteSizeSpan(-5);

        AbsoluteSizeSpan asp = new AbsoluteSizeSpan(10);
        final Parcel p = Parcel.obtain();
        try {
            asp.writeToParcel(p, 0);
            p.setDataPosition(0);
            new AbsoluteSizeSpan(p);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testGetSize() {
        AbsoluteSizeSpan absoluteSizeSpan = new AbsoluteSizeSpan(5);
        assertEquals(5, absoluteSizeSpan.getSize());

        absoluteSizeSpan = new AbsoluteSizeSpan(-5);
        assertEquals(-5, absoluteSizeSpan.getSize());
    }

    @Test
    public void testGetDip() {
        AbsoluteSizeSpan absoluteSizeSpan = new AbsoluteSizeSpan(5);
        assertEquals(false, absoluteSizeSpan.getDip());

        absoluteSizeSpan = new AbsoluteSizeSpan(5, true);
        assertTrue(absoluteSizeSpan.getDip());
    }

    @Test
    public void testUpdateMeasureState() {
        AbsoluteSizeSpan absoluteSizeSpan = new AbsoluteSizeSpan(1);

        TextPaint tp = new TextPaint();
        absoluteSizeSpan.updateMeasureState(tp);
        assertEquals(1.0f, tp.getTextSize(), 0.0f);

        absoluteSizeSpan = new AbsoluteSizeSpan(10);
        absoluteSizeSpan.updateMeasureState(tp);
        assertEquals(10.0f, tp.getTextSize(), 0.0f);
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateMeasureStateNull() {
        // Should throw NullPointerException when TextPaint is null
        AbsoluteSizeSpan absoluteSizeSpan = new AbsoluteSizeSpan(1);

        absoluteSizeSpan.updateMeasureState(null);
    }

    @Test
    public void testUpdateDrawState() {
        // new the AbsoluteSizeSpan instance
        AbsoluteSizeSpan absoluteSizeSpan = new AbsoluteSizeSpan(2);

        TextPaint tp = new TextPaint();
        absoluteSizeSpan.updateDrawState(tp);
        assertEquals(2.0f, tp.getTextSize(), 0.0f);

        // new the AbsoluteSizeSpan instance
        absoluteSizeSpan = new AbsoluteSizeSpan(20);
        absoluteSizeSpan.updateDrawState(tp);
        assertEquals(20.0f, tp.getTextSize(), 0.0f);
    }


    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        // Should throw NullPointerException when TextPaint is null
        AbsoluteSizeSpan absoluteSizeSpan = new AbsoluteSizeSpan(2);

        absoluteSizeSpan.updateDrawState(null);
    }

    @Test
    public void testDescribeContents() {
        AbsoluteSizeSpan absoluteSizeSpan = new AbsoluteSizeSpan(2);
        absoluteSizeSpan.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        AbsoluteSizeSpan absoluteSizeSpan = new AbsoluteSizeSpan(2);
        absoluteSizeSpan.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            AbsoluteSizeSpan asp = new AbsoluteSizeSpan(2);
            asp.writeToParcel(p, 0);
            p.setDataPosition(0);
            AbsoluteSizeSpan absoluteSizeSpan = new AbsoluteSizeSpan(p);
            assertEquals(2, absoluteSizeSpan.getSize());
        } finally {
            p.recycle();
        }

        p = Parcel.obtain();
        try {
            AbsoluteSizeSpan asp = new AbsoluteSizeSpan(-5);
            asp.writeToParcel(p, 0);
            p.setDataPosition(0);
            AbsoluteSizeSpan absoluteSizeSpan = new AbsoluteSizeSpan(p);
            assertEquals(-5, absoluteSizeSpan.getSize());
        } finally {
            p.recycle();
        }
    }
}
