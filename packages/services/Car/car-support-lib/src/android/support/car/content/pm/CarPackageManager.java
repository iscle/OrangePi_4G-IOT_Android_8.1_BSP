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

package android.support.car.content.pm;

import android.support.car.CarManagerBase;
import android.support.car.CarNotConnectedException;

/**
 * Provides car specific API related to package management.
 * @hide
 */
public abstract class CarPackageManager implements CarManagerBase {

    /**
     * Check if given activity is allowed while driving.
     */
    public abstract boolean isActivityAllowedWhileDriving(String packageName, String className)
            throws CarNotConnectedException;

    /**
     * Check if given service is allowed while driving.
     */
    public abstract boolean isServiceAllowedWhileDriving(String packageName, String className)
            throws CarNotConnectedException;
}
