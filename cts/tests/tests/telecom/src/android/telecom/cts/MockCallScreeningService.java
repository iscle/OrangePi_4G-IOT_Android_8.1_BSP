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

package android.telecom.cts;

import static org.junit.Assert.assertFalse;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

import java.util.concurrent.Semaphore;

public class MockCallScreeningService extends CallScreeningService {
    private static String LOG_TAG = "MockCallScreeningSvc";

    private static boolean mIsServiceUnbound;
    private static CallScreeningServiceCallbacks sCallbacks;

    public static abstract class CallScreeningServiceCallbacks {
        public Semaphore lock = new Semaphore(0);
        private MockCallScreeningService mService;

        public void onScreenCall(Call.Details callDetails) {};

        final public MockCallScreeningService getService() {
            return mService;
        }

        final public void setService(MockCallScreeningService service) {
            mService = service;
        }
    }

    @Override
    public android.os.IBinder onBind(android.content.Intent intent) {
        Log.i(LOG_TAG, "Service bounded");
        if (getCallbacks() != null) {
            getCallbacks().setService(this);
        }
        mIsServiceUnbound = false;
        return super.onBind(intent);
    }

    @Override
    public void onScreenCall(Call.Details callDetails) {
        if (getCallbacks() != null) {
            getCallbacks().onScreenCall(callDetails);
        }
    }

    public static void setCallbacks(CallScreeningServiceCallbacks callbacks) {
        sCallbacks = callbacks;
    }

    private CallScreeningServiceCallbacks getCallbacks() {
        if (sCallbacks != null) {
            sCallbacks.setService(this);
        }
        return sCallbacks;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(LOG_TAG, "Service unbounded");
        assertFalse(mIsServiceUnbound);
        mIsServiceUnbound = true;
        return super.onUnbind(intent);
    }

    public static boolean isServiceUnbound() {
        return mIsServiceUnbound;
    }

    public static void enableService(Context context) {
        context.getPackageManager().setComponentEnabledSetting(getComponentName(context),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP);
    }

    public static void disableService(Context context) {
        context.getPackageManager().setComponentEnabledSetting(getComponentName(context),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP);
    }

    private static ComponentName getComponentName(Context context) {
        return new ComponentName(context, MockCallScreeningService.class);
    }
}
