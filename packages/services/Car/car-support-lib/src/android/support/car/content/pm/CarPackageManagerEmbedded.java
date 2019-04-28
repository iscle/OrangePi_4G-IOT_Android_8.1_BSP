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

import android.support.car.CarNotConnectedException;

/**
 * @hide
 */
public class CarPackageManagerEmbedded extends CarPackageManager {

    private final android.car.content.pm.CarPackageManager mManager;

    public CarPackageManagerEmbedded(Object manager) {
        mManager = (android.car.content.pm.CarPackageManager) manager;
    }

    /** @hide */
    public android.car.content.pm.CarPackageManager getManager() {
        return mManager;
    }

    @Override
    public boolean isActivityAllowedWhileDriving(String packageName, String className)
            throws CarNotConnectedException {
        try {
            return mManager.isActivityAllowedWhileDriving(packageName, className);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public boolean isServiceAllowedWhileDriving(String packageName, String className)
            throws CarNotConnectedException {
        try {
            return mManager.isServiceAllowedWhileDriving(packageName, className);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void onCarDisconnected() {
        // nothing to do
    }
}
