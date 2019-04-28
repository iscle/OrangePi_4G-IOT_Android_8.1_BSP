/**
 * Copyright (C) 2016 The Android Open Source Project
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cellbroadcastreceiver;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.telephony.CarrierConfigManager;
import android.test.ServiceTestCase;
import android.util.Log;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public abstract class CellBroadcastServiceTestCase<T extends Service> extends ServiceTestCase<T> {

    @Mock
    protected CarrierConfigManager mMockedCarrierConfigManager;

    Intent mServiceIntentToVerify;

    Intent mActivityIntentToVerify;

    CellBroadcastServiceTestCase(Class<T> serviceClass) {
        super(serviceClass);
    }

    protected static void waitForMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    private class TestContextWrapper extends ContextWrapper {

        private final String TAG = TestContextWrapper.class.getSimpleName();

        public TestContextWrapper(Context base) {
            super(base);
        }

        @Override
        public ComponentName startService(Intent service) {
            mServiceIntentToVerify = service;
            return null;
        }

        @Override
        public void startActivity(Intent intent) {
            mActivityIntentToVerify = intent;
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public Object getSystemService(String name) {
            if (name.equals(Context.CARRIER_CONFIG_SERVICE)) {
                Log.d(TAG, "return mocked svc for " + name + ", " + mMockedCarrierConfigManager);
                return mMockedCarrierConfigManager;
            }
            Log.d(TAG, "return real service " + name);
            return super.getSystemService(name);
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = new TestContextWrapper(getContext());
        setContext(mContext);
    }
}

