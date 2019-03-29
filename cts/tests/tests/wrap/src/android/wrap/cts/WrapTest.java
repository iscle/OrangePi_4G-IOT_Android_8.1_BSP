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
package android.wrap.cts;

import android.content.pm.PackageManager;
import android.wrap.WrapActivity;
import android.test.ActivityInstrumentationTestCase2;

/**
 * A simple compatibility test which tests the SharedPreferences API.
 *
 * This test uses {@link android.test.ActivityInstrumentationTestCase2} to instrument the
 * {@link android.WrapActivity}.
 */
public class WrapTest extends ActivityInstrumentationTestCase2<WrapActivity> {

    /**
     * A reference to the activity whose shared preferences are being tested.
     */
    private WrapActivity mActivity;

    public WrapTest() {
        super(WrapActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Start the activity.
        mActivity = getActivity();
        // Wait for the UI Thread to become idle.
        getInstrumentation().waitForIdleSync();
    }

    @Override
    protected void tearDown() throws Exception {
        // Nothing to do here.
        super.tearDown();
    }

    /**
     * Tests the environment for a property.
     *
     * This reads the activity meta-data to figure out whether the property is expected, then
     * check whether the property exists.
     *
     * @throws Exception
     */
    public void testWrapProperty() throws Exception {
        boolean expectEnv = mActivity.getPackageManager().getApplicationInfo(
                mActivity.getPackageName(), PackageManager.GET_META_DATA).metaData.getBoolean(
                "android.wrap.cts.expext_env");

        String wrapEnvValue = System.getenv("WRAP_PROPERTY");
        boolean wrapExists = wrapEnvValue != null;

        assertEquals("Unexpected wrap property state", expectEnv, wrapExists);

        if (wrapExists) {
            assertEquals("Unexpected wrap property value", wrapEnvValue, "test");
        }
    }
}
