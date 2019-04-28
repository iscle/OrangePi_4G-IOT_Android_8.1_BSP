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

public abstract class AbstractTranslateHelper extends AbstractStandardAppHelper {

    public AbstractTranslateHelper(Instrumentation instr) {
        super(instr);
    }

    /**
     * Setup expectation: Translate app is open
     *
     * Inject a voice file for translation
     */
    public abstract void translate(String filePath);

    /**
     * Setup expectation: Translation is showing
     *
     * Validate the on-screen translation
     */
    public abstract void validate(String expectedTranslation);

    /**
     * Setup expectation: Translate app is open
     *
     * Change the selected languages
     */
    public abstract void changeLanguages(String source, String target);
}
