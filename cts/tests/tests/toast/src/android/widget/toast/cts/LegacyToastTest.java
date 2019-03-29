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

package android.widget.toast.cts;

import android.support.test.runner.AndroidJUnit4;
import android.view.WindowManager;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.fail;

/**
 * Test whether toasts are properly shown. For apps targeting API 25+
 * like this app the only way to add toast windows is via the dedicated
 * toast APIs.
 */
@RunWith(AndroidJUnit4.class)
public class LegacyToastTest extends BaseToastTest {
    @Test
    public void testAddSingleToastViaTestApisWhenUidFocused() throws Exception {
        // Normal toast windows cannot be obtained vie the accessibility APIs because
        // they are not touchable. In this case not crashing is good enough.
        showToastsViaToastApis(1);
    }

    @Test
    public void testAddTwoToastViaTestApisWhenUidFocused() throws Exception {
        // Normal toast windows cannot be obtained vie the accessibility APIs because
        // they are not touchable. In this case not crashing is good enough.
        showToastsViaToastApis(2);

        // Wait for the first one to expire
        waitForToastTimeout();
    }

    @Test
    public void testAddSingleToastViaAddingWindowApisWhenUidFocused() throws Exception {
        try {
            showToastsViaAddingWindow(1, false);
            fail("Shouldn't be able to add toast windows directly");
        } catch (WindowManager.BadTokenException e) {
            /* expected */
        }
    }
}
