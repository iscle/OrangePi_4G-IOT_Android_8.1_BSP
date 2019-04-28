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

import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.AUDIO_EXT_ROUTING_HINT;
import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.AUDIO_FOCUS;
import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.AUDIO_HW_VARIANT;
import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.AUDIO_PARAMETERS;
import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.AUDIO_ROUTING_POLICY;
import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.AUDIO_STREAM_STATE;
import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.AUDIO_VOLUME;
import static android.hardware.automotive.vehicle.V2_0.VehicleProperty.AUDIO_VOLUME_LIMIT;
import static com.android.car.CarServiceUtils.toIntArray;

import android.annotation.Nullable;
import android.car.VehicleZoneUtil;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioManager.OnParameterChangeListener;
import android.hardware.automotive.vehicle.V2_0.SubscribeFlags;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioContextFlag;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioExtFocusFlag;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioFocusIndex;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioFocusRequest;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioFocusState;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioHwVariantConfigFlag;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioRoutingPolicyIndex;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioVolumeCapabilityFlag;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioVolumeIndex;
import android.hardware.automotive.vehicle.V2_0.VehicleAudioVolumeLimitIndex;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.text.TextUtils;
import android.util.Log;

import com.android.car.AudioRoutingPolicy;
import com.android.car.CarAudioAttributesUtil;
import com.android.car.CarLog;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AudioHalService extends HalServiceBase {
    public static final int VEHICLE_AUDIO_FOCUS_REQUEST_INVALID = -1;
    public static final int VEHICLE_AUDIO_FOCUS_REQUEST_GAIN =
            VehicleAudioFocusRequest.REQUEST_GAIN;
    public static final int VEHICLE_AUDIO_FOCUS_REQUEST_GAIN_TRANSIENT =
            VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT;
    public static final int VEHICLE_AUDIO_FOCUS_REQUEST_GAIN_TRANSIENT_MAY_DUCK =
            VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT_MAY_DUCK;
    public static final int VEHICLE_AUDIO_FOCUS_REQUEST_GAIN_TRANSIENT_NO_DUCK =
            VehicleAudioFocusRequest.REQUEST_GAIN_TRANSIENT_NO_DUCK;
    public static final int VEHICLE_AUDIO_FOCUS_REQUEST_RELEASE =
            VehicleAudioFocusRequest.REQUEST_RELEASE;

    public static final int VEHICLE_AUDIO_FOCUS_STATE_INVALID = -1;
    public static final int VEHICLE_AUDIO_FOCUS_STATE_GAIN =
            VehicleAudioFocusState.STATE_GAIN;
    public static final int VEHICLE_AUDIO_FOCUS_STATE_GAIN_TRANSIENT =
            VehicleAudioFocusState.STATE_GAIN_TRANSIENT;
    public static final int VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT_CAN_DUCK =
            VehicleAudioFocusState.STATE_LOSS_TRANSIENT_CAN_DUCK;
    public static final int VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT =
            VehicleAudioFocusState.STATE_LOSS_TRANSIENT;
    public static final int VEHICLE_AUDIO_FOCUS_STATE_LOSS =
            VehicleAudioFocusState.STATE_LOSS;
    public static final int VEHICLE_AUDIO_FOCUS_STATE_LOSS_TRANSIENT_EXLCUSIVE =
            VehicleAudioFocusState.STATE_LOSS_TRANSIENT_EXLCUSIVE;

    public static final int VEHICLE_AUDIO_STREAM_STATE_STOPPED = 0;
    public static final int VEHICLE_AUDIO_STREAM_STATE_STARTED = 1;

    public static final int VEHICLE_AUDIO_EXT_FOCUS_NONE_FLAG =
            VehicleAudioExtFocusFlag.NONE_FLAG;
    public static final int VEHICLE_AUDIO_EXT_FOCUS_CAR_PERMANENT_FLAG =
            VehicleAudioExtFocusFlag.PERMANENT_FLAG;
    public static final int VEHICLE_AUDIO_EXT_FOCUS_CAR_TRANSIENT_FLAG =
            VehicleAudioExtFocusFlag.TRANSIENT_FLAG;
    public static final int VEHICLE_AUDIO_EXT_FOCUS_CAR_PLAY_ONLY_FLAG =
            VehicleAudioExtFocusFlag.PLAY_ONLY_FLAG;
    public static final int VEHICLE_AUDIO_EXT_FOCUS_CAR_MUTE_MEDIA_FLAG =
            VehicleAudioExtFocusFlag.MUTE_MEDIA_FLAG;

    public static final int STREAM_NUM_DEFAULT = 0;

    public static final int FOCUS_STATE_ARRAY_INDEX_STATE =
            VehicleAudioFocusIndex.FOCUS;
    public static final int FOCUS_STATE_ARRAY_INDEX_STREAMS =
            VehicleAudioFocusIndex.STREAMS;
    public static final int FOCUS_STATE_ARRAY_INDEX_EXTERNAL_FOCUS =
            VehicleAudioFocusIndex.EXTERNAL_FOCUS_STATE;

    public static final int AUDIO_CONTEXT_MUSIC_FLAG =
            VehicleAudioContextFlag.MUSIC_FLAG;
    public static final int AUDIO_CONTEXT_NAVIGATION_FLAG =
            VehicleAudioContextFlag.NAVIGATION_FLAG;
    public static final int AUDIO_CONTEXT_VOICE_COMMAND_FLAG =
            VehicleAudioContextFlag.VOICE_COMMAND_FLAG;
    public static final int AUDIO_CONTEXT_CALL_FLAG =
            VehicleAudioContextFlag.CALL_FLAG;
    public static final int AUDIO_CONTEXT_ALARM_FLAG =
            VehicleAudioContextFlag.ALARM_FLAG;
    public static final int AUDIO_CONTEXT_NOTIFICATION_FLAG =
            VehicleAudioContextFlag.NOTIFICATION_FLAG;
    public static final int AUDIO_CONTEXT_UNKNOWN_FLAG =
            VehicleAudioContextFlag.UNKNOWN_FLAG;
    public static final int AUDIO_CONTEXT_SAFETY_ALERT_FLAG =
            VehicleAudioContextFlag.SAFETY_ALERT_FLAG;
    public static final int AUDIO_CONTEXT_RADIO_FLAG =
            VehicleAudioContextFlag.RADIO_FLAG;
    public static final int AUDIO_CONTEXT_CD_ROM_FLAG =
            VehicleAudioContextFlag.CD_ROM_FLAG;
    public static final int AUDIO_CONTEXT_AUX_AUDIO_FLAG =
            VehicleAudioContextFlag.AUX_AUDIO_FLAG;
    public static final int AUDIO_CONTEXT_SYSTEM_SOUND_FLAG =
            VehicleAudioContextFlag.SYSTEM_SOUND_FLAG;
    public static final int AUDIO_CONTEXT_EXT_SOURCE_FLAG =
            VehicleAudioContextFlag.EXT_SOURCE_FLAG;
    public static final int AUDIO_CONTEXT_RINGTONE_FLAG =
            VehicleAudioContextFlag.RINGTONE_FLAG;

    public interface AudioHalFocusListener {
        /**
         * Audio focus change from car.
         * @param focusState
         * @param streams
         * @param externalFocus Flags of active external audio focus.
         *            0 means no external audio focus.
         */
        void onFocusChange(int focusState, int streams, int externalFocus);
        /**
         * Stream state change (start / stop) from android
         * @param streamNumber stream number like 0, 1, ...
         * @param streamActive Whether the stream is active or not.
         */
        void onStreamStatusChange(int streamNumber, boolean streamActive);
    }

    public interface AudioHalVolumeListener {
        /**
         * Audio volume change from car.
         * @param streamNumber
         * @param volume
         * @param volumeState
         */
        void onVolumeChange(int streamNumber, int volume, int volumeState);
        /**
         * Volume limit change from car.
         * @param streamNumber
         * @param volume
         */
        void onVolumeLimitChange(int streamNumber, int volume);
    }

    private static final boolean DBG = false;

    private final VehicleHal mVehicleHal;
    private AudioHalFocusListener mFocusListener;
    private AudioHalVolumeListener mVolumeListener;
    private int mVariant;

    private final HashMap<Integer, VehiclePropConfig> mProperties = new HashMap<>();

    private OnParameterChangeListener mOnParameterChangeListener;

    public AudioHalService(VehicleHal vehicleHal) {
        mVehicleHal = vehicleHal;
    }

    public synchronized void setFocusListener(AudioHalFocusListener focusListener) {
        mFocusListener = focusListener;
    }

    public synchronized void setVolumeListener(AudioHalVolumeListener volumeListener) {
        mVolumeListener = volumeListener;
    }

    public void setAudioRoutingPolicy(AudioRoutingPolicy policy) {
        if (!mVehicleHal.isPropertySupported(VehicleProperty.AUDIO_ROUTING_POLICY)) {
            Log.w(CarLog.TAG_AUDIO,
                    "Vehicle HAL did not implement VehicleProperty.AUDIO_ROUTING_POLICY");
            return;
        }
        int[] policyToSet = new int[2];
        for (int i = 0; i < policy.getPhysicalStreamsCount(); i++) {
            policyToSet[VehicleAudioRoutingPolicyIndex.STREAM] = i;
            int contexts = 0;
            for (int logicalStream : policy.getLogicalStreamsForPhysicalStream(i)) {
                contexts |= logicalStreamToHalContextType(logicalStream);
            }
            policyToSet[VehicleAudioRoutingPolicyIndex.CONTEXTS] = contexts;
            try {
                mVehicleHal.set(AUDIO_ROUTING_POLICY).to(policyToSet);
            } catch (PropertyTimeoutException e) {
                Log.e(CarLog.TAG_AUDIO, "Cannot write to VehicleProperty.AUDIO_ROUTING_POLICY", e);
            }
        }
    }

    /**
     * Returns the volume limits of a stream. Returns null if max value wasn't defined for
     * AUDIO_VOLUME property.
     */
    @Nullable
    public synchronized Integer getStreamMaxVolume(int stream) {
        VehiclePropConfig config = mProperties.get(VehicleProperty.AUDIO_VOLUME);
        if (config == null) {
            throw new IllegalStateException("VehicleProperty.AUDIO_VOLUME not supported");
        }
        int supportedContext = getSupportedAudioVolumeContexts();

        int MAX_VALUES_FIRST_ELEMENT_INDEX = 4;
        ArrayList<Integer> maxValues = new ArrayList<>();
        for (int i = MAX_VALUES_FIRST_ELEMENT_INDEX; i < config.configArray.size(); i++) {
            maxValues.add(config.configArray.get(i));
        }

        Integer result = null;
        if (supportedContext != 0) {
            int index = VehicleZoneUtil.zoneToIndex(supportedContext, stream);
            if (index < maxValues.size()) {
                result = maxValues.get(index);
            }
        } else {
            if (stream < maxValues.size()) {
                result = maxValues.get(stream);
            }
        }

        if (result == null) {
            Log.e(CarLog.TAG_AUDIO, "No min/max volume found in vehicle" +
                    " prop config for stream: " + stream);
        }

        return result;
    }

    /**
     * Convert car audio manager stream type (usage) into audio context type.
     */
    public static int logicalStreamToHalContextType(int logicalStream) {
        return logicalStreamWithExtTypeToHalContextType(logicalStream, null);
    }

    public static int logicalStreamWithExtTypeToHalContextType(int logicalStream, String extType) {
        switch (logicalStream) {
            case CarAudioManager.CAR_AUDIO_USAGE_RADIO:
                return VehicleAudioContextFlag.RADIO_FLAG;
            case CarAudioManager.CAR_AUDIO_USAGE_VOICE_CALL:
                return VehicleAudioContextFlag.CALL_FLAG;
            case CarAudioManager.CAR_AUDIO_USAGE_RINGTONE:
                return VehicleAudioContextFlag.RINGTONE_FLAG;
            case CarAudioManager.CAR_AUDIO_USAGE_MUSIC:
                return VehicleAudioContextFlag.MUSIC_FLAG;
            case CarAudioManager.CAR_AUDIO_USAGE_NAVIGATION_GUIDANCE:
                return VehicleAudioContextFlag.NAVIGATION_FLAG;
            case CarAudioManager.CAR_AUDIO_USAGE_VOICE_COMMAND:
                return VehicleAudioContextFlag.VOICE_COMMAND_FLAG;
            case CarAudioManager.CAR_AUDIO_USAGE_ALARM:
                return VehicleAudioContextFlag.ALARM_FLAG;
            case CarAudioManager.CAR_AUDIO_USAGE_NOTIFICATION:
                return VehicleAudioContextFlag.NOTIFICATION_FLAG;
            case CarAudioManager.CAR_AUDIO_USAGE_SYSTEM_SAFETY_ALERT:
                return VehicleAudioContextFlag.SAFETY_ALERT_FLAG;
            case CarAudioManager.CAR_AUDIO_USAGE_SYSTEM_SOUND:
                return VehicleAudioContextFlag.SYSTEM_SOUND_FLAG;
            case CarAudioManager.CAR_AUDIO_USAGE_DEFAULT:
                return VehicleAudioContextFlag.UNKNOWN_FLAG;
            case CarAudioManager.CAR_AUDIO_USAGE_EXTERNAL_AUDIO_SOURCE:
                if (extType != null) {
                    switch (extType) {
                    case CarAudioManager.CAR_EXTERNAL_SOURCE_TYPE_CD_DVD:
                        return VehicleAudioContextFlag.CD_ROM_FLAG;
                    case CarAudioManager.CAR_EXTERNAL_SOURCE_TYPE_AUX_IN0:
                    case CarAudioManager.CAR_EXTERNAL_SOURCE_TYPE_AUX_IN1:
                        return VehicleAudioContextFlag.AUX_AUDIO_FLAG;
                    default:
                        if (extType.startsWith("RADIO_")) {
                            return VehicleAudioContextFlag.RADIO_FLAG;
                        } else {
                            return VehicleAudioContextFlag.EXT_SOURCE_FLAG;
                        }
                    }
                } else { // no external source specified. fall back to radio
                    return VehicleAudioContextFlag.RADIO_FLAG;
                }
            case CarAudioAttributesUtil.CAR_AUDIO_USAGE_CARSERVICE_BOTTOM:
            case CarAudioAttributesUtil.CAR_AUDIO_USAGE_CARSERVICE_CAR_PROXY:
            case CarAudioAttributesUtil.CAR_AUDIO_USAGE_CARSERVICE_MEDIA_MUTE:
                // internal tag not associated with any stream
                return 0;
            default:
                Log.w(CarLog.TAG_AUDIO, "Unknown logical stream:" + logicalStream);
                return 0;
        }
    }

    /**
     * Converts car audio context type to car stream usage.
     */
    public static int carContextToCarUsage(int carContext) {
        switch (carContext) {
            case VehicleAudioContextFlag.MUSIC_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_MUSIC;
            case VehicleAudioContextFlag.NAVIGATION_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_NAVIGATION_GUIDANCE;
            case VehicleAudioContextFlag.ALARM_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_ALARM;
            case VehicleAudioContextFlag.VOICE_COMMAND_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_VOICE_COMMAND;
            case VehicleAudioContextFlag.AUX_AUDIO_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_EXTERNAL_AUDIO_SOURCE;
            case VehicleAudioContextFlag.CALL_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_VOICE_CALL;
            case VehicleAudioContextFlag.RINGTONE_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_RINGTONE;
            case VehicleAudioContextFlag.CD_ROM_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_EXTERNAL_AUDIO_SOURCE;
            case VehicleAudioContextFlag.NOTIFICATION_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_NOTIFICATION;
            case VehicleAudioContextFlag.RADIO_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_RADIO;
            case VehicleAudioContextFlag.SAFETY_ALERT_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_SYSTEM_SAFETY_ALERT;
            case VehicleAudioContextFlag.SYSTEM_SOUND_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_SYSTEM_SOUND;
            case VehicleAudioContextFlag.UNKNOWN_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_DEFAULT;
            case VehicleAudioContextFlag.EXT_SOURCE_FLAG:
                return CarAudioManager.CAR_AUDIO_USAGE_EXTERNAL_AUDIO_SOURCE;
            default:
                Log.w(CarLog.TAG_AUDIO, "Unknown car context:" + carContext);
                return 0;
        }
    }

    public void requestAudioFocusChange(int request, int streams, int audioContexts) {
        requestAudioFocusChange(request, streams, VEHICLE_AUDIO_EXT_FOCUS_NONE_FLAG, audioContexts);
    }

    public void requestAudioFocusChange(int request, int streams, int extFocus, int audioContexts) {
        int[] payload = { request, streams, extFocus, audioContexts };
        try {
            mVehicleHal.set(AUDIO_FOCUS).to(payload);
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_AUDIO, "Cannot write to VehicleProperty.AUDIO_FOCUS", e);
            // focus timeout will reset it anyway
        }
    }

    public void setStreamVolume(int streamType, int index) {
        int[] payload = {streamType, index, 0};
        try {
            mVehicleHal.set(VehicleProperty.AUDIO_VOLUME).to(payload);
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_AUDIO, "Cannot write to VehicleProperty.AUDIO_VOLUME", e);
            //TODO should reset volume, bug: 32096870
        }
    }

    public int getStreamVolume(int stream) {
        int[] volume = {stream, 0, 0};
        VehiclePropValue requestedStreamVolume = new VehiclePropValue();
        requestedStreamVolume.prop = VehicleProperty.AUDIO_VOLUME;
        requestedStreamVolume.value.int32Values.addAll(Arrays.asList(stream, 0 , 0));
        VehiclePropValue propValue;
        try {
            propValue = mVehicleHal.get(requestedStreamVolume);
        }  catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_AUDIO, "VehicleProperty.AUDIO_VOLUME not ready", e);
            return 0;
        }

        if (propValue.value.int32Values.size() != 3) {
            Log.e(CarLog.TAG_AUDIO, "returned value not valid");
            throw new IllegalStateException("Invalid preset returned from service: "
                    + Arrays.toString(propValue.value.int32Values.toArray()));
        }

        int retStreamNum = propValue.value.int32Values.get(0);
        int retVolume = propValue.value.int32Values.get(1);
        int retVolumeState = propValue.value.int32Values.get(2);

        if (retStreamNum != stream) {
            Log.e(CarLog.TAG_AUDIO, "Stream number is not the same: "
                    + stream + " vs " + retStreamNum);
            throw new IllegalStateException("Stream number is not the same");
        }
        return retVolume;
    }

    public synchronized int getHwVariant() {
        return mVariant;
    }

    public synchronized boolean isRadioExternal() {
        VehiclePropConfig config = mProperties.get(VehicleProperty.AUDIO_HW_VARIANT);
        if (config == null) {
            return true;
        }
        return (config.configArray.get(0)
                & VehicleAudioHwVariantConfigFlag.INTERNAL_RADIO_FLAG) == 0;
    }

    public synchronized boolean isFocusSupported() {
        return isPropertySupportedLocked(AUDIO_FOCUS);
    }

    public synchronized boolean isAudioVolumeSupported() {
        return isPropertySupportedLocked(VehicleProperty.AUDIO_VOLUME);
    }

    public synchronized int getSupportedAudioVolumeContexts() {
        if (!isPropertySupportedLocked(VehicleProperty.AUDIO_VOLUME)) {
            throw new IllegalStateException("VehicleProperty.AUDIO_VOLUME not supported");
        }
        VehiclePropConfig config = mProperties.get(VehicleProperty.AUDIO_VOLUME);
        return config.configArray.get(0);
    }

    /**
     * Whether external audio module can memorize logical audio volumes or not.
     * @return
     */
    public synchronized boolean isExternalAudioVolumePersistent() {
        if (!isPropertySupportedLocked(VehicleProperty.AUDIO_VOLUME)) {
            throw new IllegalStateException("VehicleProperty.AUDIO_VOLUME not supported");
        }
        VehiclePropConfig config = mProperties.get(VehicleProperty.AUDIO_VOLUME);
        if (config.configArray.get(0) == 0) { // physical streams only
            return false;
        }
        if ((config.configArray.get(1)
                & VehicleAudioVolumeCapabilityFlag.PERSISTENT_STORAGE) != 0) {
            return true;
        }
        return false;
    }

    public synchronized boolean isAudioVolumeLimitSupported() {
        return isPropertySupportedLocked(AUDIO_VOLUME_LIMIT);
    }

    public synchronized boolean isAudioVolumeMasterOnly() {
        if (!isPropertySupportedLocked(VehicleProperty.AUDIO_VOLUME)) {
            throw new IllegalStateException("VehicleProperty.AUDIO_VOLUME not supported");
        }
        VehiclePropConfig config = mProperties.get(
                AUDIO_VOLUME);
        if ((config.configArray.get(1) &
                VehicleAudioVolumeCapabilityFlag.MASTER_VOLUME_ONLY)
                != 0) {
            return true;
        }
        return false;
    }

    /**
     * Get the current audio focus state.
     * @return 0: focusState, 1: streams, 2: externalFocus
     */
    public int[] getCurrentFocusState() {
        if (!isFocusSupported()) {
            return new int[] { VEHICLE_AUDIO_FOCUS_STATE_GAIN, 0xffffffff, 0};
        }
        try {
            VehiclePropValue propValue = mVehicleHal.get(VehicleProperty.AUDIO_FOCUS);
            return toIntArray(propValue.value.int32Values);
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_AUDIO, "VehicleProperty.AUDIO_FOCUS not ready", e);
            return new int[] { VEHICLE_AUDIO_FOCUS_STATE_LOSS, 0x0, 0};
        }
    }

    public static class ExtRoutingSourceInfo {
        /** Represents an external route which will not disable any physical stream in android side.
         */
        public static final int NO_DISABLED_PHYSICAL_STREAM = -1;

        /** Bit position of this source in vhal */
        public final int bitPosition;
        /**
         * Physical stream replaced by this routing. will be {@link #NO_DISABLED_PHYSICAL_STREAM}
         * if no physical stream for android is replaced by this routing.
         */
        public final int physicalStreamNumber;

        public ExtRoutingSourceInfo(int bitPosition, int physycalStreamNumber) {
            this.bitPosition = bitPosition;
            this.physicalStreamNumber = physycalStreamNumber;
        }

        @Override
        public String toString() {
            return "[bitPosition=" + bitPosition + ", physicalStreamNumber="
                    + physicalStreamNumber + "]";
        }
    }

    /**
     * Get external audio routing types from AUDIO_EXT_ROUTING_HINT property.
     *
     * @return null if AUDIO_EXT_ROUTING_HINT is not supported.
     */
    public Map<String, ExtRoutingSourceInfo> getExternalAudioRoutingTypes() {
        VehiclePropConfig config;
        synchronized (this) {
            if (!isPropertySupportedLocked(AUDIO_EXT_ROUTING_HINT)) {
                if (DBG) {
                    Log.i(CarLog.TAG_AUDIO, "AUDIO_EXT_ROUTING_HINT is not supported");
                }
                return null;
            }
            config = mProperties.get(AUDIO_EXT_ROUTING_HINT);
        }
        if (TextUtils.isEmpty(config.configString)) {
            Log.w(CarLog.TAG_AUDIO, "AUDIO_EXT_ROUTING_HINT with empty config string");
            return null;
        }
        Map<String, ExtRoutingSourceInfo> routingTypes = new HashMap<>();
        String configString = config.configString;
        if (DBG) {
            Log.i(CarLog.TAG_AUDIO, "AUDIO_EXT_ROUTING_HINT config string:" + configString);
        }
        String[] routes = configString.split(",");
        for (String routeString : routes) {
            String[] tokens = routeString.split(":");
            int bitPosition = 0;
            String name = null;
            int physicalStreamNumber = ExtRoutingSourceInfo.NO_DISABLED_PHYSICAL_STREAM;
            if (tokens.length == 2) {
                bitPosition = Integer.parseInt(tokens[0]);
                name = tokens[1];
            } else if (tokens.length == 3) {
                bitPosition = Integer.parseInt(tokens[0]);
                name = tokens[1];
                physicalStreamNumber = Integer.parseInt(tokens[2]);
            } else {
                Log.w(CarLog.TAG_AUDIO, "AUDIO_EXT_ROUTING_HINT has wrong entry:" +
                        routeString);
                continue;
            }
            routingTypes.put(name, new ExtRoutingSourceInfo(bitPosition, physicalStreamNumber));
        }
        return routingTypes;
    }

    public void setExternalRoutingSource(int[] externalRoutings) {
        try {
            mVehicleHal.set(AUDIO_EXT_ROUTING_HINT).to(externalRoutings);
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_AUDIO, "Cannot write to VehicleProperty.AUDIO_EXT_ROUTING_HINT", e);
        }
    }

    private boolean isPropertySupportedLocked(int property) {
        VehiclePropConfig config = mProperties.get(property);
        return config != null;
    }

    @Override
    public synchronized void init() {
        for (VehiclePropConfig config : mProperties.values()) {
            if (VehicleHal.isPropertySubscribable(config)) {
                int subsribeFlag = SubscribeFlags.HAL_EVENT;
                if (AUDIO_STREAM_STATE == config.prop) {
                    subsribeFlag |= SubscribeFlags.SET_CALL;
                }
                mVehicleHal.subscribeProperty(this, config.prop, 0, subsribeFlag);
            }
        }
        try {
            mVariant = mVehicleHal.get(int.class, AUDIO_HW_VARIANT);
        } catch (IllegalArgumentException e) {
            // no variant. Set to default, 0.
            mVariant = 0;
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_AUDIO, "VehicleProperty.AUDIO_HW_VARIANT not ready", e);
            mVariant = 0;
        }
    }

    @Override
    public synchronized void release() {
        for (VehiclePropConfig config : mProperties.values()) {
            if (VehicleHal.isPropertySubscribable(config)) {
                mVehicleHal.unsubscribeProperty(this, config.prop);
            }
        }
        mProperties.clear();
    }

    @Override
    public synchronized Collection<VehiclePropConfig> takeSupportedProperties(
            Collection<VehiclePropConfig> allProperties) {
        for (VehiclePropConfig p : allProperties) {
            switch (p.prop) {
                case VehicleProperty.AUDIO_FOCUS:
                case VehicleProperty.AUDIO_VOLUME:
                case VehicleProperty.AUDIO_VOLUME_LIMIT:
                case VehicleProperty.AUDIO_HW_VARIANT:
                case VehicleProperty.AUDIO_EXT_ROUTING_HINT:
                case VehicleProperty.AUDIO_PARAMETERS:
                case VehicleProperty.AUDIO_STREAM_STATE:
                    mProperties.put(p.prop, p);
                    break;
            }
        }
        return new ArrayList<>(mProperties.values());
    }

    @Override
    public void handleHalEvents(List<VehiclePropValue> values) {
        AudioHalFocusListener focusListener;
        AudioHalVolumeListener volumeListener;
        OnParameterChangeListener parameterListener;
        synchronized (this) {
            focusListener = mFocusListener;
            volumeListener = mVolumeListener;
            parameterListener = mOnParameterChangeListener;
        }
        dispatchEventToListener(focusListener, volumeListener, parameterListener, values);
    }

    public String[] getAudioParameterKeys() {
        VehiclePropConfig config;
        synchronized (this) {
            if (!isPropertySupportedLocked(AUDIO_PARAMETERS)) {
                if (DBG) {
                    Log.i(CarLog.TAG_AUDIO, "AUDIO_PARAMETERS is not supported");
                }
                return null;
            }
            config = mProperties.get(AUDIO_PARAMETERS);
        }
        return config.configString.split(";");
    }

    public void setAudioParameters(String parameters) {
        synchronized (this) {
            if (!isPropertySupportedLocked(AUDIO_PARAMETERS)) {
                throw new IllegalStateException("VehicleProperty.AUDIO_PARAMETERS not supported");
            }
        }
        VehiclePropValue value = new VehiclePropValue();
        value.prop = AUDIO_PARAMETERS;
        value.value.stringValue = parameters;
        try {
            mVehicleHal.set(value);
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_AUDIO, "Cannot write to VehicleProperty.AUDIO_EXT_ROUTING_HINT", e);
        }
    }

    public String getAudioParameters(String keys) {
        synchronized (this) {
            if (!isPropertySupportedLocked(AUDIO_PARAMETERS)) {
                throw new IllegalStateException("VehicleProperty.AUDIO_PARAMETERS not supported");
            }
        }
        try {
            VehiclePropValue requested = new VehiclePropValue();
            requested.prop = AUDIO_PARAMETERS;
            requested.value.stringValue = keys;
            VehiclePropValue propValue = mVehicleHal.get(requested);
            return propValue.value.stringValue;
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_AUDIO, "VehicleProperty.AUDIO_PARAMETERS not ready", e);
            return new String("");
        }
    }

    public synchronized void setOnParameterChangeListener(OnParameterChangeListener listener) {
        mOnParameterChangeListener = listener;
    }

    private void dispatchEventToListener(AudioHalFocusListener focusListener,
            AudioHalVolumeListener volumeListener,
            OnParameterChangeListener parameterListener,
            List<VehiclePropValue> values) {
        for (VehiclePropValue v : values) {
            switch (v.prop) {
                case VehicleProperty.AUDIO_FOCUS: {
                    ArrayList<Integer> vec = v.value.int32Values;
                    int focusState = vec.get(VehicleAudioFocusIndex.FOCUS);
                    int streams = vec.get(VehicleAudioFocusIndex.STREAMS);
                    int externalFocus = vec.get(VehicleAudioFocusIndex.EXTERNAL_FOCUS_STATE);
                    if (focusListener != null) {
                        focusListener.onFocusChange(focusState, streams, externalFocus);
                    }
                } break;
                case VehicleProperty.AUDIO_STREAM_STATE: {
                    ArrayList<Integer> vec = v.value.int32Values;
                    boolean streamStarted = vec.get(0) == VEHICLE_AUDIO_STREAM_STATE_STARTED;
                    int streamNum = vec.get(1);
                    if (focusListener != null) {
                        focusListener.onStreamStatusChange(streamNum, streamStarted);
                    }
                } break;
                case AUDIO_VOLUME: {
                    ArrayList<Integer> vec = v.value.int32Values;
                    int streamNum = vec.get(VehicleAudioVolumeIndex.STREAM);
                    int volume = vec.get(VehicleAudioVolumeIndex.VOLUME);
                    int volumeState = vec.get(VehicleAudioVolumeIndex.STATE);
                    if (volumeListener != null) {
                        volumeListener.onVolumeChange(streamNum, volume, volumeState);
                    }
                } break;
                case AUDIO_VOLUME_LIMIT: {
                    ArrayList<Integer> vec = v.value.int32Values;
                    int stream = vec.get(VehicleAudioVolumeLimitIndex.STREAM);
                    int maxVolume = vec.get(VehicleAudioVolumeLimitIndex.MAX_VOLUME);
                    if (volumeListener != null) {
                        volumeListener.onVolumeLimitChange(stream, maxVolume);
                    }
                } break;
                case AUDIO_PARAMETERS: {
                    String params = v.value.stringValue;
                    if (parameterListener != null) {
                        parameterListener.onParameterChange(params);
                    }
                }
            }
        }
        values.clear();
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*Audio HAL*");
        writer.println(" audio H/W variant:" + mVariant);
        writer.println(" Supported properties");
        VehicleHal.dumpProperties(writer, mProperties.values());
    }

}
