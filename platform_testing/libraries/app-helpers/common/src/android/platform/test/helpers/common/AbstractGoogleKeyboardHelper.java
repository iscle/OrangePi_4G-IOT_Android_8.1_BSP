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

package android.platform.test.helpers;

import android.app.Instrumentation;

public abstract class AbstractGoogleKeyboardHelper extends AbstractStandardAppHelper {

    public AbstractGoogleKeyboardHelper(Instrumentation instr) {
        super(instr);
    }

    /*
     * Setup expectations: Recently performed action that will open Google Keyboard
     *
     * @param timeout wait timeout in milliseconds
     */
    public abstract boolean waitForKeyboard(long timeout);

    /*
     * Setup expectations: Google Keyboard is open and visible
     *
     * @param text text to type
     * @param delayBetweenKeyPresses delay between key presses in milliseconds
     */
    public abstract void typeText(String text, long delayBetweenKeyPresses);
}
