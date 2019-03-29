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
import android.text.style.LeadingMarginSpan;
import android.text.style.LeadingMarginSpan.Standard;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class LeadingMarginSpan_StandardTest {
    @Test
    public void testConstructor() {
        new Standard(1, 2);
        new Standard(3);
        new Standard(-1, -2);
        new Standard(-3);

        Standard standard = new Standard(10, 20);
        final Parcel p = Parcel.obtain();
        try {
            standard.writeToParcel(p, 0);
            p.setDataPosition(0);
            new Standard(p);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testGetLeadingMargin() {
        int first = 4;
        int rest = 5;

        Standard standard = new LeadingMarginSpan.Standard(first, rest);
        assertEquals(first, standard.getLeadingMargin(true));
        assertEquals(rest, standard.getLeadingMargin(false));

        standard = new LeadingMarginSpan.Standard(-1);
        assertEquals(-1, standard.getLeadingMargin(true));
        assertEquals(-1, standard.getLeadingMargin(false));
    }

    @Test
    public void testDrawLeadingMargin() {
        Standard standard = new LeadingMarginSpan.Standard(10);
        standard.drawLeadingMargin(null, null, 0, 0, 0, 0, 0, null, 0, 0, false, null);
    }

    @Test
    public void testDescribeContents() {
        Standard standard = new Standard(1);
        standard.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        Standard standard = new Standard(1);
        standard.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            Standard s = new Standard(10, 20);
            s.writeToParcel(p, 0);
            p.setDataPosition(0);
            Standard standard = new Standard(p);
            assertEquals(10, standard.getLeadingMargin(true));
            assertEquals(20, standard.getLeadingMargin(false));
        } finally {
            p.recycle();
        }

        p = Parcel.obtain();
        try {
            Standard s = new Standard(3);
            s.writeToParcel(p, 0);
            p.setDataPosition(0);
            Standard standard = new Standard(p);
            assertEquals(3, standard.getLeadingMargin(true));
            assertEquals(3, standard.getLeadingMargin(false));
        } finally {
            p.recycle();
        }
    }
}
