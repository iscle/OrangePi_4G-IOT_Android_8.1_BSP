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
package android.car.test;

import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * API for testing only. Allows mocking vehicle hal.
 * @hide
 */
@SystemApi
public final class CarTestManager implements CarManagerBase {

    private final ICarTest mService;


    public CarTestManager(IBinder carServiceBinder) {
        mService = ICarTest.Stub.asInterface(carServiceBinder);
    }

    @Override
    public void onCarDisconnected() {
        // should not happen for embedded
    }

    /**
     * Releases all car services. This make sense for test purpose when it is necessary to reduce
     * interference between testing and real instances of Car Service. For example changing audio
     * focus in CarAudioService may affect framework's AudioManager listeners. AudioManager has a
     * lot of complex logic which is hard to mock.
     */
    @RequiresPermission(Car.PERMISSION_CAR_TEST_SERVICE)
    public void stopCarService(IBinder token) {
        try {
            mService.stopCarService(token);
        } catch (RemoteException e) {
            handleRemoteException(e);
        }
    }

    /**
     * Re-initializes previously released car service.
     *
     * @see {@link #stopCarService(IBinder)}
     */
    @RequiresPermission(Car.PERMISSION_CAR_TEST_SERVICE)
    public void startCarService(IBinder token) {
        try {
            mService.startCarService(token);
        } catch (RemoteException e) {
            handleRemoteException(e);
        }
    }

    private static void handleRemoteException(RemoteException e) {
        // let test fail
        throw new RuntimeException(e);
    }
}
