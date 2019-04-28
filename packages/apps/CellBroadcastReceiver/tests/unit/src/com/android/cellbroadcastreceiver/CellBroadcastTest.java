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

package com.android.cellbroadcastreceiver;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;

import android.content.Context;
import android.content.res.Resources;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.util.Log;
import android.util.SparseArray;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public abstract class CellBroadcastTest {

    protected static String TAG;

    private SparseArray<PersistableBundle> mBundles = new SparseArray<>();

    MockedServiceManager mMockedServiceManager;

    @Mock
    Context mContext;
    @Mock
    CarrierConfigManager mCarrierConfigManager;
    @Mock
    Resources mResources;

    protected void setUp(String tag) throws Exception {
        TAG = tag;
        MockitoAnnotations.initMocks(this);
        mMockedServiceManager = new MockedServiceManager();
        initContext();
    }

    private void initContext() {
        doReturn(mCarrierConfigManager).when(mContext)
                .getSystemService(eq(Context.CARRIER_CONFIG_SERVICE));
        doReturn(mResources).when(mContext).getResources();
    }

    void carrierConfigSetStringArray(int subId, String key, String[] values) {
        if (mBundles.get(subId) == null) {
            mBundles.put(subId, new PersistableBundle());
        }
        mBundles.get(subId).putStringArray(key, values);
        doReturn(mBundles.get(subId)).when(mCarrierConfigManager).getConfigForSubId(eq(subId));
    }

    void putResources(int id, String[] values) {
        doReturn(values).when(mResources).getStringArray(eq(id));
    }

    protected void tearDown() throws Exception {
        mMockedServiceManager.restoreAllServices();
    }

    protected static void logd(String s) {
        Log.d(TAG, s);
    }
}
