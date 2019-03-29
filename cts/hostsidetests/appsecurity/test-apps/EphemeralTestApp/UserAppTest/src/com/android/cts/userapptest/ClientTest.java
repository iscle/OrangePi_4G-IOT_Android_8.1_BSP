/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.userapptest;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ClientTest {
    /** Action to query for test activities */
    private static final String ACTION_QUERY_ACTIVITY =
            "com.android.cts.ephemeraltest.QUERY";

    @Test
    public void testQueryInstant() throws Exception {
        final Intent queryIntent = new Intent(ACTION_QUERY_ACTIVITY);
        final List<ResolveInfo> resolveInfo = InstrumentationRegistry
                .getContext().getPackageManager().queryIntentActivities(queryIntent, 0 /*flags*/);
        if (resolveInfo != null && resolveInfo.size() != 0) {
            fail("resolved intents");
        }
    }
    
    @Test
    public void testQueryFull() throws Exception {
        final Intent queryIntent = new Intent(ACTION_QUERY_ACTIVITY);
        final List<ResolveInfo> resolveInfo = InstrumentationRegistry
                .getContext().getPackageManager().queryIntentActivities(queryIntent, 0 /*flags*/);
        if (resolveInfo == null || resolveInfo.size() == 0) {
            fail("didn't resolve any intents");
        }
        assertThat(resolveInfo.size(), is(1));
        assertThat(resolveInfo.get(0).activityInfo.packageName,
                is("com.android.cts.userapp"));
        assertThat(resolveInfo.get(0).activityInfo.name,
                is("com.android.cts.userapp.UserActivity"));
    }
}
