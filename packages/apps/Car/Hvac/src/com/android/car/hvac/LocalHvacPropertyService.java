/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.hvac;

import android.car.VehicleAreaType;
import android.car.VehicleWindow;
import android.car.VehicleZone;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarProperty;
import android.car.hardware.property.ICarPropertyEventListener;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A local {@link ICarProperty} that is used to mock up data for HVAC.
 */
public class LocalHvacPropertyService {
    private static final int DRIVER_ZONE_ID = VehicleZone.ZONE_ROW_1_LEFT;
    private static final int PASSENGER_ZONE_ID = VehicleZone.ZONE_ROW_1_RIGHT;

    private static final float MIN_TEMP = 16;
    private static final float MAX_TEMP = 32;

    private static final int MAX_FAN_SPEED = 7;
    private static final int MIN_FAN_SPEED = 1;

    private static final int DEFAULT_AREA_ID = 0;

    private static final boolean DEFAULT_POWER_ON = true;
    private static final boolean DEFAULT_DEFROSTER_ON = true;
    private static final boolean DEFAULT_AIR_CIRCULATION_ON = true;
    private static final boolean DEFAULT_AC_ON = true;
    private static final boolean DEFAULT_AUTO_MODE = false;
    private static final int DEFAULT_FAN_SPEED = 3;
    private static final int DEFAULT_FAN_POSITION = 2;
    private static final float DEFAULT_DRIVER_TEMP = 16;
    private static final float DEFAULT_PASSENGER_TEMP = 25;

    private final List<CarPropertyConfig> mPropertyList;
    private final Map<Pair, Object> mProperties = new HashMap<>();
    private final List<ICarPropertyEventListener> mListeners = new ArrayList<>();

    public LocalHvacPropertyService() {
        CarPropertyConfig fanSpeedConfig = CarPropertyConfig.newBuilder(Integer.class,
                CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT,
                VehicleAreaType.VEHICLE_AREA_TYPE_ZONE)
                .addAreaConfig(DEFAULT_AREA_ID, MIN_FAN_SPEED, MAX_FAN_SPEED).build();

        CarPropertyConfig temperatureConfig = CarPropertyConfig.newBuilder(Float.class,
                CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                VehicleAreaType.VEHICLE_AREA_TYPE_ZONE)
                .addAreaConfig(DEFAULT_AREA_ID, MIN_TEMP, MAX_TEMP).build();

        mPropertyList = new ArrayList<>(2);
        mPropertyList.addAll(Arrays.asList(fanSpeedConfig, temperatureConfig));
        setupDefaultValues();
    }

    private final IBinder mCarPropertyService = new ICarProperty.Stub(){
        @Override
        public void registerListener(ICarPropertyEventListener listener) throws RemoteException {
            mListeners.add(listener);
        }

        @Override
        public void unregisterListener(ICarPropertyEventListener listener) throws RemoteException {
            mListeners.remove(listener);
        }

        @Override
        public List<CarPropertyConfig> getPropertyList() throws RemoteException {
            return mPropertyList;
        }

        @Override
        public CarPropertyValue getProperty(int prop, int zone) throws RemoteException {
            return new CarPropertyValue(prop, zone, mProperties.get(new Pair(prop, zone)));
        }

        @Override
        public void setProperty(CarPropertyValue prop) throws RemoteException {
            mProperties.put(new Pair(prop.getPropertyId(), prop.getAreaId()), prop.getValue());
            for (ICarPropertyEventListener listener : mListeners) {
                listener.onEvent(
                        new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE, prop));
            }
        }
    };

    public IBinder getCarPropertyService() {
        return mCarPropertyService;
    }

    private void setupDefaultValues() {
        mProperties.put(new Pair<>(CarHvacManager.ID_ZONED_HVAC_POWER_ON,
                VehicleZone.ZONE_ROW_1_ALL), DEFAULT_POWER_ON);
        mProperties.put(new Pair<>(CarHvacManager.ID_WINDOW_DEFROSTER_ON,
                VehicleWindow.WINDOW_FRONT_WINDSHIELD), DEFAULT_DEFROSTER_ON);
        mProperties.put(new Pair<>(CarHvacManager.ID_WINDOW_DEFROSTER_ON,
                VehicleWindow.WINDOW_REAR_WINDSHIELD), DEFAULT_DEFROSTER_ON);
        mProperties.put(new Pair<>(CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON,
                VehicleZone.ZONE_ROW_1_ALL), DEFAULT_AIR_CIRCULATION_ON);
        mProperties.put(new Pair<>(CarHvacManager.ID_ZONED_AC_ON,
                VehicleZone.ZONE_ROW_1_ALL), DEFAULT_AC_ON);
        mProperties.put(new Pair<>(CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON,
                VehicleZone.ZONE_ROW_1_ALL), DEFAULT_AUTO_MODE);

        mProperties.put(new Pair<>(CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT,
                VehicleZone.ZONE_ROW_1_ALL), DEFAULT_FAN_SPEED);
        mProperties.put(new Pair<>(CarHvacManager.ID_ZONED_FAN_POSITION,
                VehicleZone.ZONE_ROW_1_ALL), DEFAULT_FAN_POSITION);

        mProperties.put(new Pair<>(CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                DRIVER_ZONE_ID), DEFAULT_DRIVER_TEMP);
        mProperties.put(new Pair<>(CarHvacManager.ID_ZONED_TEMP_SETPOINT,
                PASSENGER_ZONE_ID), DEFAULT_PASSENGER_TEMP);
    }
}
