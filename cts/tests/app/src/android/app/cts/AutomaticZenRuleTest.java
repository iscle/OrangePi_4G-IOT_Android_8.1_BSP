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

import android.app.AutomaticZenRule;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Parcel;
import android.test.AndroidTestCase;

public class AutomaticZenRuleTest extends AndroidTestCase {

    private final String mName = "name";
    private final ComponentName mOwner = new ComponentName("pkg", "cls");
    private final Uri mConditionId = new Uri.Builder().scheme("scheme")
            .authority("authority")
            .appendPath("path")
            .appendPath("test")
            .build();
    private final int mInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
    private final boolean mEnabled = true;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testDescribeContents() {
        final int expected = 0;
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConditionId,
                mInterruptionFilter, mEnabled);
        assertEquals(expected, rule.describeContents());
    }

    public void testWriteToParcel() {
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConditionId,
                mInterruptionFilter, mEnabled);
        Parcel parcel = Parcel.obtain();
        rule.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AutomaticZenRule rule1 = new AutomaticZenRule(parcel);
        assertEquals(mName, rule1.getName());
        assertEquals(mOwner, rule1.getOwner());
        assertEquals(mConditionId, rule1.getConditionId());
        assertEquals(mInterruptionFilter, rule1.getInterruptionFilter());
        assertEquals(mEnabled, rule1.isEnabled());

        rule.setName(null);
        parcel = Parcel.obtain();
        rule.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        rule1 = new AutomaticZenRule(parcel);
        assertNull(rule1.getName());
    }

    public void testSetConditionId() {
        final Uri newConditionId = new Uri.Builder().scheme("scheme")
                .authority("authority2")
                .appendPath("3path")
                .appendPath("test4")
                .build();
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConditionId,
                mInterruptionFilter, mEnabled);
        rule.setConditionId(newConditionId);
        assertEquals(newConditionId, rule.getConditionId());
    }

    public void testSetEnabled() {
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConditionId,
                mInterruptionFilter, mEnabled);
        rule.setEnabled(!mEnabled);
        assertEquals(!mEnabled, rule.isEnabled());
    }

    public void testSetInterruptionFilter() {
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConditionId,
                mInterruptionFilter, mEnabled);
        for (int i = NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
             i <= NotificationManager.INTERRUPTION_FILTER_ALARMS; i++) {
            rule.setInterruptionFilter(i);
            assertEquals(i, rule.getInterruptionFilter());
        }
    }

    public void testSetName() {
        AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConditionId,
                mInterruptionFilter, mEnabled);
        rule.setName(mName + "new");
        assertEquals(mName + "new", rule.getName());
    }
}
