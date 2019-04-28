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

package com.android.bluetooth.avrcpcontroller;

import android.bluetooth.BluetoothAvrcpPlayerSettings;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/*
 * Contains information Player Application Setting extended from BluetootAvrcpPlayerSettings
 */
class PlayerApplicationSettings {
    private static final String TAG = "PlayerApplicationSettings";

    /*
     * Values for SetPlayerApplicationSettings from AVRCP Spec V1.6 Appendix F.
     */
    private static final byte JNI_ATTRIB_EQUALIZER_STATUS = 0x01;
    private static final byte JNI_ATTRIB_REPEAT_STATUS = 0x02;
    private static final byte JNI_ATTRIB_SHUFFLE_STATUS = 0x03;
    private static final byte JNI_ATTRIB_SCAN_STATUS = 0x04;

    private static final byte JNI_EQUALIZER_STATUS_OFF = 0x01;
    private static final byte JNI_EQUALIZER_STATUS_ON = 0x02;

    private static final byte JNI_REPEAT_STATUS_OFF = 0x01;
    private static final byte JNI_REPEAT_STATUS_SINGLE_TRACK_REPEAT = 0x02;
    private static final byte JNI_REPEAT_STATUS_ALL_TRACK_REPEAT = 0x03;
    private static final byte JNI_REPEAT_STATUS_GROUP_REPEAT = 0x04;

    private static final byte JNI_SHUFFLE_STATUS_OFF = 0x01;
    private static final byte JNI_SHUFFLE_STATUS_ALL_TRACK_SHUFFLE = 0x02;
    private static final byte JNI_SHUFFLE_STATUS_GROUP_SHUFFLE = 0x03;

    private static final byte JNI_SCAN_STATUS_OFF = 0x01;
    private static final byte JNI_SCAN_STATUS_ALL_TRACK_SCAN = 0x02;
    private static final byte JNI_SCAN_STATUS_GROUP_SCAN = 0x03;

    private static final byte JNI_STATUS_INVALID = -1;


    /*
     * Hash map of current settings.
     */
    private Map<Integer, Integer> mSettings = new HashMap<Integer, Integer>();

    /*
     * Hash map of supported values, a setting should be supported by the remote in order to enable
     * in mSettings.
     */
    private Map<Integer, ArrayList<Integer>> mSupportedValues =
        new HashMap<Integer, ArrayList<Integer>>();

    /* Convert from JNI array to Java classes. */
    static PlayerApplicationSettings makeSupportedSettings(byte[] btAvrcpAttributeList) {
        PlayerApplicationSettings newObj = new PlayerApplicationSettings();
        try {
            for (int i = 0; i < btAvrcpAttributeList.length; ) {
                byte attrId = btAvrcpAttributeList[i++];
                byte numSupportedVals = btAvrcpAttributeList[i++];
                ArrayList<Integer> supportedValues = new ArrayList<Integer>();

                for (int j = 0; j < numSupportedVals; j++) {
                    // Yes, keep using i for array indexing.
                    supportedValues.add(mapAttribIdValtoAvrcpPlayerSetting(attrId,
                        btAvrcpAttributeList[i++]));
                }
                newObj.mSupportedValues.put(mapBTAttribIdToAvrcpPlayerSettings(attrId),
                    supportedValues);
            }
        } catch (ArrayIndexOutOfBoundsException exception) {
            Log.e(TAG,"makeSupportedSettings attributeList index error.");
        }
        return newObj;
    }

    public BluetoothAvrcpPlayerSettings getAvrcpSettings() {
        int supportedSettings = 0;
        for (Integer setting : mSettings.keySet()) {
            supportedSettings |= setting;
        }
        BluetoothAvrcpPlayerSettings result = new BluetoothAvrcpPlayerSettings(supportedSettings);
        for (Integer setting : mSettings.keySet()) {
            result.addSettingValue(setting, mSettings.get(setting));
        }
        return result;
    }

    static PlayerApplicationSettings makeSettings(byte[] btAvrcpAttributeList) {
        PlayerApplicationSettings newObj = new PlayerApplicationSettings();
        try {
            for (int i = 0; i < btAvrcpAttributeList.length; ) {
                byte attrId = btAvrcpAttributeList[i++];

                newObj.mSettings.put(mapBTAttribIdToAvrcpPlayerSettings(attrId),
                    mapAttribIdValtoAvrcpPlayerSetting(attrId,
                        btAvrcpAttributeList[i++]));
            }
        } catch (ArrayIndexOutOfBoundsException exception) {
            Log.e(TAG,"makeSettings JNI_ATTRIButeList index error.");
        }
        return newObj;
    }

