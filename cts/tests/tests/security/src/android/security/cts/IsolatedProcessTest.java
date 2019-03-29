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
package android.security.cts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.SecurityTest;
import android.security.cts.IIsolatedService;
import android.security.cts.IsolatedService;
import android.test.AndroidTestCase;
import android.util.Log;
import com.android.internal.util.ArrayUtils;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import junit.framework.Assert;

@SecurityTest
public class IsolatedProcessTest extends AndroidTestCase {
    static final String TAG = IsolatedProcessTest.class.getSimpleName();

    private static final long BIND_SERVICE_TIMEOUT = 5000;

    // No service other than these should be visible to an isolated process
    private static final String[] SERVICES_ALLOWED_TO_ISOLATED_PROCESS = {
            "package",
            Context.ACTIVITY_SERVICE
    };
    // Arbitrary set of services to test accessibility from an isolated process
    private static final String[] RESTRICTED_SERVICES_TO_TEST = {
            Context.ALARM_SERVICE,
            Context.WINDOW_SERVICE,
            Context.POWER_SERVICE
    };

    private CountDownLatch mLatch;
    private IIsolatedService mService;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e(TAG, "Isolated service " + name + " died abruptly");
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IIsolatedService.Stub.asInterface(service);
            mLatch.countDown();
        }
    };

    @Override
    public void setUp() throws InterruptedException {
        mLatch = new CountDownLatch(1);
        Intent serviceIntent = new Intent(mContext, IsolatedService.class);
        mContext.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        Assert.assertTrue("Timed out while waiting to bind to isolated service",
                mLatch.await(BIND_SERVICE_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    public void testGetCachedServicesFromIsolatedService() throws RemoteException {
        String[] cachedServices = mService.getCachedSystemServices();
        for (String serviceName : cachedServices) {
            Assert.assertTrue(serviceName + " should not be accessbible from an isolated process",
                    ArrayUtils.contains(SERVICES_ALLOWED_TO_ISOLATED_PROCESS, serviceName));
        }
    }

    public void testGetServiceFromIsolatedService() throws RemoteException {
        for (String serviceName : RESTRICTED_SERVICES_TO_TEST) {
            IBinder service = mService.getSystemService(serviceName);
            Assert.assertNull(serviceName + " should not be accessible from an isolated process",
                    service);
        }
    }

    @Override
    public void tearDown() {
        mContext.unbindService(mServiceConnection);
    }

}
