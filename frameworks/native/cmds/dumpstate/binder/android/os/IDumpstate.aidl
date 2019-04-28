/**
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os;

import android.os.IDumpstateListener;
import android.os.IDumpstateToken;

/**
  * Binder interface for the currently running dumpstate process.
  * {@hide}
  */
interface IDumpstate {

    /*
     * Sets the listener for this dumpstate progress.
     *
     * Returns a token used to monitor dumpstate death, or `nullptr` if the listener was already
     * set (the listener behaves like a Highlander: There Can be Only One).
     */
    IDumpstateToken setListener(@utf8InCpp String name, IDumpstateListener listener);
}
