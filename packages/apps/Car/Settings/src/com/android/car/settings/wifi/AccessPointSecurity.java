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
 * limitations under the License
 */

package com.android.car.settings.wifi;

import android.content.Context;

import com.android.car.settings.R;
import com.android.settingslib.wifi.AccessPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents Security protocol for AccessPoint.
 */
public class AccessPointSecurity {
    public static final int SECURITY_NONE_POSITION = 0;
    private final int mSecurityType;
    private final Context mContext;
    private static final List<Integer> SECURITY_TYPES = Arrays.asList(
            AccessPoint.SECURITY_NONE,
            AccessPoint.SECURITY_WEP,
            AccessPoint.SECURITY_PSK,
            AccessPoint.SECURITY_EAP);

    public static List<AccessPointSecurity> getSecurityTypes(Context context) {
        List<AccessPointSecurity> securities = new ArrayList<>();
        for (int security : SECURITY_TYPES) {
            securities.add(new AccessPointSecurity(context, security));
        }
        return securities;
    }

    private AccessPointSecurity(Context context, int securityType) {
        mContext = context;
        mSecurityType = securityType;
    }

    public int getSecurityType() {
        return mSecurityType;
    }

    @Override
    public String toString() {
        switch(mSecurityType) {
            case AccessPoint.SECURITY_EAP:
                return mContext.getString(R.string.wifi_security_eap);
            case AccessPoint.SECURITY_PSK:
                return mContext.getString(R.string.wifi_security_psk_generic);
            case AccessPoint.SECURITY_WEP:
                return mContext.getString(R.string.wifi_security_wep);
            case AccessPoint.SECURITY_NONE:
            default:
                return mContext.getString(R.string.wifi_security_none);
        }
    }
}
