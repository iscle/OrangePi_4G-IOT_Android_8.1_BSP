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
import android.text.style.RelativeSizeSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RelativeSizeSpanTest {
    @Test
    public void testConstructor() {
        RelativeSizeSpan relativeSizeSpan = new RelativeSizeSpan(1.0f);

        Parcel p = Parcel.obtain();
        try {
            relativeSizeSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            new RelativeSizeSpan(p);

            new RelativeSizeSpan(-1.0f);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testGetSizeChange() {
        RelativeSizeSpan relativeSizeSpan = new RelativeSizeSpan(2.0f);
        assertEquals(2.0f, relativeSizeSpan.getSizeChange(), 0.0f);

        relativeSizeSpan = new RelativeSizeSpan(-2.0f);
        assertEquals(-2.0f, relativeSizeSpan.getSizeChange(), 0.0f);
    }

    @Test
    public void testUpdateMeasureState() {
        float proportion = 3.0f;
        RelativeSizeSpan relativeSizeSpan = new RelativeSizeSpan(proportion);

        TextPaint tp = new TextPaint();
        tp.setTextSize(2.0f);
        float oldSize = tp.getTextSize();
        relativeSizeSpan.updateMeasureState(tp);
        assertEquals(2.0f * proportion, tp.getTextSize(), 0.0f);

        // setTextSize, the value must >0, so set to negative is useless.
        tp.setTextSize(-3.0f);
        oldSize = tp.getTextSize();
        relativeSizeSpan.updateMeasureState(tp);
        assertEquals(oldSize * proportion, tp.getTextSize(), 0.0f);
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateMeasureStateNull() {
        RelativeSizeSpan relativeSizeSpan = new RelativeSizeSpan(3.0f);

        relativeSizeSpan.updateMeasureState(null);
    }

    @Test
    public void testUpdateDrawState() {
        float proportion = 3.0f;
        RelativeSizeSpan relativeSizeSpan = new RelativeSizeSpan(proportion);

        TextPaint tp = new TextPaint();
        tp.setTextSize(2.0f);
        float oldSize = tp.getTextSize();
        relativeSizeSpan.updateDrawState(tp);
        assertEquals(oldSize * proportion, tp.getTextSize(), 0.0f);

        // setTextSize, the value must >0, so set to negative is useless.
        tp.setTextSize(-3.0f);
        oldSize = tp.getTextSize();
        relativeSizeSpan.updateDrawState(tp);
        assertEquals(oldSize * proportion, tp.getTextSize(), 0.0f);
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        RelativeSizeSpan relativeSizeSpan = new RelativeSizeSpan(3.0f);

        relativeSizeSpan.updateDrawState(null);
    }

    @Test
    public void testDescribeContents() {
        RelativeSizeSpan relativeSizeSpan = new RelativeSizeSpan(2.0f);
        relativeSizeSpan.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        RelativeSizeSpan relativeSizeSpan = new RelativeSizeSpan(2.0f);
        relativeSizeSpan.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            float proportion = 3.0f;
            RelativeSizeSpan relativeSizeSpan = new RelativeSizeSpan(proportion);
            relativeSizeSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            RelativeSizeSpan newSpan = new RelativeSizeSpan(p);
            assertEquals(proportion, newSpan.getSizeChange(), 0.0f);
        } finally {
            p.recycle();
        }
    }
}
