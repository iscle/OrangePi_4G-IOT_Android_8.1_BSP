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
package com.android.car;

import android.car.CarInfoManager;
import android.car.ICarInfo;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;

import com.android.car.hal.InfoHalService;
import com.android.car.internal.FeatureConfiguration;
import com.android.car.internal.FeatureUtil;

import java.io.PrintWriter;

public class CarInfoService extends ICarInfo.Stub implements CarServiceBase {

    private final InfoHalService mInfoHal;
    private final Context mContext;

    public CarInfoService(Context context, InfoHalService infoHal) {
        mInfoHal = infoHal;
        mContext = context;
    }

    @Override
    public Bundle getBasicInfo() {
        return mInfoHal.getBasicInfo();
    }

    @Override
    public String getStringInfo(String key) {
        switch (key) {
            case CarInfoManager.INFO_KEY_PRODUCT_CONFIGURATION:
                FeatureUtil.assertFeature(
                        FeatureConfiguration.ENABLE_PRODUCT_CONFIGURATION_INFO);
                // still protect with if-feature code. code under if can be dropped by
                // proguard if necessary.
                if (FeatureConfiguration.ENABLE_PRODUCT_CONFIGURATION_INFO) {
                    //TODO get it from HAL layer
                    return null;
                }
                break;
            default: // just throw exception
                break;
        }
        throw new IllegalArgumentException("Unsupported key:" + key);
    }

    @Override
    public void init() {
        Bundle info = mInfoHal.getBasicInfo();
        // do not update ID immediately even if user clears it.
        info.putString(CarInfoManager.BASIC_INFO_KEY_VEHICLE_ID,
                Settings.Secure.getString(mContext.getContentResolver(),
                        Settings.Secure.ANDROID_ID));
    }

    @Override
    public synchronized void release() {
        //nothing to do
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*CarInfoService*");
        writer.println("**Check HAL dump");
    }
}

