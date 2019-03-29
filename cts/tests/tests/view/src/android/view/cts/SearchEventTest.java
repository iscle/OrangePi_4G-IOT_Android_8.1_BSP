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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Instrumentation;
import android.content.Context;
import android.hardware.input.InputManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.SearchEvent;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class SearchEventTest {

    private Instrumentation mInstrumentation;
    private SearchEventActivity mActivity;

    @Rule
    public ActivityTestRule<SearchEventActivity> mActivityRule =
            new ActivityTestRule<>(SearchEventActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(5000, mActivity::hasWindowFocus);
    }

    @Test
    public void testConstructor() {
        final InputManager inputManager = (InputManager) mInstrumentation.getTargetContext().
                getSystemService(Context.INPUT_SERVICE);
        if (inputManager == null) {
            return;
        }
        final int[] inputDeviceIds = inputManager.getInputDeviceIds();
        if (inputDeviceIds != null) {
            for (int inputDeviceId : inputDeviceIds) {
                final InputDevice inputDevice = inputManager.getInputDevice(inputDeviceId);
                new SearchEvent(inputDevice);
            }
        }
    }

    @Test
    public void testBasics() {
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_SEARCH);
        SearchEvent se = mActivity.getTestSearchEvent();
        assertNotNull(se);
        InputDevice id = se.getInputDevice();
        assertNotNull(id);
        assertEquals(-1, id.getId());
    }
}
