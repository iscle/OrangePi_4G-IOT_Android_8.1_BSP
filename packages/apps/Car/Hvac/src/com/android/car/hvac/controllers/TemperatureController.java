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
package com.android.car.hvac.controllers;

import com.android.car.hvac.HvacController;
import com.android.car.hvac.ui.TemperatureBarOverlay;

/**
 * A controller that handles temperature updates for the driver and passenger.
 */
public class TemperatureController {
    private final TemperatureBarOverlay mDriverTempBar;
    private final TemperatureBarOverlay mPassengerTempBar;
    private final HvacController mHvacController;

    public TemperatureController(TemperatureBarOverlay passengerTemperatureBar,
            TemperatureBarOverlay driverTemperatureBar, HvacController controller) {
        mDriverTempBar = driverTemperatureBar;
        mPassengerTempBar = passengerTemperatureBar;
        mHvacController = controller;

        mHvacController.registerCallback(mCallback);
        mDriverTempBar.setTemperatureChangeListener(mDriverTempClickListener);
        mPassengerTempBar.setTemperatureChangeListener(mPassengerTempClickListener);

        mDriverTempBar.setTemperature(mHvacController.getDriverTemperature());
        mPassengerTempBar.setTemperature(mHvacController.getPassengerTemperature());
    }

    private final HvacController.Callback mCallback = new HvacController.Callback() {
        @Override
        public void onPassengerTemperatureChange(float temp) {
            mPassengerTempBar.setTemperature((int) temp);
        }

        @Override
        public void onDriverTemperatureChange(float temp) {
            mDriverTempBar.setTemperature((int) temp);
        }
    };

    private final TemperatureBarOverlay.TemperatureAdjustClickListener mPassengerTempClickListener =
            new TemperatureBarOverlay.TemperatureAdjustClickListener() {
                @Override
                public void onTemperatureChanged(int temperature) {
                    mHvacController.setPassengerTemperature(temperature);
                }
            };

    private final TemperatureBarOverlay.TemperatureAdjustClickListener mDriverTempClickListener =
            new TemperatureBarOverlay.TemperatureAdjustClickListener() {
                @Override
                public void onTemperatureChanged(int temperature) {
                    mHvacController.setDriverTemperature(temperature);
                }
            };
}
