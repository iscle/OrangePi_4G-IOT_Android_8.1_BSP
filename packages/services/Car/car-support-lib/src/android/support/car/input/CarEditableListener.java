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
package android.support.car.input;

/**
 * Callbacks from the {@link CarEditable} to the Car IME. These methods should be called on
 * the main thread.
 * @hide
 */
public interface CarEditableListener {
    /**
     * Callback to indicate that the selection has changed on the current {@link CarEditable}. Note
     * that selection changes include cursor movements.
     * @param oldSelStart the old selection starting index
     * @param oldSelEnd the old selection ending index
     * @param newSelStart the new selection starting index
     * @param newSelEnd the new selection ending index
     */
    void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd);
}