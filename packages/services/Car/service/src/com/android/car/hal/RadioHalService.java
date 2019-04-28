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
import android.car.hardware.radio.CarRadioEvent;
import android.car.hardware.radio.CarRadioPreset;
import android.hardware.radio.RadioManager;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VehicleRadioConstants;
import android.util.Log;

import com.android.car.CarLog;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * This class exposes the Radio related features in the HAL layer.
 *
 * The current set of features support radio presets. The rest of the radio functionality is already
 * covered under RadioManager API.
 */
public class RadioHalService extends HalServiceBase {
    public static boolean DBG = false;
    public static String TAG = CarLog.TAG_HAL + ".RadioHalService";

    private int mPresetCount = 0;
    private VehicleHal mHal;
    private RadioListener mListener;

    public interface RadioListener {
        void onEvent(CarRadioEvent event);
    }

    public RadioHalService(VehicleHal hal) {
        mHal = hal;
    }

    @Override
    public synchronized void init() {
    }

    @Override
    public synchronized void release() {
        mListener = null;
    }

    @Override
    public synchronized Collection<VehiclePropConfig> takeSupportedProperties(
            Collection<VehiclePropConfig> allProperties) {
        Collection<VehiclePropConfig> supported = new LinkedList<>();
        for (VehiclePropConfig p : allProperties) {
            if (handleRadioProperty(p)) {
                supported.add(p);
            }
        }
        return supported;
    }

    @Override
    public void handleHalEvents(List<VehiclePropValue> values) {
        if (DBG) {
            Log.d(TAG, "handleHalEvents");
        }
        RadioHalService.RadioListener radioListener;
        synchronized (this) {
            radioListener = mListener;
        }

        if (radioListener == null) {
            Log.e(TAG, "radio listener is null, ignoring event: " + values);
            return;
        }

        for (VehiclePropValue v : values) {
            CarRadioEvent radioEvent = createCarRadioEvent(v);
            if (radioEvent != null) {
                if (DBG) {
                    Log.d(TAG, "Sending event to listener: " + radioEvent);
                }
                radioListener.onEvent(radioEvent);
            } else {
                Log.w(TAG, "Value conversion failed: " + v);
            }
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*RadioHal*");
        writer.println("**Supported properties**");
        writer.println(VehicleProperty.RADIO_PRESET);
        if (mListener != null) {
            writer.println("Hal service registered.");
        }
    }

    public synchronized void registerListener(RadioListener listener) {
        if (DBG) {
            Log.d(TAG, "registerListener");
        }
        mListener = listener;

        // Subscribe to all radio properties.
        mHal.subscribeProperty(this, VehicleProperty.RADIO_PRESET);
    }

    public synchronized void unregisterListener() {
        if (DBG) {
            Log.d(TAG, "unregisterListener");
        }
        mListener = null;

        // Unsubscribe from all properties.
        mHal.unsubscribeProperty(this, VehicleProperty.RADIO_PRESET);
    }

    public synchronized int getPresetCount() {
        Log.d(TAG, "get preset count: " + mPresetCount);
        return mPresetCount;
    }

    @Nullable
    public CarRadioPreset getRadioPreset(int presetNumber) {
        // Check if the preset number is out of range. We should return NULL if that is the case.
        if (DBG) {
            Log.d(TAG, "getRadioPreset called with preset number " + presetNumber);
        }
        if (!isValidPresetNumber(presetNumber)) {
            throw new IllegalArgumentException("Preset number not valid: " + presetNumber);
        }

        VehiclePropValue presetNumberValue = new VehiclePropValue();
        presetNumberValue.prop = VehicleProperty.RADIO_PRESET;
        presetNumberValue.value.int32Values.addAll(Arrays.asList(presetNumber, 0, 0, 0));

        VehiclePropValue presetConfig;
        try {
            presetConfig = mHal.get(presetNumberValue);
        } catch (PropertyTimeoutException e) {
            Log.e(TAG, "property VehicleProperty.RADIO_PRESET not ready", e);
            return null;
        }
        // Sanity check the output from HAL.
        if (presetConfig.value.int32Values.size() != 4) {
            Log.e(TAG, "Return value does not have 4 elements: " +
                Arrays.toString(presetConfig.value.int32Values.toArray()));
            throw new IllegalStateException(
                "Invalid preset returned from service: "
                        + Arrays.toString(presetConfig.value.int32Values.toArray()));
        }

        int retPresetNumber = presetConfig.value.int32Values.get(0);
        int retBand = presetConfig.value.int32Values.get(1);
        int retChannel = presetConfig.value.int32Values.get(2);
        int retSubChannel = presetConfig.value.int32Values.get(3);
        if (retPresetNumber != presetNumber) {
            Log.e(TAG, "Preset number is not the same: " + presetNumber + " vs " + retPresetNumber);
            return null;
        }
        if (!isValidBand(retBand)) return null;

        // Return the actual config.
        CarRadioPreset retConfig =
            new CarRadioPreset(retPresetNumber, retBand, retChannel, retSubChannel);
        if (DBG) {
            Log.d(TAG, "Preset obtained: " + retConfig);
        }
        return retConfig;
    }

    public boolean setRadioPreset(CarRadioPreset preset) {
        if (DBG) {
            Log.d(TAG, "setRadioPreset with config " + preset);
        }

        if (!isValidPresetNumber(preset.getPresetNumber()) ||
            !isValidBand(preset.getBand())) {
            return false;
        }

        try {
            mHal.set(VehicleProperty.RADIO_PRESET).to(new int[] {
                    preset.getPresetNumber(),
                    preset.getBand(),
                    preset.getChannel(),
                    preset.getSubChannel()});
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_POWER, "cannot set to RADIO_PRESET", e);
            return false;
        }
        return true;
    }

