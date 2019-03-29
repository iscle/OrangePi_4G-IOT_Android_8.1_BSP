/*
 * Copyright 2015 The Android Open Source Project
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

package android.hardware.input.cts.tests;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;

import android.hardware.cts.R;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class GamepadTestCase extends InputTestCase {
    private static final String TAG = "GamepadTests";

    @Test
    public void testButtonA() throws Exception {
        registerInputDevice(R.raw.gamepad_register_device);

        sendHidCommands(R.raw.gamepad_button_a_down);
        sendHidCommands(R.raw.gamepad_delay);
        assertReceivedKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A);

        sendHidCommands(R.raw.gamepad_button_a_up);
        assertReceivedKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A);

        assertNoMoreEvents();
    }
}

