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

package com.android.car.vehiclehal.test;

import android.annotation.CheckResult;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyChangeMode;

import java.util.Collection;

/**
 * A builder class for {@link android.hardware.automotive.vehicle.V2_0.VehiclePropConfig}
 */
public class VehiclePropConfigBuilder {

    private final VehiclePropConfig mConfig;

    @CheckResult
    public static VehiclePropConfigBuilder newBuilder(int propId) {
        return new VehiclePropConfigBuilder(propId);
    }

    private VehiclePropConfigBuilder(int propId) {
        mConfig = new VehiclePropConfig();
        mConfig.prop = propId;
        mConfig.access = VehiclePropertyAccess.READ_WRITE;
        mConfig.changeMode = VehiclePropertyChangeMode.ON_CHANGE;
    }

    private VehiclePropConfig clone(VehiclePropConfig propConfig) {
        VehiclePropConfig newConfig = new VehiclePropConfig();

        newConfig.prop = propConfig.prop;
        newConfig.access = propConfig.access;
        newConfig.changeMode = propConfig.changeMode;
        newConfig.supportedAreas = propConfig.supportedAreas;
        newConfig.configFlags = propConfig.configFlags;
        newConfig.configString = propConfig.configString;
        newConfig.minSampleRate = propConfig.minSampleRate;
        newConfig.maxSampleRate = propConfig.maxSampleRate;
        newConfig.configArray.addAll(propConfig.configArray);
        for (VehicleAreaConfig area : propConfig.areaConfigs) {
            VehicleAreaConfig newArea = new VehicleAreaConfig();
            newArea.areaId = area.areaId;
            newArea.minInt32Value = area.minInt32Value;
            newArea.maxInt32Value = area.maxInt32Value;
            newArea.minInt64Value = area.minInt64Value;
            newArea.maxInt64Value = area.maxInt64Value;
            newArea.minFloatValue = area.minFloatValue;
            newArea.maxFloatValue = area.maxFloatValue;
            newConfig.areaConfigs.add(newArea);
        }

        return newConfig;
    }

    @CheckResult
    public VehiclePropConfigBuilder setAccess(int access) {
        mConfig.access = access;
        return this;
    }

    @CheckResult
    public VehiclePropConfigBuilder setChangeMode(int changeMode) {
        mConfig.changeMode = changeMode;
        return this;
    }

    @CheckResult
    public VehiclePropConfigBuilder setSupportedAreas(int supportedAreas) {
        mConfig.supportedAreas = supportedAreas;
        return this;
    }

    @CheckResult
    public VehiclePropConfigBuilder setConfigFlags(int configFlags) {
        mConfig.configFlags = configFlags;
        return this;
    }

    @CheckResult
    public VehiclePropConfigBuilder setConfigString(String configString) {
        mConfig.configString = configString;
        return this;
    }


    @CheckResult
    public VehiclePropConfigBuilder setConfigArray(Collection<Integer> configArray) {
        mConfig.configArray.clear();
        mConfig.configArray.addAll(configArray);
        return this;
    }

    @CheckResult
    public VehiclePropConfigBuilder addAreaConfig(int areaId, int minValue, int maxValue) {
        VehicleAreaConfig area = new VehicleAreaConfig();
        area.areaId = areaId;
        area.minInt32Value = minValue;
        area.maxInt32Value = maxValue;
        mConfig.areaConfigs.add(area);
        return this;
    }

    @CheckResult
    public VehiclePropConfigBuilder addAreaConfig(int areaId, float minValue, float maxValue) {
        VehicleAreaConfig area = new VehicleAreaConfig();
        area.areaId = areaId;
        area.minFloatValue = minValue;
        area.maxFloatValue = maxValue;
        mConfig.areaConfigs.add(area);
        return this;
    }

    public VehiclePropConfig build() {
        return clone(mConfig);
    }
}
