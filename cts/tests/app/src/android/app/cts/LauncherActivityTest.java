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

package android.app.cts;

import android.app.Instrumentation;
import android.app.LauncherActivity.ListItem;
import android.app.stubs.LauncherActivityStub;
import android.test.ActivityInstrumentationTestCase2;
import android.view.KeyEvent;

import java.util.List;

public class LauncherActivityTest
        extends ActivityInstrumentationTestCase2<LauncherActivityStub> {

    private Instrumentation mInstrumentation;
    private LauncherActivityStub mActivity;

    public LauncherActivityTest() {
        super("android.app.stubs", LauncherActivityStub.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
    }

    public void testLaunchActivity() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                // Test getTargetIntent. LaunchActivity#getTargetIntent() just returns a Intent() instance
                // with no content, so we use LaunchActivityStub#getSuperIntent() to get the default Intent,
                // and create a new intent for other tests.
                assertNotNull(mActivity.getSuperIntent());

                // Test makeListItems. Make sure the size > 0. The sorted order is related to the sort
                // way, so it's mutable.
                final List<ListItem> list = mActivity.makeListItems();
                assertTrue(list.size() > 0);

                // There should be an activity(but with uncertain content) in position 0.
                assertNotNull(mActivity.intentForPosition(0));
            }
        });
        mInstrumentation.waitForIdleSync();
        // Test onListItemClick
        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(mActivity.isOnListItemClick);
    }
}