    public void setSupport(PlayerApplicationSettings updates) {
        mSettings = updates.mSettings;
        mSupportedValues = updates.mSupportedValues;
    }

    public void setValues(BluetoothAvrcpPlayerSettings updates) {
        int supportedSettings = updates.getSettings();
        for (int i = 1; i <= BluetoothAvrcpPlayerSettings.SETTING_SCAN; i++) {
            if ((i & supportedSettings) > 0) {
                mSettings.put(i, updates.getSettingValue(i));
            }
        }
    }

    /*
     * Check through all settings to ensure that they are all available to be set and then check
     * that the desired value is in fact supported by our remote player.
     */
    public boolean supportsSettings(BluetoothAvrcpPlayerSettings settingsToCheck) {
        int settingSubset = settingsToCheck.getSettings();
        int supportedSettings = 0;
        for (Integer setting : mSupportedValues.keySet()) {
            supportedSettings |= setting;
        }
        try {
            if ((supportedSettings & settingSubset) == settingSubset) {
                for (Integer settingId : mSettings.keySet()) {
                    if ((settingId & settingSubset )== settingId &&
                        (!mSupportedValues.get(settingId).contains(settingsToCheck.
                            getSettingValue(settingId))))
                        // The setting is in both settings to check and supported settings but the
                        // value is not supported.
                        return false;
                }
                return true;
            }
        } catch (NullPointerException e) {
            Log.e(TAG,
                "supportsSettings received a supported setting that has no supported values.");
        }
        return false;
    }

    // Convert currently desired settings into an attribute array to pass to the native layer to
    // enable them.
    public ArrayList<Byte> getNativeSettings() {
        int i = 0;
        ArrayList<Byte> attribArray = new ArrayList<Byte>();
        for (Integer settingId : mSettings.keySet()) {
            switch (settingId)
            {
                case BluetoothAvrcpPlayerSettings.SETTING_EQUALIZER:
                    attribArray.add(JNI_ATTRIB_EQUALIZER_STATUS);
                    attribArray.add(mapAvrcpPlayerSettingstoBTattribVal(
                        settingId, mSettings.get(settingId)));
                    break;
                case BluetoothAvrcpPlayerSettings.SETTING_REPEAT:
                    attribArray.add(JNI_ATTRIB_REPEAT_STATUS);
                    attribArray.add(mapAvrcpPlayerSettingstoBTattribVal(
                        settingId, mSettings.get(settingId)));
                    break;
                case BluetoothAvrcpPlayerSettings.SETTING_SHUFFLE:
                    attribArray.add(JNI_ATTRIB_SHUFFLE_STATUS);
                    attribArray.add(mapAvrcpPlayerSettingstoBTattribVal(
                        settingId, mSettings.get(settingId)));
                    break;
                case BluetoothAvrcpPlayerSettings.SETTING_SCAN:
                    attribArray.add(JNI_ATTRIB_SCAN_STATUS);
                    attribArray.add(mapAvrcpPlayerSettingstoBTattribVal(
                        settingId, mSettings.get(settingId)));
                    break;
                default:
                    Log.w(TAG,"Unknown setting found in getNativeSettings: " + settingId);
            }
        }
        return attribArray;
    }

    // Convert a native Attribute Id/Value pair into the AVRCP equivalent value.
    private static int mapAttribIdValtoAvrcpPlayerSetting(byte attribId, byte attribVal) {
        if (attribId == JNI_ATTRIB_EQUALIZER_STATUS) {
            switch(attribVal) {
                case JNI_EQUALIZER_STATUS_OFF:
                    return BluetoothAvrcpPlayerSettings.STATE_OFF;
                case JNI_EQUALIZER_STATUS_ON:
                    return BluetoothAvrcpPlayerSettings.STATE_ON;
            }
        } else if (attribId == JNI_ATTRIB_REPEAT_STATUS) {
            switch(attribVal) {
                case JNI_REPEAT_STATUS_ALL_TRACK_REPEAT:
                    return BluetoothAvrcpPlayerSettings.STATE_ALL_TRACK;
                case JNI_REPEAT_STATUS_GROUP_REPEAT:
                    return BluetoothAvrcpPlayerSettings.STATE_GROUP;
                case JNI_REPEAT_STATUS_OFF:
                    return BluetoothAvrcpPlayerSettings.STATE_OFF;
                case JNI_REPEAT_STATUS_SINGLE_TRACK_REPEAT:
                    return BluetoothAvrcpPlayerSettings.STATE_SINGLE_TRACK;
            }
        } else if (attribId == JNI_ATTRIB_SCAN_STATUS) {
            switch(attribVal) {
                case JNI_SCAN_STATUS_ALL_TRACK_SCAN:
                    return BluetoothAvrcpPlayerSettings.STATE_ALL_TRACK;
                case JNI_SCAN_STATUS_GROUP_SCAN:
                    return BluetoothAvrcpPlayerSettings.STATE_GROUP;
                case JNI_SCAN_STATUS_OFF:
                    return BluetoothAvrcpPlayerSettings.STATE_OFF;
            }
        } else if (attribId == JNI_ATTRIB_SHUFFLE_STATUS) {
            switch(attribVal) {
                case JNI_SHUFFLE_STATUS_ALL_TRACK_SHUFFLE:
                    return BluetoothAvrcpPlayerSettings.STATE_ALL_TRACK;
                case JNI_SHUFFLE_STATUS_GROUP_SHUFFLE:
                    return BluetoothAvrcpPlayerSettings.STATE_GROUP;
                case JNI_SHUFFLE_STATUS_OFF:
                    return BluetoothAvrcpPlayerSettings.STATE_OFF;
            }
        }
        return BluetoothAvrcpPlayerSettings.STATE_INVALID;
    }

