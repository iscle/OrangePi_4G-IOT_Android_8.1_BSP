/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car;

import android.car.IAppFocusListener;
import android.car.IAppFocusOwnershipCallback;

/** @hide */
interface IAppFocus {
    void registerFocusListener(IAppFocusListener callback, int appType) = 0;
    void unregisterFocusListener(IAppFocusListener callback, int appType) = 1;
    int[] getActiveAppTypes() = 2;
    /** callback used as a token */
    boolean isOwningFocus(IAppFocusOwnershipCallback callback, int appType) = 3;
    /** callback used as a token */
    int requestAppFocus(IAppFocusOwnershipCallback callback, int appType) = 4;
    /** callback used as a token */
    void abandonAppFocus(IAppFocusOwnershipCallback callback, int appType) = 5;
}
