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
 * limitations under the License
 */

package android.platform.test.helpers;

import android.support.test.uiautomator.Direction;

public interface IRecentsHelper extends IStandardAppHelper {
    /**
     * Setup expectations: "Recents" is open.
     * <p>
     * Flings the recent apps in the specified direction.
     * </p>
     * @param dir the direction for the apps to move
     */
    void flingRecents(Direction dir);

    /**
     * Setup expectations: "Recents" is open with content
     * <p>
     * Clears up open recent items. Nothing happens if there is no content.
     * </p>
     */
    void clearAll();

    /**
     * Setup expectations: "Recents" is open.
     *
     * @return True if there is Recents content.
     */
    boolean hasContent();
}
