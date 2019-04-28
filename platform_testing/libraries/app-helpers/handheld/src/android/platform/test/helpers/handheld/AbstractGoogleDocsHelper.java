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

public abstract class AbstractGoogleDocsHelper extends AbstractStandardAppHelper {

    public AbstractGoogleDocsHelper(Instrumentation instr) {
        super(instr);
    }

    /**
     * Setup expectation: Google Docs is open and the Recent Docs Tab can be reached by
     * pressing back button multiple times, i.e. the test procedure has been on the Recent
     * Docs tab.
     *
     * Returns to the Recent Docs Tab.
     */
    public abstract void goToRecentDocsTab();

    /**
     * Setup expectation: Google Docs is on the Recent Docs tab.
     *
     * Opens the document.
     *
     * @param title The title (case sensitive) of the document as is displayed in the app.
     */
    public abstract void openDoc(String title);

    /**
     * Setup expectation: Google Docs is on a document page.
     *
     * Scrolls down the document.
     */
    public abstract void scrollDownDocument();
}
