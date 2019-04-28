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

package android.support.car;


/** @hide */
public class CarInfoManagerEmbedded extends CarInfoManager {

    private final android.car.CarInfoManager mManager;

    /** @hide */
    CarInfoManagerEmbedded(Object manager) {
        mManager = (android.car.CarInfoManager) manager;
    }

    @Override
    public String getManufacturer() throws CarNotConnectedException {
        try {
            return mManager.getManufacturer();
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public String getModel() throws CarNotConnectedException {
        try {
            return mManager.getModel();
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public String getModelYear() throws CarNotConnectedException {
        try {
            return mManager.getModelYear();
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public String getVehicleId() throws CarNotConnectedException {
        try {
            return mManager.getVehicleId();
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public String getHeadunitManufacturer() throws CarNotConnectedException {
        return null;
    }

    @Override
    public String getHeadunitModel() throws CarNotConnectedException {
        return null; // N/A
    }

    @Override
    public String getHeadunitSoftwareBuild() throws CarNotConnectedException {
        return null; // N/A
    }

    @Override
    public String getHeadunitSoftwareVersion() throws CarNotConnectedException {
        return null; // N/A
    }

    @Override
    public int getDriverPosition() throws CarNotConnectedException {
        return CarInfoManager.DRIVER_SIDE_UNKNOWN; // N/A
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        //nothing to do
    }
}
