/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.sample.SampleDeviceActivity;
import android.test.ActivityInstrumentationTestCase2;

/**
 * A simple compatibility test which tests the SharedPreferences API.
 *
 * This test uses {@link android.test.ActivityInstrumentationTestCase2} to instrument the
 * {@link android.sample.SampleDeviceActivity}.
 */
public class SampleDeviceTest extends ActivityInstrumentationTestCase2<SampleDeviceActivity> {

    private static final String KEY = "foo";

    private static final String VALUE = "bar";

    /**
     * A reference to the activity whose shared preferences are being tested.
     */
    private SampleDeviceActivity mActivity;

    public SampleDeviceTest() {
        super(SampleDeviceActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Start the activity and get a reference to it.
        mActivity = getActivity();
        // Wait for the UI Thread to become idle.
        getInstrumentation().waitForIdleSync();
    }

    @Override
    protected void tearDown() throws Exception {
        // Scrub the activity so it can be freed. The next time the setUp will create a new activity
        // rather than reusing the old one.
        mActivity = null;
        super.tearDown();
    }

    /**
     * Tests the SharedPreferences API.
     *
     * This inserts the key value pair and assert they can be retrieved. Then it clears the
     * preferences and asserts they can no longer be retrieved.
     *
     * @throws Exception
     */
    public void testSharedPreferences() throws Exception {
        // Save the key value pair to the preferences and assert they were saved.
        mActivity.savePreference(KEY, VALUE);
        assertEquals("Preferences were not saved", VALUE, mActivity.getPreference(KEY));

        // Clear the shared preferences and assert the data was removed.
        mActivity.clearPreferences();
        assertNull("Preferences were not cleared", mActivity.getPreference(KEY));
    }
}
