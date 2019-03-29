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

import android.graphics.Color;
import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ForegroundColorSpanTest {
    @Test
    public void testConstructor() {
        ForegroundColorSpan f = new ForegroundColorSpan(Color.GREEN);

        final Parcel p = Parcel.obtain();
        try {
            f.writeToParcel(p, 0);
            p.setDataPosition(0);
            new ForegroundColorSpan(p);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testGetForegroundColor() {
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(Color.BLUE);
        assertEquals(Color.BLUE, foregroundColorSpan.getForegroundColor());

        foregroundColorSpan = new ForegroundColorSpan(Color.BLACK);
        assertEquals(Color.BLACK, foregroundColorSpan.getForegroundColor());
    }

    @Test
    public void testUpdateDrawState() {
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(Color.CYAN);

        TextPaint tp = new TextPaint();
        tp.setColor(0);
        assertEquals(0, tp.getColor());
        foregroundColorSpan.updateDrawState(tp);
        assertEquals(Color.CYAN, tp.getColor());

        foregroundColorSpan = new ForegroundColorSpan(Color.DKGRAY);
        foregroundColorSpan.updateDrawState(tp);
        assertEquals(Color.DKGRAY, tp.getColor());
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(Color.CYAN);

        foregroundColorSpan.updateDrawState(null);
    }

    @Test
    public void testDescribeContents() {
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(Color.RED);
        foregroundColorSpan.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(Color.RED);
        foregroundColorSpan.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(Color.RED);
            foregroundColorSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            ForegroundColorSpan f = new ForegroundColorSpan(p);
            assertEquals(Color.RED, f.getForegroundColor());
        } finally {
            p.recycle();
        }

        p = Parcel.obtain();
        try {
            ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(Color.MAGENTA);
            foregroundColorSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            ForegroundColorSpan f = new ForegroundColorSpan(p);
            assertEquals(Color.MAGENTA, f.getForegroundColor());
        } finally {
            p.recycle();
        }
    }
}
