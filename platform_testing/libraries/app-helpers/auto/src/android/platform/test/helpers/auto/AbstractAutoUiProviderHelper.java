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

/**
 * AbstractAutoUiProviderHelper used to open drawer in different applications like Dialer,
 * Bluetooth Media and Local Media player.
 */

public abstract class AbstractAutoUiProviderHelper extends AbstractStandardAppHelper {

    public AbstractAutoUiProviderHelper(Instrumentation instr) {
        super(instr);
    }

    /**
     * Setup expectations: The applications like Dialer,Media should be open.
     *
     * This method is used to open drawer in different applications like Dialer and Media.
     */
    public abstract void openDrawer();

    /**
     * Setup expectations: The drawer in applications like Dialer,Media should be open.
     *
     * This method is used to close drawer in different applications like Dialer and Media.
     */
    public abstract void closeDrawer();
}
