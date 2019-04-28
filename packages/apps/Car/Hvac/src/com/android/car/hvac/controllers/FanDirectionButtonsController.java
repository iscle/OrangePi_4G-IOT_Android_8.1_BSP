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

import android.car.hardware.hvac.CarHvacManager;
import android.util.SparseIntArray;

import com.android.car.hvac.HvacController;
import com.android.car.hvac.ui.FanDirectionButtons;

/**
 * A controller to handle changes in the fan direction. Also maps fan directions specified
 * in the {@link FanDirectionButtons} to the {@link CarHvacManager}{@code #FAN_POSITION_*} constants
 * in the vehicle hardware.
 */
public class FanDirectionButtonsController {
    private final static int FAN_DIRECTION_COUNT = 4;

    private final FanDirectionButtons mFanDirectionButtons;
    private final HvacController mHvacController;
    private final SparseIntArray mFanDirectionMap = new SparseIntArray(FAN_DIRECTION_COUNT);

    public FanDirectionButtonsController(FanDirectionButtons speedBar, HvacController controller) {
        mFanDirectionButtons = speedBar;
        mHvacController = controller;
        initialize();
    }

    private void initialize() {
        // Note Car specific values are being used here, as not all cars have the floor
        // and defroster fan direction.
        mFanDirectionMap.put(FanDirectionButtons.FAN_DIRECTION_FACE,
                CarHvacManager.FAN_POSITION_FACE);
        mFanDirectionMap.put(FanDirectionButtons.FAN_DIRECTION_FACE_FLOOR,
                CarHvacManager.FAN_POSITION_FACE_AND_FLOOR);
        mFanDirectionMap.put(FanDirectionButtons.FAN_DIRECTION_FLOOR,
                CarHvacManager.FAN_POSITION_FLOOR);
        mFanDirectionMap.put(FanDirectionButtons.FAN_DIRECTION_FLOOR_DEFROSTER,
                CarHvacManager.FAN_POSITION_DEFROST_AND_FLOOR);
        mFanDirectionButtons.setFanDirectionClickListener(mListener);
    }

    private final FanDirectionButtons.FanDirectionClickListener mListener
            = new FanDirectionButtons.FanDirectionClickListener() {
        @Override
        public void onFanDirectionClicked(@FanDirectionButtons.FanDirection int direction) {
            mHvacController.setFanDirection(mFanDirectionMap.get(direction));
        }
    };
}
