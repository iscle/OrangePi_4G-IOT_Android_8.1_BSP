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

package android.app.cts;

import android.net.Uri;
import android.os.Parcel;
import android.service.notification.Condition;
import android.test.AndroidTestCase;

public class ConditionTest extends AndroidTestCase {

    private final Uri mConditionId = new Uri.Builder().scheme("scheme")
            .authority("authority")
            .appendPath("path")
            .appendPath("test")
            .build();
    private final String mSummary = "summary";
    private final int mState = Condition.STATE_FALSE;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testDescribeContents() {
        final int expected = 0;
        Condition condition = new Condition(mConditionId, mSummary, mState);
        assertEquals(expected, condition.describeContents());
    }

    public void testConstructor() {
        Condition condition = new Condition(mConditionId, mSummary, mState);
        assertEquals(mConditionId, condition.id);
        assertEquals(mSummary, condition.summary);
        assertEquals("", condition.line1);
        assertEquals("", condition.line2);
        assertEquals(mState, condition.state);
        assertEquals(-1, condition.icon);
        assertEquals(Condition.FLAG_RELEVANT_ALWAYS, condition.flags);
    }

    public void testWriteToParcel() {
        Condition condition = new Condition(mConditionId, mSummary, mState);
        Parcel parcel = Parcel.obtain();
        condition.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Condition condition1 = new Condition(parcel);
        assertEquals(mConditionId, condition1.id);
        assertEquals(mSummary, condition1.summary);
        assertEquals("", condition1.line1);
        assertEquals("", condition1.line2);
        assertEquals(mState, condition1.state);
        assertEquals(-1, condition1.icon);
        assertEquals(Condition.FLAG_RELEVANT_ALWAYS, condition1.flags);
    }
}
