/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.tests;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;

import com.android.tv.TvActivity;
import com.android.tv.testing.Utils;

import org.junit.Rule;
import org.junit.Test;

@MediumTest
public class TvActivityTest {
    @Rule
    public ActivityTestRule<TvActivity> mActivityTestRule =
            new ActivityTestRule<>(TvActivity.class, false, false);

    @Test
    public void testLifeCycle() {
        assertTrue("TvActivity should be enabled.", Utils.isTvActivityEnabled(getTargetContext()));
        assertNotNull(mActivityTestRule.launchActivity(null));
    }
}
