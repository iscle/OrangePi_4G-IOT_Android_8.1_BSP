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

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.test.AndroidTestCase;

import java.io.FileDescriptor;
import java.util.concurrent.Semaphore;

/**
 * Check past exploits of DeviceIdleController.
 */
public class DeviceIdleControllerTest extends AndroidTestCase {
    int mResult;

    /**
     * Verify that the command line interface can not be used to add an app to the whitelist.
     */
    public void testAddWhiteList() {
        final IBinder service = ServiceManager.getService("deviceidle");
        final Semaphore mSemaphore = new Semaphore(0);
        mResult = 0;
        try {
            service.shellCommand(FileDescriptor.in, FileDescriptor.out, FileDescriptor.err,
                    new String[]{"whitelist", "+" + mContext.getPackageName()},
                    null,
                    new ResultReceiver(null) {
                        @Override
                        protected void onReceiveResult(int resultCode, Bundle resultData) {
                            mResult = resultCode;
                            mSemaphore.release();
                        }
                    });
        } catch (RemoteException e) {
        }
        try {
            mSemaphore.acquire();
        } catch (InterruptedException e) {
        }
        assertEquals(-1, mResult);
        PowerManager pm = mContext.getSystemService(PowerManager.class);
        assertFalse(pm.isIgnoringBatteryOptimizations(mContext.getPackageName()));
    }
}
