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
package com.android.car.hal;

import android.car.hardware.cabin.CarCabinManager;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;

public class CabinHalService extends PropertyHalServiceBase {
    private static final boolean DBG = false;
    private static final String TAG = "CAR.CABIN.HAL";

    private final ManagerToHalPropIdMap mMgrHalPropIdMap = ManagerToHalPropIdMap.create(
            CarCabinManager.ID_DOOR_POS,
            VehicleProperty.DOOR_POS,

            CarCabinManager.ID_DOOR_MOVE,
            VehicleProperty.DOOR_MOVE,

            CarCabinManager.ID_DOOR_LOCK,
            VehicleProperty.DOOR_LOCK,

            CarCabinManager.ID_MIRROR_Z_POS,
            VehicleProperty.MIRROR_Z_POS,

            CarCabinManager.ID_MIRROR_Z_MOVE,
            VehicleProperty.MIRROR_Z_MOVE,

            CarCabinManager.ID_MIRROR_Y_POS,
            VehicleProperty.MIRROR_Y_POS,

            CarCabinManager.ID_MIRROR_Y_MOVE,
            VehicleProperty.MIRROR_Y_MOVE,

            CarCabinManager.ID_MIRROR_LOCK,
            VehicleProperty.MIRROR_LOCK,

            CarCabinManager.ID_MIRROR_FOLD,
            VehicleProperty.MIRROR_FOLD,

            CarCabinManager.ID_SEAT_MEMORY_SELECT,
            VehicleProperty.SEAT_MEMORY_SELECT,

            CarCabinManager.ID_SEAT_MEMORY_SET,
            VehicleProperty.SEAT_MEMORY_SET,

            CarCabinManager.ID_SEAT_BELT_BUCKLED,
            VehicleProperty.SEAT_BELT_BUCKLED,

            CarCabinManager.ID_SEAT_BELT_HEIGHT_POS,
            VehicleProperty.SEAT_BELT_HEIGHT_POS,

            CarCabinManager.ID_SEAT_BELT_HEIGHT_MOVE,
            VehicleProperty.SEAT_BELT_HEIGHT_MOVE,

            CarCabinManager.ID_SEAT_FORE_AFT_POS,
            VehicleProperty.SEAT_FORE_AFT_POS,

            CarCabinManager.ID_SEAT_FORE_AFT_MOVE,
            VehicleProperty.SEAT_FORE_AFT_MOVE,

            CarCabinManager.ID_SEAT_BACKREST_ANGLE_1_POS,
            VehicleProperty.SEAT_BACKREST_ANGLE_1_POS,

            CarCabinManager.ID_SEAT_BACKREST_ANGLE_1_MOVE,
            VehicleProperty.SEAT_BACKREST_ANGLE_1_MOVE,

            CarCabinManager.ID_SEAT_BACKREST_ANGLE_2_POS,
            VehicleProperty.SEAT_BACKREST_ANGLE_2_POS,

            CarCabinManager.ID_SEAT_BACKREST_ANGLE_2_MOVE,
            VehicleProperty.SEAT_BACKREST_ANGLE_2_MOVE,

            CarCabinManager.ID_SEAT_HEIGHT_POS,
            VehicleProperty.SEAT_HEIGHT_POS,

            CarCabinManager.ID_SEAT_HEIGHT_MOVE,
            VehicleProperty.SEAT_HEIGHT_MOVE,

            CarCabinManager.ID_SEAT_DEPTH_POS,
            VehicleProperty.SEAT_DEPTH_POS,

            CarCabinManager.ID_SEAT_DEPTH_MOVE,
            VehicleProperty.SEAT_DEPTH_MOVE,

            CarCabinManager.ID_SEAT_TILT_POS,
            VehicleProperty.SEAT_TILT_POS,

            CarCabinManager.ID_SEAT_TILT_MOVE,
            VehicleProperty.SEAT_TILT_MOVE,

            CarCabinManager.ID_SEAT_LUMBAR_FORE_AFT_POS,
            VehicleProperty.SEAT_LUMBAR_FORE_AFT_POS,

            CarCabinManager.ID_SEAT_LUMBAR_FORE_AFT_MOVE,
            VehicleProperty.SEAT_LUMBAR_FORE_AFT_MOVE,

            CarCabinManager.ID_SEAT_LUMBAR_SIDE_SUPPORT_POS,
            VehicleProperty.SEAT_LUMBAR_SIDE_SUPPORT_POS,

            CarCabinManager.ID_SEAT_LUMBAR_SIDE_SUPPORT_MOVE,
            VehicleProperty.SEAT_LUMBAR_SIDE_SUPPORT_MOVE,

            CarCabinManager.ID_SEAT_HEADREST_HEIGHT_POS,
            VehicleProperty.SEAT_HEADREST_HEIGHT_POS,

            CarCabinManager.ID_SEAT_HEADREST_HEIGHT_MOVE,
            VehicleProperty.SEAT_HEADREST_HEIGHT_MOVE,

            CarCabinManager.ID_SEAT_HEADREST_ANGLE_POS,
            VehicleProperty.SEAT_HEADREST_ANGLE_POS,

            CarCabinManager.ID_SEAT_HEADREST_ANGLE_MOVE,
            VehicleProperty.SEAT_HEADREST_ANGLE_MOVE,

            CarCabinManager.ID_SEAT_HEADREST_FORE_AFT_POS,
            VehicleProperty.SEAT_HEADREST_FORE_AFT_POS,

            CarCabinManager.ID_SEAT_HEADREST_FORE_AFT_MOVE,
            VehicleProperty.SEAT_HEADREST_FORE_AFT_MOVE,

            CarCabinManager.ID_WINDOW_POS,
            VehicleProperty.WINDOW_POS,

            CarCabinManager.ID_WINDOW_MOVE,
            VehicleProperty.WINDOW_MOVE,

            CarCabinManager.ID_WINDOW_VENT_POS,
            VehicleProperty.WINDOW_VENT_POS,

            CarCabinManager.ID_WINDOW_VENT_MOVE,
            VehicleProperty.WINDOW_VENT_MOVE,

            CarCabinManager.ID_WINDOW_LOCK,
            VehicleProperty.WINDOW_LOCK
    );

    public CabinHalService(VehicleHal vehicleHal) {
        super(vehicleHal, TAG, DBG);
    }

    // Convert the Cabin public API property ID to HAL property ID
    @Override
    protected int managerToHalPropId(int propId) {
        return mMgrHalPropIdMap.getHalPropId(propId);
    }

    // Convert he HAL specific property ID to Cabin public API
    @Override
    protected int halToManagerPropId(int halPropId) {
        return mMgrHalPropIdMap.getManagerPropId(halPropId);
    }
}
