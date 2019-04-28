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

public abstract class AbstractMapsHelper extends AbstractStandardAppHelper {

    public AbstractMapsHelper(Instrumentation instr) {
        super(instr);
    }

    /**
     * Setup expectation: On the standard Map screen in any setup.
     *
     * Best effort attempt to go to the query screen (if not currently there),
     * does a search, and selects the results.
     */
    public abstract void doSearch(String query);

    /**
     * Setup expectation: Destination is selected.
     *
     * Best effort attempt to go to the directions screen for the selected destination.
     */
    public abstract void getDirections();

    /**
     * Setup expectation: On directions screen.
     *
     * Best effort attempt to start navigation for the selected destination.
     */
    public abstract void startNavigation();

    /**
     * Setup expectation: On navigation screen.
     *
     * Best effort attempt to stop navigation, and go back to the directions screen.
     */
    public abstract void stopNavigation();
}
