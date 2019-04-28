/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.bluetooth.btservice;

import java.util.ArrayList;

import android.bluetooth.BluetoothProfile;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.Log;

import com.android.bluetooth.R;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.a2dpsink.A2dpSinkService;
import com.android.bluetooth.avrcpcontroller.AvrcpControllerService;
import com.android.bluetooth.hdp.HealthService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.hfpclient.HeadsetClientService;
import com.android.bluetooth.hid.HidService;
import com.android.bluetooth.pan.PanService;
import com.android.bluetooth.gatt.GattService;
import com.android.bluetooth.map.BluetoothMapService;
import com.android.bluetooth.mapclient.MapClientService;
import com.android.bluetooth.sap.SapService;
import com.android.bluetooth.pbapclient.PbapClientService;
import com.android.bluetooth.hid.HidDevService;
import com.android.bluetooth.pbap.BluetoothPbapService;
import com.android.bluetooth.opp.BluetoothOppService;

public class Config {
    private static final String TAG = "AdapterServiceConfig";
    /**
     * List of profile services.
     */
    @SuppressWarnings("rawtypes")
    // Do not inclue OPP and PBAP, because their services
    // are not managed by AdapterService
    private static final Class[] PROFILE_SERVICES = {HeadsetService.class, A2dpService.class,
            A2dpSinkService.class, HidService.class, HealthService.class, PanService.class,
            GattService.class, BluetoothMapService.class, HeadsetClientService.class,
            AvrcpControllerService.class, SapService.class, PbapClientService.class,
            MapClientService.class, HidDevService.class, BluetoothOppService.class,
            BluetoothPbapService.class};
    /**
     * Resource flag to indicate whether profile is supported or not.
     */
    private static final int[] PROFILE_SERVICES_FLAG = {R.bool.profile_supported_hs_hfp,
            R.bool.profile_supported_a2dp, R.bool.profile_supported_a2dp_sink,
            R.bool.profile_supported_hid, R.bool.profile_supported_hdp,
            R.bool.profile_supported_pan, R.bool.profile_supported_gatt,
            R.bool.profile_supported_map, R.bool.profile_supported_hfpclient,
            R.bool.profile_supported_avrcp_controller, R.bool.profile_supported_sap,
            R.bool.profile_supported_pbapclient, R.bool.profile_supported_mapmce,
            R.bool.profile_supported_hidd, R.bool.profile_supported_opp,
            R.bool.profile_supported_pbap};

    private static Class[] SUPPORTED_PROFILES = new Class[0];

    static void init(Context ctx) {
        if (ctx == null) {
            return;
        }
        Resources resources = ctx.getResources();
        if (resources == null) {
            return;
        }

        ArrayList<Class> profiles = new ArrayList<Class>(PROFILE_SERVICES.length);
        for (int i=0; i < PROFILE_SERVICES_FLAG.length; i++) {
            boolean supported = resources.getBoolean(PROFILE_SERVICES_FLAG[i]);
            if (supported && !isProfileDisabled(ctx, PROFILE_SERVICES[i])) {
                Log.d(TAG, "Adding " + PROFILE_SERVICES[i].getSimpleName());
                profiles.add(PROFILE_SERVICES[i]);
            }
        }
        SUPPORTED_PROFILES = profiles.toArray(new Class[profiles.size()]);
    }

    static Class[]  getSupportedProfiles() {
        return SUPPORTED_PROFILES;
    }

    static long getSupportedProfilesBitMask() {
        long mask = 0;
        for (final Class profileClass : getSupportedProfiles()) {
            final int profileIndex = getProfileIndex(profileClass);

            if (profileIndex != -1) {
                mask |= 1 << getProfileIndex(profileClass);
            }
        }

        return mask;
    }

    private static boolean isProfileDisabled(Context context, Class profile) {
        final int profileIndex = getProfileIndex(profile);

        if (profileIndex == -1) {
            Log.w(TAG, "Could not find profile bit mask");
            return false;
        }

        final ContentResolver resolver = context.getContentResolver();
        final long disabledProfilesBitMask = Settings.Global.getLong(resolver,
                Settings.Global.BLUETOOTH_DISABLED_PROFILES, 0);
        final long profileBit = 1 << profileIndex;

        return (disabledProfilesBitMask & profileBit) != 0;
    }

    private static int getProfileIndex(Class profile) {
        int profileIndex = -1;

        if (profile == HeadsetService.class) {
            profileIndex = BluetoothProfile.HEADSET;
        } else if (profile == A2dpService.class) {
            profileIndex = BluetoothProfile.A2DP;
        } else if (profile == A2dpSinkService.class) {
            profileIndex = BluetoothProfile.A2DP_SINK;
        } else if (profile == HidService.class) {
            profileIndex = BluetoothProfile.INPUT_DEVICE;
        } else if (profile == HealthService.class) {
            profileIndex = BluetoothProfile.HEALTH;
        } else if (profile == PanService.class) {
            profileIndex = BluetoothProfile.PAN;
        } else if (profile == GattService.class) {
            profileIndex = BluetoothProfile.GATT;
        } else if (profile == BluetoothMapService.class) {
            profileIndex = BluetoothProfile.MAP;
        } else if (profile == HeadsetClientService.class) {
            profileIndex = BluetoothProfile.HEADSET_CLIENT;
        } else if (profile == AvrcpControllerService.class) {
            profileIndex = BluetoothProfile.AVRCP_CONTROLLER;
        } else if (profile == SapService.class) {
            profileIndex = BluetoothProfile.SAP;
        } else if (profile == PbapClientService.class) {
            profileIndex = BluetoothProfile.PBAP_CLIENT;
        } else if (profile == MapClientService.class) {
            profileIndex = BluetoothProfile.MAP_CLIENT;
        } else if (profile == HidDevService.class) {
            profileIndex = BluetoothProfile.INPUT_HOST;
        }

        return profileIndex;
    }
}
