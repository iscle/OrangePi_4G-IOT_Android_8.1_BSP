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
import android.text.Layout.Alignment;
import android.text.style.AlignmentSpan.Standard;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link Standard}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AlignmentSpan_StandardTest {
    @Test
    public void testConstructor() {
        new Standard(Alignment.ALIGN_CENTER);

        Standard standard = new Standard(Alignment.ALIGN_NORMAL);
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
    public void testGetAlignment() {
        Standard standard = new Standard(Alignment.ALIGN_NORMAL);
        assertEquals(Alignment.ALIGN_NORMAL, standard.getAlignment());

        standard = new Standard(Alignment.ALIGN_OPPOSITE);
        assertEquals(Alignment.ALIGN_OPPOSITE, standard.getAlignment());

        standard = new Standard(Alignment.ALIGN_CENTER);
        assertEquals(Alignment.ALIGN_CENTER, standard.getAlignment());
    }

    @Test
    public void testDescribeContents() {
        Standard standard = new Standard(Alignment.ALIGN_NORMAL);
        standard.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        Standard standard = new Standard(Alignment.ALIGN_NORMAL);
        standard.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            Standard s = new Standard(Alignment.ALIGN_NORMAL);
            s.writeToParcel(p, 0);
            p.setDataPosition(0);
            Standard standard = new Standard(p);
            assertEquals(Alignment.ALIGN_NORMAL, standard.getAlignment());
        } finally {
            p.recycle();
        }

        p = Parcel.obtain();
        try {
            Standard s = new Standard(Alignment.ALIGN_OPPOSITE);
            s.writeToParcel(p, 0);
            p.setDataPosition(0);
            Standard standard = new Standard(p);
            assertEquals(Alignment.ALIGN_OPPOSITE, standard.getAlignment());
        } finally {
            p.recycle();
        }

        p = Parcel.obtain();
        try {
            Standard s = new Standard(Alignment.ALIGN_CENTER);
            s.writeToParcel(p, 0);
            p.setDataPosition(0);
            Standard standard = new Standard(p);
            assertEquals(Alignment.ALIGN_CENTER, standard.getAlignment());
        } finally {
            p.recycle();
        }
    }
}
