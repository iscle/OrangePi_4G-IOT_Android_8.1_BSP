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

import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextPaint;
import android.text.style.SubscriptSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SubscriptSpanTest {
    @Test
    public void testConstructor() {
        SubscriptSpan subscriptSpan = new SubscriptSpan();

        Parcel p = Parcel.obtain();
        try {
            subscriptSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            new SubscriptSpan(p);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testUpdateMeasureState() {
        // the expected result is: tp.baselineShift -= (int) (tp.ascent() / 2)
        SubscriptSpan subscriptSpan = new SubscriptSpan();

        TextPaint tp = new TextPaint();
        float ascent = tp.ascent();
        int baselineShift = 100;
        tp.baselineShift = baselineShift;

        subscriptSpan.updateMeasureState(tp);
        assertEquals(baselineShift - (int) (ascent / 2), tp.baselineShift);
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateMeasureStateNull() {
        SubscriptSpan subscriptSpan = new SubscriptSpan();

        subscriptSpan.updateMeasureState(null);
    }

    @Test
    public void testUpdateDrawState() {
        // the expected result is: tp.baselineShift -= (int) (tp.ascent() / 2)
        SubscriptSpan subscriptSpan = new SubscriptSpan();

        TextPaint tp = new TextPaint();
        float ascent = tp.ascent();
        int baselineShift = 50;
        tp.baselineShift = baselineShift;

        subscriptSpan.updateDrawState(tp);
        assertEquals(baselineShift - (int) (ascent / 2), tp.baselineShift);
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        SubscriptSpan subscriptSpan = new SubscriptSpan();

        subscriptSpan.updateDrawState(null);
    }

    @Test
    public void testDescribeContents() {
        SubscriptSpan subscriptSpan = new SubscriptSpan();
        subscriptSpan.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        SubscriptSpan subscriptSpan = new SubscriptSpan();
        subscriptSpan.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            SubscriptSpan subscriptSpan = new SubscriptSpan();
            subscriptSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            new SubscriptSpan(p);
        } finally {
            p.recycle();
        }
    }
}
