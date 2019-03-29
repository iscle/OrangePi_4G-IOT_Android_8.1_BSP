/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package android.app.cts;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests the {@link android.app.ActivityManager} method to ensure a fixed number of supported
 * actions.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PictureInPictureActionsTest {

    private Activity mActivity;

    @Rule
    public ActivityTestRule<PictureInPictureActivity> mActivityRule =
            new ActivityTestRule<>(PictureInPictureActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testNumPictureInPictureActions() throws Exception {
        // Currently enforce that there are at least 3 actions
        assertTrue(mActivity.getMaxNumPictureInPictureActions() >= 3);
    }
}
