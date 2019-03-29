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

package android.telephony.cts;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.compatibility.common.util.TestThread;

import java.util.List;

public class SubscriptionManagerTest extends AndroidTestCase {
    private SubscriptionManager mSubscriptionManager;
    private static ConnectivityManager mCm;
    private SubscriptionManager.OnSubscriptionsChangedListener mListener;
    private final Object mLock = new Object();
    private boolean mOnSubscriptionsChangedCalled = false;
    private static final int TOLERANCE = 1000;
    private static final String TAG = "android.telephony.cts.SubscriptionManagerTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mSubscriptionManager = new SubscriptionManager(getContext());
        mCm = (ConnectivityManager)getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mListener != null) {
            // unregister the listener
            mSubscriptionManager.removeOnSubscriptionsChangedListener(mListener);
        }
        super.tearDown();
    }

    public void testGetActiveSubscriptionInfoCount() {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }
        assertTrue(mSubscriptionManager.getActiveSubscriptionInfoCount() <=
                mSubscriptionManager.getActiveSubscriptionInfoCountMax());
    }

    public void testActiveSubscriptions() {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }
        List<SubscriptionInfo> subList = mSubscriptionManager.getActiveSubscriptionInfoList();
        // Assert when there is no sim card present or detected
        assertTrue(subList != null && subList.size() > 0);
        for (int i = 0; i < subList.size(); i++) {
            assertTrue(subList.get(i).getSubscriptionId() >= 0);
            assertTrue(subList.get(i).getSimSlotIndex() >= 0);
        }
    }
}
