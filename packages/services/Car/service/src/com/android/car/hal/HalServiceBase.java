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

package com.android.car.hal;


import android.annotation.Nullable;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Common interface for all HAL service like sensor HAL.
 * Each HAL service is connected with XyzService supporting XyzManager,
 * and will translate HAL data into car api specific format.
 */
public abstract class HalServiceBase {
    /** For dispatching events. Kept here to avoid alloc every time */
    private final LinkedList<VehiclePropValue> mDispatchList = new LinkedList<VehiclePropValue>();

    final static int NOT_SUPPORTED_PROPERTY = -1;

    public List<VehiclePropValue> getDispatchList() {
        return mDispatchList;
    }

    /** initialize */
    public abstract void init();

    /** release and stop operation */
    public abstract void release();

    /**
     * return supported properties among all properties.
     * @return null if no properties are supported
     */
    /**
     * Take supported properties from given allProperties and return List of supported properties.
     * @param allProperties
     * @return null if no properties are supported.
     */
    @Nullable
    public Collection<VehiclePropConfig> takeSupportedProperties(
            Collection<VehiclePropConfig> allProperties) {
        return null;
    }

    public abstract void handleHalEvents(List<VehiclePropValue> values);

    public void handlePropertySetError(int property, int area) {}

    public abstract void dump(PrintWriter writer);

    /**
     * Helper class that maintains bi-directional mapping between manager's property
     * Id (public or system API) and vehicle HAL property Id.
     *
     * <p>This class is supposed to be immutable. Use {@link #create(int[])} factory method to
     * instantiate this class.
     */
    static class ManagerToHalPropIdMap {
        private final BidirectionalSparseIntArray mMap;

        /**
         * Creates {@link ManagerToHalPropIdMap} for provided [manager prop Id, hal prop Id] pairs.
         *
         * <p> The input array should have an odd number of elements.
         */
        static ManagerToHalPropIdMap create(int... mgrToHalPropIds) {
            return new ManagerToHalPropIdMap(BidirectionalSparseIntArray.create(mgrToHalPropIds));
        }

        private ManagerToHalPropIdMap(BidirectionalSparseIntArray map) {
            mMap = map;
        }

        int getHalPropId(int managerPropId) {
            return mMap.getValue(managerPropId, NOT_SUPPORTED_PROPERTY);
        }

        int getManagerPropId(int halPropId) {
            return mMap.getKey(halPropId, NOT_SUPPORTED_PROPERTY);
        }
    }
}
