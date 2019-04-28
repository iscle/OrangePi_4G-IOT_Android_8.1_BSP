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

import android.car.Car;
import android.car.test.ICarTest;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to allow testing / mocking vehicle HAL.
 * This service uses Vehicle HAL APIs directly (one exception) as vehicle HAL mocking anyway
 * requires accessing that level directly.
 */
class CarTestService extends ICarTest.Stub implements CarServiceBase {

    private static final String TAG = CarTestService.class.getSimpleName();

    private final Context mContext;
    private final ICarImpl mICarImpl;

    private final Map<IBinder, TokenDeathRecipient> mTokens = new HashMap<>();

    CarTestService(Context context, ICarImpl carImpl) {
        mContext = context;
        mICarImpl = carImpl;
    }

    @Override
    public void init() {
        // nothing to do.
        // This service should not reset anything for init / release to maintain mocking.
    }

    @Override
    public void release() {
        // nothing to do
        // This service should not reset anything for init / release to maintain mocking.
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*CarTestService*");
        writer.println(" mTokens:" + Arrays.toString(mTokens.entrySet().toArray()));
    }

    @Override
    public void stopCarService(IBinder token) throws RemoteException {
        Log.d(TAG, "stopCarService, token: " + token);
        ICarImpl.assertPermission(mContext, Car.PERMISSION_CAR_TEST_SERVICE);

        synchronized (this) {
            if (mTokens.containsKey(token)) {
                Log.w(TAG, "Calling stopCarService twice with the same token.");
                return;
            }

            TokenDeathRecipient deathRecipient = new TokenDeathRecipient(token);
            mTokens.put(token, deathRecipient);
            token.linkToDeath(deathRecipient, 0);

            if (mTokens.size() == 1) {
                mICarImpl.release();
            }
        }
    }

    @Override
    public void startCarService(IBinder token) throws RemoteException {
        Log.d(TAG, "startCarService, token: " + token);
        ICarImpl.assertPermission(mContext, Car.PERMISSION_CAR_TEST_SERVICE);
        releaseToken(token);
    }

    private synchronized void releaseToken(IBinder token) {
        Log.d(TAG, "releaseToken, token: " + token);
        DeathRecipient deathRecipient = mTokens.remove(token);
        if (deathRecipient != null) {
            token.unlinkToDeath(deathRecipient, 0);
        }

        if (mTokens.size() == 0) {
            mICarImpl.init();
        }
    }

    private class TokenDeathRecipient implements DeathRecipient {
        private final IBinder mToken;

        TokenDeathRecipient(IBinder token) throws RemoteException {
            mToken = token;
        }

        @Override
        public void binderDied() {
            releaseToken(mToken);
        }
    }
}