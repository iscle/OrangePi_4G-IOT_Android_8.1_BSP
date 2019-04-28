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

package android.platform.test.helpers;

import android.app.Instrumentation;
import android.support.test.uiautomator.BySelector;

public abstract class AbstractVoiceHelper extends AbstractStandardAppHelper {

    public AbstractVoiceHelper(Instrumentation instr) {
        super(instr);
    }

    /**
     * Setup expectation: Microphone is open
     *
     * Inject audio file
     */
    public abstract void inject(String filePath);

    /**
     * Setup expectation: Some result is showing
     *
     * Validate on-screen result
     */
    public abstract void validate(BySelector itemToValidate);
}
