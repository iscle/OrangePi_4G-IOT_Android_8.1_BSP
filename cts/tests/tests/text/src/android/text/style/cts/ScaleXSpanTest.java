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
import android.text.style.ScaleXSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ScaleXSpanTest {
    @Test
    public void testConstructor() {
        ScaleXSpan scaleXSpan = new ScaleXSpan(1.5f);

        Parcel p = Parcel.obtain();
        try {
            scaleXSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            new ScaleXSpan(p);

            new ScaleXSpan(-2.5f);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testUpdateDrawState() {
        float proportion = 3.0f;
        ScaleXSpan scaleXSpan = new ScaleXSpan(proportion);

        TextPaint tp = new TextPaint();
        tp.setTextScaleX(2.0f);
        scaleXSpan.updateDrawState(tp);
        assertEquals(2.0f * proportion, tp.getTextScaleX(), 0.0f);

        tp.setTextScaleX(-3.0f);
        scaleXSpan.updateDrawState(tp);
        assertEquals(-3.0f * proportion, tp.getTextScaleX(), 0.0f);
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        ScaleXSpan scaleXSpan = new ScaleXSpan(3.0f);

        scaleXSpan.updateDrawState(null);
    }

    @Test
    public void testUpdateMeasureState() {
        float proportion = 3.0f;
        ScaleXSpan scaleXSpan = new ScaleXSpan(proportion);

        TextPaint tp = new TextPaint();
        tp.setTextScaleX(2.0f);
        scaleXSpan.updateMeasureState(tp);
        assertEquals(2.0f * proportion, tp.getTextScaleX(), 0.0f);

        tp.setTextScaleX(-3.0f);
        scaleXSpan.updateMeasureState(tp);
        assertEquals(-3.0f * proportion, tp.getTextScaleX(), 0.0f);
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateMeasureStateNull() {
        ScaleXSpan scaleXSpan = new ScaleXSpan(3.0f);

        scaleXSpan.updateMeasureState(null);
    }

    @Test
    public void testGetScaleX() {
        ScaleXSpan scaleXSpan = new ScaleXSpan(5.0f);
        assertEquals(5.0f, scaleXSpan.getScaleX(), 0.0f);

        scaleXSpan = new ScaleXSpan(-5.0f);
        assertEquals(-5.0f, scaleXSpan.getScaleX(), 0.0f);
    }

    @Test
    public void testDescribeContents() {
        ScaleXSpan scaleXSpan = new ScaleXSpan(5.0f);
        scaleXSpan.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        ScaleXSpan scaleXSpan = new ScaleXSpan(5.0f);
        scaleXSpan.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            float proportion = 3.0f;
            ScaleXSpan scaleXSpan = new ScaleXSpan(proportion);
            scaleXSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            ScaleXSpan newSpan = new ScaleXSpan(p);
            assertEquals(proportion, newSpan.getScaleX(), 0.0f);
        } finally {
            p.recycle();
        }
    }
}
