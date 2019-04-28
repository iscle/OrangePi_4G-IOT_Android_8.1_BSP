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

import android.widget.EditText;

/**
 *  Manages use of the in-car IME. All methods should only be called on the main thread.
 *  Instances should be obtained by calling
 *  {@link android.support.car.app.CarActivity#getInputManager()}.
 *  @hide
 */
public abstract class CarInputManager {
    /**
     * Starts input on the requested {@link android.widget.EditText}, showing the IME.
     * If IME input is already occurring for another view, this call stops input on the previous
     * view and starts input on the new view.
     *
     * This method must only be called from the UI thread. Calling this method from a stopped
     * activity is an illegal operation.
     */
    abstract public void startInput(EditText view);

    /**
     * Stops input, hiding the IME. This method fails silently if the calling application didn't
     * request input and isn't the active IME.
     *
     * This function must only be called from the UI thread.
     */
    abstract public void stopInput();

    /**
     * Returns {@code true} while the InputManager is valid. The InputManager is valid as long as
     * the {@link android.support.car.app.CarActivity} from which it was obtained has
     * been created and not destroyed.
     */
    abstract public boolean isValid();

    /**
     * Returns {@code true} if this InputManager is valid and the IME is active.
     */
    abstract public boolean isInputActive();

    /**
     * Returns {@code true} if IME is active on the given {@link android.widget.EditText}.
     */
    abstract public boolean isCurrentCarEditable(EditText view);

}
