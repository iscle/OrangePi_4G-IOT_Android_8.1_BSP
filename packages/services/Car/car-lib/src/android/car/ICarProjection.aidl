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

package android.car;

import android.car.ICarProjectionCallback;
import android.content.Intent;

/**
 * Binder interface for {@link android.car.CarProjectionManager}.
 * Check {@link android.car.CarProjectionManager} APIs for expected behavior of each calls.
 *
 * @hide
 */
interface ICarProjection {
    /**
     * Registers projection runner on projection start with projection service
     * to create reverse binding.
     */
    void registerProjectionRunner(in Intent serviceIntent) = 0;

    /**
     * Unregisters projection runner on projection stop with projection service to create
     * reverse binding.
     */
    void unregisterProjectionRunner(in Intent serviceIntent) = 1;

    /**
     * Registers projection callback.
     * Re-registering same callback with different filter will cause only filter to update.
     */
    void registerProjectionListener(ICarProjectionCallback callback, int filter) = 2;

    /**
     * Unregisters projection callback.
     */
    void unregisterProjectionListener(ICarProjectionCallback callback) = 3;
}
