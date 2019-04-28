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
 * limitations under the License
 */
package com.android.providers.blockednumber;

import android.app.AppOpsManager;
import android.app.backup.BackupManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.location.CountryDetector;
import android.os.UserManager;
import android.provider.BlockedNumberContract;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentResolver;
import android.test.mock.MockContext;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MyMockContext extends MockContext {
    @Mock
    CountryDetector mCountryDetector;
    @Mock
    AppOpsManager mAppOpsManager;
    @Mock
    UserManager mUserManager;
    @Mock
    TelecomManager mTelecomManager;
    @Mock
    TelephonyManager mTelephonyManager;
    @Mock
    CarrierConfigManager mCarrierConfigManager;
    @Mock
    BackupManager mBackupManager;

    private final HashMap<Class<?>, String> mSupportedServiceNamesByClass =
            new HashMap<Class<?>, String>();
    private MockContentResolver mResolver;
    private BlockedNumberProviderTestable mProvider;
    private Context mRealTestContext;
    final List<String> mIntentsBroadcasted = new ArrayList<>();

    public MyMockContext(Context realTestContext) {
        this.mRealTestContext = realTestContext;
    }

    @Override
    public String getSystemServiceName(Class<?> serviceClass) {
        if (mSupportedServiceNamesByClass.containsKey(serviceClass)) {
            return mSupportedServiceNamesByClass.get(serviceClass);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getSystemService(String name) {
        switch (name) {
            case Context.COUNTRY_DETECTOR:
                return mCountryDetector;
            case Context.APP_OPS_SERVICE:
                return mAppOpsManager;
            case Context.USER_SERVICE:
                return mUserManager;
            case Context.TELECOM_SERVICE:
                return mTelecomManager;
            case Context.TELEPHONY_SERVICE:
                return mTelephonyManager;
            case Context.CARRIER_CONFIG_SERVICE:
                return mCarrierConfigManager;
        }
        throw new UnsupportedOperationException("Service not supported: " + name);
    }

    @Override
    public ContentResolver getContentResolver() {
        return mResolver;
    }

    @Override
    public int checkCallingPermission(String permission) {
        return permission != null && (permission.equals("android.permission.READ_BLOCKED_NUMBERS")
                || permission.equals("android.permission.WRITE_BLOCKED_NUMBERS"))
                ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return mRealTestContext.getSharedPreferences(name, mode);
    }

    @Override
    public void sendBroadcast(Intent intent, String receiverPermission) {
        mIntentsBroadcasted.add(intent.getAction());
    }

    public void initializeContext() {
        registerServices();
        mResolver = new MockContentResolver();

        mProvider = new BlockedNumberProviderTestable(mBackupManager);

        final ProviderInfo info = new ProviderInfo();
        info.authority = BlockedNumberContract.AUTHORITY;
        mProvider.attachInfoForTesting(this, info);

        mResolver.addProvider(BlockedNumberContract.AUTHORITY, mProvider);

        SharedPreferences prefs = mRealTestContext.getSharedPreferences(
                "block_number_provider_prefs", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    private void registerServices() {
        MockitoAnnotations.initMocks(this);

        mSupportedServiceNamesByClass.put(CountryDetector.class, Context.COUNTRY_DETECTOR);
        mSupportedServiceNamesByClass.put(AppOpsManager.class, Context.APP_OPS_SERVICE);
        mSupportedServiceNamesByClass.put(UserManager.class, Context.USER_SERVICE);
        mSupportedServiceNamesByClass.put(TelecomManager.class, Context.TELECOM_SERVICE);
        mSupportedServiceNamesByClass.put(TelephonyManager.class, Context.TELEPHONY_SERVICE);
        mSupportedServiceNamesByClass.put(
                CarrierConfigManager.class, Context.CARRIER_CONFIG_SERVICE);
    }

    public void shutdown() {
        mProvider.shutdown();
    }
}