    private boolean isValidPresetNumber(int presetNumber) {
        // Check for preset number.
        if (presetNumber < VehicleRadioConstants.VEHICLE_RADIO_PRESET_MIN_VALUE
            || presetNumber > mPresetCount) {
            Log.e(TAG, "Preset number not in range (1, " + mPresetCount + ") - " + presetNumber);
            return false;
        }
        return true;
    }

    private boolean isValidBand(int band) {
        // Check for band info.
        if (band != RadioManager.BAND_AM &&
            band != RadioManager.BAND_FM &&
            band != RadioManager.BAND_FM_HD &&
            band != RadioManager.BAND_AM_HD) {
            Log.e(TAG, "Preset band is not valid: " + band);
            return false;
        }
        return true;
    }

    private boolean handleRadioProperty(VehiclePropConfig property) {
        switch (property.prop) {
            case VehicleProperty.RADIO_PRESET:
                // Extract the count of presets.
                mPresetCount = property.configArray.get(0);
                Log.d(TAG, "Read presets count: " + mPresetCount);
                return true;
            default:
                return false;
        }
        // Should never come here.
    }

    private CarRadioEvent createCarRadioEvent(VehiclePropValue v) {
        switch (v.prop) {
            case VehicleProperty.RADIO_PRESET:
                int vecSize = v.value.int32Values.size();
                if (vecSize != 4) {
                    Log.e(TAG, "Returned a wrong array size: " + vecSize);
                    return null;
                }

                Integer intValues[] = new Integer[4];
                v.value.int32Values.toArray(intValues);

                // Verify the correctness of the values.
                if (!isValidPresetNumber(intValues[0]) && !isValidBand(intValues[1])) {
                    return null;
                }

                CarRadioPreset preset =
                    new CarRadioPreset(intValues[0], intValues[1], intValues[2], intValues[3]);
                CarRadioEvent event = new CarRadioEvent(CarRadioEvent.RADIO_PRESET, preset);
                return event;
            default:
                Log.e(TAG, "createCarRadioEvent: Value not supported as event: " + v);
                return null;
        }
    }
}
