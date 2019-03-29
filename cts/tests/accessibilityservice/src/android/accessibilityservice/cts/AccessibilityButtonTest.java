/**
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.accessibilityservice.cts;

import android.accessibilityservice.AccessibilityButtonController;
import android.app.Instrumentation;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test cases for accessibility service APIs related to the accessibility button within
 * software-rendered navigation bars.
 *
 * TODO: Extend coverage with a more precise signal if a device is compatible with the button
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityButtonTest {

    private StubAccessibilityButtonService mService;
    private AccessibilityButtonController mButtonController;
    private AccessibilityButtonController.AccessibilityButtonCallback mStubCallback =
            new AccessibilityButtonController.AccessibilityButtonCallback() {
        @Override
        public void onClicked(AccessibilityButtonController controller) {
            /* do nothing */
        }

        @Override
        public void onAvailabilityChanged(AccessibilityButtonController controller,
                boolean available) {
            /* do nothing */
        }
    };

    @Before
    public void setUp() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mService = StubAccessibilityButtonService.enableSelf(instrumentation);
        mButtonController = mService.getAccessibilityButtonController();
    }

    @After
    public void tearDown() {
        mService.runOnServiceSync(() -> mService.disableSelf());
    }

    @Test
    public void testCallbackRegistrationUnregistration_serviceDoesNotCrash() {
        mButtonController.registerAccessibilityButtonCallback(mStubCallback);
        mButtonController.unregisterAccessibilityButtonCallback(mStubCallback);
    }
}