    // Convert an AVRCP Setting/Value pair into the native equivalent value;
    private static byte mapAvrcpPlayerSettingstoBTattribVal(int mSetting, int mSettingVal) {
        if (mSetting == BluetoothAvrcpPlayerSettings.SETTING_EQUALIZER) {
            switch(mSettingVal) {
                case BluetoothAvrcpPlayerSettings.STATE_OFF:
                    return JNI_EQUALIZER_STATUS_OFF;
                case BluetoothAvrcpPlayerSettings.STATE_ON:
                    return JNI_EQUALIZER_STATUS_ON;
            }
        } else if (mSetting == BluetoothAvrcpPlayerSettings.SETTING_REPEAT) {
            switch(mSettingVal) {
                case BluetoothAvrcpPlayerSettings.STATE_OFF:
                    return JNI_REPEAT_STATUS_OFF;
                case BluetoothAvrcpPlayerSettings.STATE_SINGLE_TRACK:
                    return JNI_REPEAT_STATUS_SINGLE_TRACK_REPEAT;
                case BluetoothAvrcpPlayerSettings.STATE_ALL_TRACK:
                    return JNI_REPEAT_STATUS_ALL_TRACK_REPEAT;
                case BluetoothAvrcpPlayerSettings.STATE_GROUP:
                    return JNI_REPEAT_STATUS_GROUP_REPEAT;
            }
        } else if (mSetting == BluetoothAvrcpPlayerSettings.SETTING_SHUFFLE) {
            switch(mSettingVal) {
                case BluetoothAvrcpPlayerSettings.STATE_OFF:
                    return JNI_SHUFFLE_STATUS_OFF;
                case BluetoothAvrcpPlayerSettings.STATE_ALL_TRACK:
                    return JNI_SHUFFLE_STATUS_ALL_TRACK_SHUFFLE;
                case BluetoothAvrcpPlayerSettings.STATE_GROUP:
                    return JNI_SHUFFLE_STATUS_GROUP_SHUFFLE;
            }
        } else if (mSetting == BluetoothAvrcpPlayerSettings.SETTING_SCAN) {
            switch(mSettingVal) {
                case BluetoothAvrcpPlayerSettings.STATE_OFF:
                    return JNI_SCAN_STATUS_OFF;
                case BluetoothAvrcpPlayerSettings.STATE_ALL_TRACK:
                    return JNI_SCAN_STATUS_ALL_TRACK_SCAN;
                case BluetoothAvrcpPlayerSettings.STATE_GROUP:
                    return JNI_SCAN_STATUS_GROUP_SCAN;
            }
        }
        return JNI_STATUS_INVALID;
    }

    // convert a native Attribute Id into the AVRCP Setting equivalent value;
    private static int mapBTAttribIdToAvrcpPlayerSettings(byte attribId) {
        switch(attribId) {
            case JNI_ATTRIB_EQUALIZER_STATUS:
                return BluetoothAvrcpPlayerSettings.SETTING_EQUALIZER;
            case JNI_ATTRIB_REPEAT_STATUS:
                return BluetoothAvrcpPlayerSettings.SETTING_REPEAT;
            case JNI_ATTRIB_SHUFFLE_STATUS:
                return BluetoothAvrcpPlayerSettings.SETTING_SHUFFLE;
            case JNI_ATTRIB_SCAN_STATUS:
                return BluetoothAvrcpPlayerSettings.SETTING_SCAN;
            default:
                return BluetoothAvrcpPlayerSettings.STATE_INVALID;
        }
    }

}

