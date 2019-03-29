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
package android.sample.cts;

import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import android.app.Activity;
import android.sample.SampleDeviceActivity;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.ActivityInstrumentationTestCase2;

/**
 * A simple compatibility test which tests the SharedPreferences API.
 *
 * This test uses {@link ActivityTestRule} to instrument the
 * {@link android.sample.SampleDeviceActivity}.
 */
@RunWith(AndroidJUnit4.class)
public class SampleJUnit4DeviceTest {

    private static final String KEY = "foo";

    private static final String VALUE = "bar";

    @Rule
    public ActivityTestRule<SampleDeviceActivity> mActivityRule =
        new ActivityTestRule(SampleDeviceActivity.class);


    /**
     * This inserts the key value pair and assert they can be retrieved. Then it clears the
     * preferences and asserts they can no longer be retrieved.
     *
     * @throws Exception
     */
    @Test
    public void shouldSaveSharedPreferences() throws Exception {
        // Save the key value pair to the preferences and assert they were saved.
        mActivityRule.getActivity().savePreference(KEY, VALUE);
        Assert.assertEquals("Preferences were not saved", VALUE,
            mActivityRule.getActivity().getPreference(KEY));

        // Clear the shared preferences and assert the data was removed.
        mActivityRule.getActivity().clearPreferences();
        Assert.assertNull("Preferences were not cleared",
            mActivityRule.getActivity().getPreference(KEY));
    }
}
