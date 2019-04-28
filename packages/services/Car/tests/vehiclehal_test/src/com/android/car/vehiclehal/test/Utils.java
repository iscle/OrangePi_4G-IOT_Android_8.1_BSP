/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car.vehiclehal.test;

import android.annotation.Nullable;
import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.os.RemoteException;
import android.util.Log;

import com.android.car.vehiclehal.VehiclePropValueBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;

final class Utils {
    private Utils() {}

    private static final String TAG = concatTag(Utils.class);

    static String concatTag(Class clazz) {
        return "VehicleHalTest." + clazz.getSimpleName();
    }

    static boolean isVhalPropertyAvailable(IVehicle vehicle, int prop) throws RemoteException {
        return vehicle.getAllPropConfigs()
                .stream()
                .anyMatch((VehiclePropConfig config) -> config.prop == prop);
    }

    static VehiclePropValue readVhalProperty(
        IVehicle vehicle,
        VehiclePropValue request,
        java.util.function.BiFunction<Integer, VehiclePropValue, Boolean> f) {
        vehicle = Objects.requireNonNull(vehicle);
        request = Objects.requireNonNull(request);
        VehiclePropValue vpv[] = new VehiclePropValue[] {null};
        try {
            vehicle.get(
                request,
                (int status, VehiclePropValue propValue) -> {
                    if (f.apply(status, propValue)) {
                        vpv[0] = propValue;
                    }
                });
        } catch (RemoteException e) {
            Log.w(TAG, "attempt to read VHAL property " + request + " caused RemoteException: ", e);
        }
        return vpv[0];
    }

    static VehiclePropValue readVhalProperty(
        IVehicle vehicle,
        int propertyId,
        java.util.function.BiFunction<Integer, VehiclePropValue, Boolean> f) {
        return readVhalProperty(vehicle, propertyId, 0, f);
    }

    static VehiclePropValue readVhalProperty(
            IVehicle vehicle,
            int propertyId,
            int areaId,
            java.util.function.BiFunction<Integer, VehiclePropValue, Boolean> f) {
        VehiclePropValue request =
            VehiclePropValueBuilder.newBuilder(propertyId).setAreaId(areaId).build();
        return readVhalProperty(vehicle, request, f);
    }

    @Nullable
    static IVehicle getVehicle() throws RemoteException {
        IVehicle service;
        try {
            service = android.hardware.automotive.vehicle.V2_0.IVehicle.getService();
        } catch (NoSuchElementException ex) {
            Log.d(TAG, "Couldn't connect to vehicle@2.1, connecting to vehicle@2.0...");
            service =  IVehicle.getService();
        }
        Log.d(TAG, "Connected to IVehicle service: " + service);
        return service;
    }
}
